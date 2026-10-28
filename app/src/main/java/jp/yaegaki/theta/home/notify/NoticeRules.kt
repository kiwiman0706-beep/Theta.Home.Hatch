package jp.yaegaki.theta.home.notify

import jp.yaegaki.theta.home.data.VoiceMatch
import org.json.JSONArray
import org.json.JSONObject

/**
 * ホームの「通知」の区画に出す通知の条件。アプリ（空ならどれでも）・含む言葉（どれか 1 つ。空ならどれでも）・除く言葉（1 つでもあれば出さない）。
 * 言葉は題名と本文の両方から探す（ひらがな・カタカナ・全角半角・大文字小文字の違いは見ない）。alert なら目立つ色で出す。
 */
data class NoticeRule(
    val id: String,
    val name: String,
    val apps: List<String> = emptyList(),
    val include: List<String> = emptyList(),
    val exclude: List<String> = emptyList(),
    val alert: Boolean = false,
) {
    fun matches(n: Notice): Boolean {
        if (apps.isNotEmpty() && n.packageName !in apps) return false
        val body = n.title + "\n" + n.text
        if (exclude.any { it.isNotBlank() && VoiceMatch.contains(body, it) }) return false
        return include.isEmpty() || include.any { it.isNotBlank() && VoiceMatch.contains(body, it) }
    }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("alert", alert)
        .put("apps", JSONArray(apps)).put("include", JSONArray(include)).put("exclude", JSONArray(exclude))

    companion object {
        fun fromJson(o: JSONObject) = NoticeRule(
            o.getString("id"), o.optString("name"),
            strings(o.optJSONArray("apps")), strings(o.optJSONArray("include")), strings(o.optJSONArray("exclude")),
            o.optBoolean("alert"),
        )

        private fun strings(a: JSONArray?): List<String> = if (a == null) emptyList() else (0 until a.length()).map { a.getString(it) }

        /** 「、」「,」・改行で区切った言葉。 */
        fun words(text: String): List<String> = text.split(',', '、', '，', '\n').map { it.trim() }.filter { it.isNotEmpty() }.distinct()

        /** 条件に合う通知と、合った条件（最初のもの）。新しい順。 */
        fun pick(rules: List<NoticeRule>, notices: List<Notice>): List<Pair<Notice, NoticeRule>> =
            if (rules.isEmpty()) emptyList() else notices.mapNotNull { n -> rules.firstOrNull { it.matches(n) }?.let { n to it } }
    }
}
