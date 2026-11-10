package app.thetahatch.home.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** 文字を返す Gemini の呼び出し（文字起こし・読み取り）。使えないモデルなら次へ。 */
object Gemini {
    val TEXT_MODELS = listOf("gemini-2.5-flash", "gemini-2.5-flash-lite", "gemini-2.0-flash")

    suspend fun generate(apiKey: String, body: JSONObject): String = withContext(Dispatchers.IO) {
        var last: Exception? = null
        for (m in TEXT_MODELS) {
            try {
                return@withContext call(apiKey, m, body.toString())
            } catch (e: NotFound) {
                last = e
            }
        }
        throw last ?: IOException("Gemini を呼べませんでした")
    }

    private class NotFound : IOException("モデルが見つかりません")

    private fun call(apiKey: String, model: String, body: String): String {
        val c = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent").openConnection() as HttpURLConnection
        c.connectTimeout = 20_000
        c.readTimeout = 120_000
        c.requestMethod = "POST"
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        c.setRequestProperty("x-goog-api-key", apiKey)
        c.outputStream.use { it.write(body.toByteArray()) }
        val code = c.responseCode
        if (code == 404) throw NotFound()
        if (code !in 200..299) {
            val err = c.errorStream?.bufferedReader()?.readText().orEmpty()
            val msg = runCatching { JSONObject(err).getJSONObject("error").getString("message") }.getOrDefault(err.take(200))
            throw IOException("Gemini HTTP $code：$msg")
        }
        val parts = JSONObject(c.inputStream.bufferedReader().readText())
            .optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
            ?: throw IOException("Gemini から答えが返りませんでした")
        return (0 until parts.length()).joinToString("") { parts.getJSONObject(it).optString("text") }.trim()
            .ifEmpty { throw IOException("Gemini の答えが空でした") }
    }
}
