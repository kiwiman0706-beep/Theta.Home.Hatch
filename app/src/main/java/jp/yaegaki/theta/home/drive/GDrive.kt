package jp.yaegaki.theta.home.drive

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Google ドライブ（Drive API v3）。Google アカウントの許可は Play 開発者サービスの AuthorizationClient で取り、
 * アクセストークンで REST を呼ぶ。範囲は読み取りだけ（drive.readonly）。
 * 使うには、自分の Google Cloud プロジェクトで Drive API を有効にし、このアプリ（パッケージ名＋署名の SHA-1）を
 * Android の OAuth クライアントとして登録しておく（設定の「GOOGLE DRIVE」に手順）。
 */
class GDrive(context: Context, private val prefs: SharedPreferences) {
    private val context = context.applicationContext

    data class Item(
        val id: String,
        val name: String,
        val mime: String,
        val size: Long,
        val modified: Long,
        val thumb: String?,
        val link: String?,
    ) {
        val isDir: Boolean get() = mime == FOLDER
        /** Google ドキュメント・スプレッドシートなど。ファイルとして取得できないので、リンクで開く。 */
        val isGoogleDoc: Boolean get() = mime.startsWith("application/vnd.google-apps.") && !isDir
    }

    /** 許可の画面を出す必要がある。pendingIntent をアクティビティから開く。 */
    class NeedConsent(val intent: PendingIntent) : IOException("Google の許可が要ります")

    var enabled: Boolean
        get() = prefs.getBoolean("driveOn", false)
        private set(v) = prefs.edit().putBoolean("driveOn", v).apply()

    var email: String
        get() = prefs.getString("driveEmail", "") ?: ""
        set(v) = prefs.edit().putString("driveEmail", v).apply()

    @Volatile private var token: String? = null
    @Volatile private var tokenAt = 0L

    private val request: AuthorizationRequest
        get() = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(SCOPE))).build()

    private suspend fun authorize(): AuthorizationResult = suspendCancellableCoroutine { cont ->
        Identity.getAuthorizationClient(context).authorize(request)
            .addOnSuccessListener { cont.resume(it) }
            .addOnFailureListener { cont.resumeWithException(it) }
    }

    /** アクセストークン。許可済みなら画面を出さずに取れる。まだなら NeedConsent。 */
    suspend fun token(force: Boolean = false): String {
        val t = token
        if (!force && t != null && System.currentTimeMillis() - tokenAt < 45 * 60_000) return t
        val r = try {
            authorize()
        } catch (e: Exception) {
            throw IOException(explain(e), e)
        }
        if (r.hasResolution()) throw NeedConsent(r.pendingIntent ?: throw IOException("許可の画面を出せません"))
        return accept(r)
    }

    /** 許可の画面から戻ってきたとき。 */
    fun acceptResult(data: Intent?): Boolean = runCatching {
        accept(Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data))
    }.isSuccess

    private fun accept(r: AuthorizationResult): String {
        val t = r.accessToken ?: throw IOException("トークンを受け取れませんでした")
        token = t
        tokenAt = System.currentTimeMillis()
        enabled = true
        return t
    }

    /** この端末での接続をやめる（Google 側の許可の取り消しは、Google アカウントの「サードパーティ接続」から）。 */
    fun signOut() {
        token?.let { t -> runCatching { GoogleAuthUtil.clearToken(context, t) } }
        token = null
        enabled = false
        email = ""
    }

    // ---- REST ----

    /** つないだアカウントのメールアドレス。 */
    fun about(t: String): String =
        JSONObject(get(t, "$API/about?fields=user(emailAddress)")).optJSONObject("user")?.optString("emailAddress").orEmpty()

    /**
     * フォルダの中身。id は Drive のフォルダ ID か、次の特別な場所：
     * ROOT（マイドライブ・共有アイテム・共有ドライブ・スター付きを並べた入口）、"root"（マイドライブ）、SHARED、DRIVES、STARRED。
     */
    fun list(t: String, id: String): List<Item> {
        if (id == ROOT) return listOf(
            folder("root", "マイドライブ"),
            folder(SHARED, "共有アイテム"),
            folder(DRIVES, "共有ドライブ"),
            folder(STARRED, "スター付き"),
        )
        if (id == DRIVES) {
            val o = JSONObject(get(t, "$API/drives?pageSize=100&fields=drives(id,name)"))
            val a = o.optJSONArray("drives") ?: return emptyList()
            return (0 until a.length()).map { a.getJSONObject(it).let { d -> folder(d.getString("id"), d.getString("name")) } }
        }
        val q = when (id) {
            SHARED -> "sharedWithMe and trashed=false"
            STARRED -> "starred and trashed=false"
            else -> "'${id.replace("'", "\\'")}' in parents and trashed=false"
        }
        val out = ArrayList<Item>()
        var page: String? = null
        do {
            val url = "$API/files?q=" + enc(q) +
                "&pageSize=1000&corpora=allDrives&includeItemsFromAllDrives=true&supportsAllDrives=true" +
                "&fields=" + enc("nextPageToken,files(id,name,mimeType,size,modifiedTime,thumbnailLink,webViewLink,shortcutDetails)") +
                (page?.let { "&pageToken=" + enc(it) } ?: "")
            val o = JSONObject(get(t, url))
            val a = o.optJSONArray("files")
            if (a != null) for (i in 0 until a.length()) out += item(a.getJSONObject(i))
            page = o.optString("nextPageToken").ifEmpty { null }
        } while (page != null && out.size < 5000)
        return out.sortedWith(compareBy<Item>({ !it.isDir }, { it.name.lowercase(Locale.ROOT) }))
    }

    /** 保存した検索の結果。新しい順、最大 500 件。本文検索は並べ替えを指定できないので、受け取ってから並べる。 */
    fun search(t: String, query: jp.yaegaki.theta.home.data.SavedQuery): List<Item> {
        val out = ArrayList<Item>()
        var page: String? = null
        do {
            val url = "$API/files?q=" + enc(query.driveQuery()) +
                "&pageSize=500&corpora=allDrives&includeItemsFromAllDrives=true&supportsAllDrives=true" +
                (if (query.text.isBlank()) "&orderBy=" + enc(if (query.order == "viewed") "viewedByMeTime desc" else "modifiedTime desc") else "") +
                "&fields=" + enc("nextPageToken,files(id,name,mimeType,size,modifiedTime,thumbnailLink,webViewLink,shortcutDetails)") +
                (page?.let { "&pageToken=" + enc(it) } ?: "")
            val o = JSONObject(get(t, url))
            val a = o.optJSONArray("files")
            if (a != null) for (i in 0 until a.length()) out += item(a.getJSONObject(i))
            page = o.optString("nextPageToken").ifEmpty { null }
        } while (page != null && out.size < 500)
        // 最近開いた順はドライブの並びのまま（開いた時刻は一覧に要らないので受け取らない）
        return if (query.order == "viewed" && query.text.isBlank()) out else out.sortedByDescending { it.modified }
    }

    /** ファイルを端末へ取得する。Google ドキュメントは取得できない（リンクで開く）。 */
    fun download(t: String, item: Item, dest: File) {
        val c = open(t, "$API/files/${enc(item.id)}?alt=media&supportsAllDrives=true", 60_000)
        val part = File(dest.path + ".part")
        c.inputStream.use { i -> FileOutputStream(part).use { o -> i.copyTo(o, 65536) } }
        if (!part.renameTo(dest)) throw IOException("保存できませんでした")
    }

    /** Drive が作ったサムネイル。px は辺の長さ。 */
    fun thumb(t: String, item: Item, px: Int): ByteArray? {
        val link = item.thumb ?: return null
        val sized = if (Regex("=s\\d+$").containsMatchIn(link)) link.replace(Regex("=s\\d+$"), "=s$px") else link
        return runCatching { open(t, sized, 15_000).inputStream.use { it.readBytes() } }.getOrNull()
    }

    private fun folder(id: String, name: String) = Item(id, name, FOLDER, 0, 0, null, null)

    private fun item(o: JSONObject): Item {
        // ショートカットは指している先として扱う
        val sc = o.optJSONObject("shortcutDetails")
        val id = sc?.optString("targetId")?.ifEmpty { null } ?: o.getString("id")
        val mime = sc?.optString("targetMimeType")?.ifEmpty { null } ?: o.optString("mimeType")
        return Item(
            id = id,
            name = o.optString("name"),
            mime = mime,
            size = o.optString("size").toLongOrNull() ?: 0,
            modified = runCatching { java.time.Instant.parse(o.optString("modifiedTime")).toEpochMilli() }.getOrDefault(0),
            thumb = o.optString("thumbnailLink").ifEmpty { null },
            link = o.optString("webViewLink").ifEmpty { null },
        )
    }

    private fun get(t: String, url: String): String = open(t, url, 15_000).inputStream.use { String(it.readBytes(), Charsets.UTF_8) }

    private fun open(t: String, url: String, timeout: Int): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = timeout
        c.readTimeout = timeout
        c.setRequestProperty("Authorization", "Bearer $t")
        val code = c.responseCode
        if (code == 401) {
            // 期限切れ・取り消し。次は取り直す
            runCatching { GoogleAuthUtil.clearToken(context, t) }
            token = null
            throw IOException("Google の許可が切れました。もう一度つないでください")
        }
        if (code / 100 != 2) {
            val body = runCatching { c.errorStream?.use { String(it.readBytes(), Charsets.UTF_8) } }.getOrNull().orEmpty()
            val reason = runCatching { JSONObject(body).getJSONObject("error").optString("message") }.getOrNull().orEmpty()
            throw IOException(
                when {
                    code == 403 && (reason.contains("has not been used", true) || reason.contains("disabled", true)) -> "Drive API が有効になっていません（Google Cloud で有効にしてください）"
                    else -> "HTTP $code $reason".trim()
                },
            )
        }
        return c
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun explain(e: Exception): String {
        val m = e.message.orEmpty()
        return when {
            m.contains("10:") || m.contains("DEVELOPER_ERROR") ->
                "このアプリが Google Cloud に登録されていません（OAuth クライアントのパッケージ名と SHA-1 を確かめてください）"
            m.contains("7:") || m.contains("NETWORK_ERROR") -> "ネットワークにつながりません"
            else -> "Google の許可を取れませんでした（$m）"
        }
    }

    companion object {
        const val SCOPE = "https://www.googleapis.com/auth/drive.readonly"
        const val FOLDER = "application/vnd.google-apps.folder"
        const val ROOT = ""
        const val SHARED = "~shared"
        const val DRIVES = "~drives"
        const val STARRED = "~starred"
        private const val API = "https://www.googleapis.com/drive/v3"

        /** Google Cloud の OAuth クライアント（Android）に登録する、このアプリの署名の SHA-1。 */
        fun signingSha1(context: Context): String = runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            val cert = info.signingInfo!!.apkContentsSigners[0].toByteArray()
            MessageDigest.getInstance("SHA-1").digest(cert).joinToString(":") { "%02X".format(it) }
        }.getOrDefault("（取得できませんでした）")
    }
}
