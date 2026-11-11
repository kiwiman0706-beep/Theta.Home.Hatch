package app.thetahatch.home.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * 人と LINE のトークの結び付けと、よく連絡する人。
 * LINE は友だちの一覧を外に出さないので、Home から見えるトーク（最近のトーク・ホーム画面に追加したもの）を手で結ぶ。
 */
data class LineLink(val shortcutId: String, val label: String)

/**
 * よく連絡する人の 1 人。key は "c:<連絡先の lookupKey>" か "line:<トークのショートカット ID>"（連絡先に無い LINE の相手・グループ）。
 * label は表示名（連絡先の名前・トークの名前）。
 */
data class FavPerson(val key: String, val label: String) {
    val contactKey: String? get() = key.removePrefix("c:").takeIf { key.startsWith("c:") }
    val lineId: String? get() = key.removePrefix("line:").takeIf { key.startsWith("line:") }
}

object PeopleLinks {
    fun linksFromJson(text: String?): Map<String, LineLink> = runCatching {
        val o = JSONObject(text ?: "{}")
        o.keys().asSequence().associateWith { k -> o.getJSONObject(k).let { LineLink(it.getString("id"), it.optString("label")) } }
    }.getOrDefault(emptyMap())

    fun linksToJson(m: Map<String, LineLink>): String =
        JSONObject().apply { m.forEach { (k, v) -> put(k, JSONObject().put("id", v.shortcutId).put("label", v.label)) } }.toString()

    fun favsFromJson(text: String?): List<FavPerson> = runCatching {
        val a = JSONArray(text ?: "[]")
        (0 until a.length()).map { a.getJSONObject(it) }.map { FavPerson(it.getString("key"), it.optString("label")) }
    }.getOrDefault(emptyList())

    fun favsToJson(l: List<FavPerson>): String = JSONArray().apply { l.forEach { put(JSONObject().put("key", it.key).put("label", it.label)) } }.toString()

    /** 名前の近さ（LINE の表示名と連絡先の名前）。空白・敬称を除いて、含む・頭が同じなら高い。 */
    fun similarity(a: String, b: String): Int {
        fun norm(s: String) = s.lowercase().replace(Regex("[\\s　・.]"), "").replace(Regex("(さん|様|ちゃん|くん|君)$"), "")
        val x = norm(a)
        val y = norm(b)
        if (x.isEmpty() || y.isEmpty()) return 0
        if (x == y) return 100
        if (x.contains(y) || y.contains(x)) return 80
        val common = x.toSet().intersect(y.toSet()).size
        return common * 60 / maxOf(x.length, y.length)
    }

    /** 未読：LINE の通知のうち、そのトークのもの（ショートカット ID が同じか、題名がトークの名前）。 */
    fun unread(notices: List<app.thetahatch.home.notify.Notice>, link: LineLink?): Int {
        link ?: return 0
        return notices.count { n -> n.packageName == Contacts.LINE_PACKAGE && (n.shortcutId == link.shortcutId || n.title == link.label) }
    }
}
