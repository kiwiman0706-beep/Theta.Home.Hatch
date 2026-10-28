package jp.yaegaki.theta.home.data

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 保存した検索（仮想フォルダ）。条件に合うファイルを、フォルダのように一覧で出す。
 * source は "drive"（Google ドライブ）か "nas"。text（本文）・starred・shared はドライブだけ。
 * folderId は場所の絞り込み（ドライブはそのフォルダの直下、NAS はそのフォルダ以下すべて）。空ならどこでも。
 */
data class SavedQuery(
    val id: String,
    val name: String,
    val source: String,
    val kinds: Set<String> = emptySet(),
    val nameContains: String = "",
    val text: String = "",
    val days: Int = 0,
    val starred: Boolean = false,
    val shared: Boolean = false,
    val folderId: String = "",
    val folderName: String = "",
    /** 並び："" は更新した順、"viewed" は自分が最近開いた順（ドライブだけ）。 */
    val order: String = "",
) {
    /** 一覧に出す条件の要約。 */
    fun summary(): String = listOfNotNull(
        kinds.takeIf { it.isNotEmpty() }?.joinToString("・") { k -> KINDS.firstOrNull { it.first == k }?.second ?: k },
        nameContains.takeIf { it.isNotBlank() }?.let { "名前「$it」" },
        text.takeIf { it.isNotBlank() }?.let { "本文「$it」" },
        DAYS.firstOrNull { it.first == days && days > 0 }?.second,
        if (starred) "スター付き" else null,
        if (shared) "共有された" else null,
        folderName.takeIf { folderId.isNotEmpty() }?.let { "場所 $it" },
        if (order == "viewed") "最近開いた順" else null,
    ).joinToString(" · ").ifEmpty { "すべてのファイル" }

    /** NAS の結果を種類で絞る（ドライブは問い合わせの段階で絞る）。 */
    fun matchesKind(name: String): Boolean {
        if (kinds.isEmpty()) return true
        val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val mime = Files.mimeOf(name).orEmpty()
        return kinds.any { k ->
            when (k) {
                "image" -> mime.startsWith("image/")
                "video" -> mime.startsWith("video/")
                "audio" -> mime.startsWith("audio/")
                "pdf" -> ext == "pdf"
                "doc" -> ext in setOf("doc", "docx", "odt", "rtf", "txt", "md", "pages")
                "sheet" -> ext in setOf("xls", "xlsx", "ods", "csv", "numbers")
                "slide" -> ext in setOf("ppt", "pptx", "odp", "key")
                else -> false
            }
        }
    }

    /** Drive API の q。 */
    fun driveQuery(now: Long = System.currentTimeMillis()): String {
        val parts = mutableListOf("trashed = false", "mimeType != 'application/vnd.google-apps.folder'")
        if (kinds.isNotEmpty()) parts += kinds.flatMap { DRIVE_MIME[it].orEmpty() }.joinToString(" or ", "(", ")")
        if (nameContains.isNotBlank()) parts += "name contains '${esc(nameContains.trim())}'"
        if (text.isNotBlank()) parts += "fullText contains '${esc(text.trim())}'"
        if (days > 0) parts += "modifiedTime > '${rfc3339(now - days * 86_400_000L)}'"
        if (starred) parts += "starred = true"
        if (shared) parts += "sharedWithMe"
        if (folderId.isNotEmpty()) parts += "'${esc(folderId)}' in parents"
        return parts.joinToString(" and ")
    }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("source", source).put("kinds", JSONArray(kinds.toList()))
        .put("nameContains", nameContains).put("text", text).put("days", days)
        .put("starred", starred).put("shared", shared).put("folderId", folderId).put("folderName", folderName).put("order", order)

    companion object {
        /** 仮想フォルダの ID は "q-" で始める（ドライブのフォルダ ID・NAS のパスと混ざらない）。 */
        const val PREFIX = "q-"

        val KINDS = listOf(
            "image" to "写真", "video" to "動画", "audio" to "音楽", "pdf" to "PDF",
            "doc" to "文書", "sheet" to "表", "slide" to "スライド", "gdocs" to "Google 文書・表・スライド",
        )
        val DAYS = listOf(0 to "いつでも", 1 to "今日", 7 to "7 日以内", 30 to "30 日以内", 365 to "1 年以内")

        private val DRIVE_MIME = mapOf(
            "image" to listOf("mimeType contains 'image/'"),
            "video" to listOf("mimeType contains 'video/'"),
            "audio" to listOf("mimeType contains 'audio/'"),
            "pdf" to listOf("mimeType = 'application/pdf'"),
            "doc" to listOf(
                "mimeType = 'application/vnd.google-apps.document'",
                "mimeType = 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'",
                "mimeType = 'application/msword'",
                "mimeType = 'text/plain'",
            ),
            "sheet" to listOf(
                "mimeType = 'application/vnd.google-apps.spreadsheet'",
                "mimeType = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'",
                "mimeType = 'application/vnd.ms-excel'",
                "mimeType = 'text/csv'",
            ),
            "gdocs" to listOf(
                "mimeType = 'application/vnd.google-apps.document'",
                "mimeType = 'application/vnd.google-apps.spreadsheet'",
                "mimeType = 'application/vnd.google-apps.presentation'",
            ),
            "slide" to listOf(
                "mimeType = 'application/vnd.google-apps.presentation'",
                "mimeType = 'application/vnd.openxmlformats-officedocument.presentationml.presentation'",
                "mimeType = 'application/vnd.ms-powerpoint'",
            ),
        )

        private fun esc(s: String) = s.replace("\\", "\\\\").replace("'", "\\'")

        private fun rfc3339(t: Long): String =
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(t))

        fun fromJson(o: JSONObject) = SavedQuery(
            id = o.getString("id"),
            name = o.getString("name"),
            source = o.optString("source", "drive"),
            kinds = o.optJSONArray("kinds")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }.orEmpty(),
            nameContains = o.optString("nameContains"),
            text = o.optString("text"),
            days = o.optInt("days"),
            starred = o.optBoolean("starred"),
            shared = o.optBoolean("shared"),
            folderId = o.optString("folderId"),
            folderName = o.optString("folderName"),
            order = o.optString("order"),
        )

        /** 初めて使うときに置いておく、ドライブの仮想フォルダ。消しても戻さない。 */
        fun defaults(): List<SavedQuery> = listOf(
            SavedQuery(PREFIX + "recent", "RECENT 7D", "drive", days = 7),
            SavedQuery(PREFIX + "pdf", "PDF", "drive", kinds = setOf("pdf")),
            SavedQuery(PREFIX + "photos", "PHOTOS", "drive", kinds = setOf("image")),
            SavedQuery(PREFIX + "office", "OFFICE", "drive", kinds = setOf("doc", "sheet", "slide")),
            GOOGLE_DOCS,
        )

        /** Google のドキュメント・スプレッドシート・スライドを、自分が最近開いた順に。タブにも初めから置く。 */
        val GOOGLE_DOCS = SavedQuery(PREFIX + "gdocs", "GOOGLE DOCS", "drive", kinds = setOf("gdocs"), order = "viewed")
    }
}
