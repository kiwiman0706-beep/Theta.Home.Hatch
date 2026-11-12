package app.thetahatch.home.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * アプリの名前から、声で呼ぶときの読み（ひらがな）を Gemini に考えてもらう。
 * 英語の名前（Gemini → じぇみに・ジェミナイ、Slack → すらっく）や、略して呼ぶ言い方（ユーチューブ → ようつべ）を、いくつか。
 */
object Readings {
    private fun str() = JSONObject().put("type", "STRING")

    /** apps は (キー, 名前, パッケージ)。 */
    fun request(apps: List<Triple<String, String, String>>): JSONObject {
        val lines = apps.joinToString("\n") { (k, label, pkg) -> "$k\t$label\t$pkg" }
        val ask = "スマホのアプリの一覧です（キー・名前・パッケージ名をタブで区切り）。日本の人が声でそのアプリを呼ぶときの読み方を、" +
            "ひらがなで 1〜4 個ずつ考えてください。英語の名前は日本での読み方（例 Gemini → じぇみに、じぇみない。Slack → すらっく）、" +
            "よく使われる略し方・言い換えも（例 YouTube → ゆーちゅーぶ、ようつべ）。漢字の名前はその読み。" +
            "名前がすでにひらがなだけなら空でよいです。キーはそのまま返してください。\n\n$lines"
        val schema = JSONObject().put("type", "ARRAY").put("items", JSONObject().put("type", "OBJECT").put("properties",
            JSONObject().put("key", str()).put("readings", JSONObject().put("type", "ARRAY").put("items", str()))))
        return JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", ask)))))
            .put("generationConfig", JSONObject().put("responseMimeType", "application/json").put("responseSchema", schema))
    }

    /** 答えから キー → 読み。ひらがな（と長音）以外は外し、名前と同じものも外す。 */
    fun parse(json: String, labels: Map<String, String>): Map<String, List<String>> {
        val a = JSONArray(json)
        return (0 until a.length()).map { a.getJSONObject(it) }.mapNotNull { o ->
            val key = o.optString("key").takeIf { it in labels } ?: return@mapNotNull null
            val r = o.optJSONArray("readings")?.let { x -> (0 until x.length()).map { x.optString(it).trim() } }.orEmpty()
                .filter { it.isNotEmpty() && it.all { c -> c in 'ぁ'..'ゖ' || c == 'ー' || c == ' ' } }
                .filter { VoiceMatch.normalize(it) != VoiceMatch.normalize(labels.getValue(key)) }
                .distinct().take(4)
            key to r
        }.toMap()
    }
}
