package app.thetahatch.home.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** 録音を Gemini で文字起こしする（キーは設定の Gemini のもの）。結果は録音の横に .txt で残す。 */
object Transcribe {
    private val MODELS = listOf("gemini-2.5-flash", "gemini-2.5-flash-lite", "gemini-2.0-flash")
    /** Gemini にそのまま送れる大きさ（約 18 分ぶん）。 */
    const val MAX_BYTES = 18L * 1024 * 1024

    fun textFile(audio: File) = File(audio.parentFile, audio.nameWithoutExtension + ".txt")

    suspend fun run(apiKey: String, audio: File): String = withContext(Dispatchers.IO) {
        if (audio.length() > MAX_BYTES) throw IOException("録音が長すぎて文字起こしできません（18 分くらいまで）")
        val body = JSONObject().put("contents", JSONArray().put(JSONObject().put("parts", JSONArray()
            .put(JSONObject().put("text", "この録音を日本語で文字起こししてください。話した言葉だけを、句読点を付けて返してください。聞き取れない所は（不明）と書いてください。"))
            .put(JSONObject().put("inline_data", JSONObject().put("mime_type", "audio/mp4").put("data", Base64.encodeToString(audio.readBytes(), Base64.NO_WRAP)))))))
            .toString()
        var last: Exception? = null
        for (m in MODELS) {
            try {
                val text = call(apiKey, m, body)
                runCatching { textFile(audio).writeText(text) }
                return@withContext text
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
            ?: throw IOException("文字起こしが返りませんでした")
        return (0 until parts.length()).joinToString("") { parts.getJSONObject(it).optString("text") }.trim()
            .ifEmpty { throw IOException("文字起こしが空でした") }
    }
}
