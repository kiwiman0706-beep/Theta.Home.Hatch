package jp.yaegaki.theta.home.hatch

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

/**
 * このアプリ自身の更新。GitHub Releases（試験版＝プレリリースも含む）から一番新しい APK を探す。
 * 取得・SHA-256 の照合・インストールは Hatch シリーズと同じ HatchInstaller を使う。
 */
object SelfUpdate {
    const val REPO = "Theta.Home.Hatch"
    /** 機能追加を頼むときに開く、開発の会話。 */
    const val REQUEST_URL = "https://claude.ai/code/session_01GMSgoGarRChJJQLGtjAFKY"
    const val RELEASES_URL = "https://github.com/${HatchApp.OWNER}/$REPO/releases"

    suspend fun latest(): Release? = withContext(Dispatchers.IO) {
        val c = URL("https://api.github.com/repos/${HatchApp.OWNER}/$REPO/releases?per_page=10").openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 20_000
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.setRequestProperty("User-Agent", "ThetaHomeHatch")
        if (c.responseCode !in 200..299) return@withContext null
        val list = JSONArray(c.inputStream.bufferedReader().use { it.readText() })
        for (i in 0 until list.length()) {
            val r = list.getJSONObject(i)
            if (r.optBoolean("draft")) continue
            val assets = r.optJSONArray("assets") ?: continue
            var apk: Pair<String, String>? = null
            var sums: String? = null
            for (j in 0 until assets.length()) {
                val a = assets.getJSONObject(j)
                val name = a.optString("name")
                if (name.startsWith("ThetaHomeHatch-") && name.endsWith(".apk")) apk = name to a.optString("browser_download_url")
                if (name.contains("SHA256", ignoreCase = true)) sums = a.optString("browser_download_url")
            }
            if (apk != null) return@withContext Release(r.optString("tag_name"), apk.first, apk.second, sums)
        }
        null
    }
}
