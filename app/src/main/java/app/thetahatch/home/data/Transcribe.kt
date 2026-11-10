package app.thetahatch.home.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

/** 録音を Gemini で文字起こしする（キーは設定の Gemini のもの）。結果は録音の横に .txt で残す。 */
object Transcribe {
    /** Gemini にそのまま送れる大きさ（約 18 分ぶん）。 */
    const val MAX_BYTES = 18L * 1024 * 1024

    fun textFile(audio: File) = File(audio.parentFile, audio.nameWithoutExtension + ".txt")

    suspend fun run(apiKey: String, audio: File): String = withContext(Dispatchers.IO) {
        if (audio.length() > MAX_BYTES) throw IOException("録音が長すぎて文字起こしできません（18 分くらいまで）")
        val body = JSONObject().put("contents", JSONArray().put(JSONObject().put("parts", JSONArray()
            .put(JSONObject().put("text", "この録音を日本語で文字起こししてください。話した言葉だけを、句読点を付けて返してください。聞き取れない所は（不明）と書いてください。"))
            .put(JSONObject().put("inline_data", JSONObject().put("mime_type", "audio/mp4").put("data", Base64.encodeToString(audio.readBytes(), Base64.NO_WRAP)))))))
        val text = Gemini.generate(apiKey, body)
        runCatching { textFile(audio).writeText(text) }
        text
    }
}
