package jp.yaegaki.theta.home.hatch

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class Release(
    val tag: String,
    val apkName: String,
    val apkUrl: String,
    val sumsUrl: String?,
) {
    /** "v0.1.1" → "0.1.1" */
    val version: String get() = tag.removePrefix("v")
}

/**
 * GitHub Releases の最新版を確認し、APK を取得 → SHA-256 を照合 → Android のインストール確認画面へ渡す。
 * 黙ってインストールすることはできず、必ず利用者の確認を通る。
 */
object HatchInstaller {
    private const val API = "https://api.github.com/repos"

    suspend fun latest(app: HatchApp): Release? = withContext(Dispatchers.IO) {
        val repo = app.repo ?: return@withContext null
        val json = get("$API/${HatchApp.OWNER}/$repo/releases/latest") ?: return@withContext null
        val o = JSONObject(json)
        val assets = o.optJSONArray("assets") ?: return@withContext null
        var apk: JSONObject? = null
        var sums: JSONObject? = null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            val name = a.getString("name")
            if (name.endsWith(".apk") && apk == null) apk = a
            if (name.contains("SHA256", ignoreCase = true)) sums = a
        }
        apk ?: return@withContext null
        Release(o.getString("tag_name"), apk.getString("name"), apk.getString("browser_download_url"), sums?.getString("browser_download_url"))
    }

    /** 取得してハッシュを確かめた APK のファイル。照合に失敗したら IOException。 */
    suspend fun download(context: Context, release: Release, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "hatch").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val out = File(dir, release.apkName.replace('/', '_'))
        val conn = open(release.apkUrl)
        val total = conn.contentLengthLong
        val digest = MessageDigest.getInstance("SHA-256")
        conn.inputStream.use { input ->
            out.outputStream().use { o ->
                val buf = ByteArray(64 * 1024)
                var done = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    o.write(buf, 0, n)
                    digest.update(buf, 0, n)
                    done += n
                    if (total > 0) onProgress(done.toFloat() / total)
                }
            }
        }
        val hash = digest.digest().joinToString("") { "%02x".format(it) }
        val sums = release.sumsUrl?.let { get(it) }
        if (sums != null) {
            val expected = sums.lineSequence()
                .map { it.trim().split(Regex("\\s+")) }
                .firstOrNull { it.size >= 2 && it.last().removePrefix("*").endsWith(release.apkName) }
                ?.first()?.lowercase()
            if (expected != null && expected != hash) {
                out.delete()
                throw IOException("SHA-256 が一致しません")
            }
        }
        out
    }

    /** 提供元不明のアプリのインストールが許可されていなければ、その設定画面を返す。 */
    fun permissionIntent(context: Context): Intent? =
        if (context.packageManager.canRequestPackageInstalls()) null
        else Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    fun install(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        // 前回このアプリが入れたもの（自分自身の更新を含む）なら、Android 12 以降は確認を省ける場合がある
        if (android.os.Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("base.apk", 0, apk.length()).use { o ->
                apk.inputStream().use { it.copyTo(o) }
                session.fsync(o)
            }
            val pi = PendingIntent.getBroadcast(
                context, id,
                Intent(context, InstallResultReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(pi.intentSender)
        }
    }

    private fun open(url: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("User-Agent", "ThetaHomeHatch")
        conn.instanceFollowRedirects = true
        if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode}")
        return conn
    }

    private fun get(url: String): String? = runCatching { open(url).inputStream.bufferedReader().use { it.readText() } }.getOrNull()
}

/** PackageInstaller の結果。確認が必要なときは確認画面を開く。 */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PackageInstaller.STATUS_SUCCESS -> Toast.makeText(context, "インストールしました", Toast.LENGTH_SHORT).show()
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: ""
                Toast.makeText(context, "インストールできませんでした $msg", Toast.LENGTH_LONG).show()
            }
        }
    }
}
