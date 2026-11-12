package app.thetahatch.home.drive

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Gmail（読むだけ）。メールのタブで、Gmail の検索（「is:important」＝重要 など）に合うメールだけを出す。
 * 許可はドライブとは別に頼む（gmail.readonly）。Google Cloud のプロジェクトで Gmail API を有効にしておく。
 */
class Gmail(private val context: Context) {
    data class Mail(val id: String, val threadId: String, val from: String, val subject: String, val snippet: String, val date: Long, val unread: Boolean)

    class NeedConsent(val intent: PendingIntent) : IOException("Gmail の許可が要ります")

    @Volatile private var token: String? = null
    @Volatile private var tokenAt = 0L

    private val request get() = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(SCOPE))).build()

    private suspend fun authorize(): AuthorizationResult = suspendCancellableCoroutine { cont ->
        Identity.getAuthorizationClient(context).authorize(request)
            .addOnSuccessListener { cont.resume(it) }
            .addOnFailureListener { cont.resumeWithException(it) }
    }

    suspend fun token(): String {
        token?.takeIf { System.currentTimeMillis() - tokenAt < 45 * 60_000 }?.let { return it }
        val r = try { authorize() } catch (e: Exception) { throw IOException("Gmail の許可を取れませんでした（${e.message}）", e) }
        if (r.hasResolution()) throw NeedConsent(r.pendingIntent ?: throw IOException("許可の画面を出せません"))
        return accept(r)
    }

    fun acceptResult(data: Intent?): Boolean = runCatching { accept(Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data)) }.isSuccess

    private fun accept(r: AuthorizationResult): String {
        val t = r.accessToken ?: throw IOException("トークンを受け取れませんでした")
        token = t; tokenAt = System.currentTimeMillis()
        return t
    }

    /** 検索に合うメール（新しい順に max 件）。 */
    suspend fun search(query: String, max: Int = 25): List<Mail> = withContext(Dispatchers.IO) {
        val t = token()
        val list = JSONObject(get(t, "$API/messages?maxResults=$max&q=" + URLEncoder.encode(query, "UTF-8")))
        val ids = list.optJSONArray("messages")?.let { a -> (0 until a.length()).map { a.getJSONObject(it).getString("id") } }.orEmpty()
        coroutineScope {
            ids.map { id ->
                async {
                    runCatching {
                        val m = JSONObject(get(t, "$API/messages/$id?format=metadata&metadataHeaders=From&metadataHeaders=Subject"))
                        val headers = m.optJSONObject("payload")?.optJSONArray("headers")
                        fun h(name: String) = headers?.let { a -> (0 until a.length()).map { a.getJSONObject(it) }.firstOrNull { it.optString("name").equals(name, true) }?.optString("value") }.orEmpty()
                        val labels = m.optJSONArray("labelIds")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
                        Mail(id, m.optString("threadId"), fromName(h("From")), h("Subject").ifEmpty { "（件名なし）" },
                            android.text.Html.fromHtml(m.optString("snippet"), 0).toString(), m.optString("internalDate").toLongOrNull() ?: 0L, "UNREAD" in labels)
                    }.getOrNull()
                }
            }.awaitAll().filterNotNull().sortedByDescending { it.date }
        }
    }

    private fun get(t: String, url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 15_000
        c.setRequestProperty("Authorization", "Bearer $t")
        val code = c.responseCode
        if (code == 401) {
            runCatching { GoogleAuthUtil.clearToken(context, t) }
            token = null
            throw IOException("Gmail の許可が切れました。もう一度読み込んでください")
        }
        if (code / 100 != 2) {
            val body = runCatching { c.errorStream?.use { String(it.readBytes()) } }.getOrNull().orEmpty()
            val msg = runCatching { JSONObject(body).getJSONObject("error").getString("message") }.getOrDefault("HTTP $code")
            throw IOException(if (msg.contains("has not been used", true) || msg.contains("disabled", true)) "Google Cloud で Gmail API を有効にしてください" else "Gmail：$msg")
        }
        return c.inputStream.use { String(it.readBytes(), Charsets.UTF_8) }
    }

    companion object {
        const val SCOPE = "https://www.googleapis.com/auth/gmail.readonly"
        private const val API = "https://gmail.googleapis.com/gmail/v1/users/me"
        const val GMAIL_PACKAGE = "com.google.android.gm"

        /** 「山田 太郎 <taro@example.com>」→「山田 太郎」。 */
        fun fromName(v: String): String = v.substringBefore('<').trim().trim('"').ifEmpty { v.substringAfter('<').substringBefore('>') }

        /** よく使う絞り込み。 */
        val PRESETS = listOf(
            "重要" to "is:important in:inbox",
            "重要・未読" to "is:important is:unread",
            "スター" to "is:starred",
            "未読" to "is:unread in:inbox",
        )
    }
}
