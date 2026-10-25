package jp.yaegaki.theta.home.data

import android.net.Uri
import java.util.Locale

/** 中で開くファイル 1 つ。 */
data class ViewItem(val uri: Uri, val name: String, val mime: String?)

/**
 * ホームの中（ポップアップ）で開ける形式か。種類は "image"・"video"・"audio"・"pdf"・"text"・"csv"・"zip"・
 * "docx"・"xlsx"・"pptx"（中身のかんたんなプレビュー）、開けなければ null。
 * 拡張子を先に見る（SAF の MIME は application/octet-stream のことがあるので）。
 */
object Viewable {
    private val IMAGE = setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp", "avif")
    private val VIDEO = setOf("mp4", "m4v", "mkv", "webm", "mov", "3gp", "ts")
    private val AUDIO = setOf("mp3", "m4a", "aac", "flac", "ogg", "oga", "opus", "wav", "amr", "mid", "midi")
    private val TEXT = setOf(
        "txt", "md", "markdown", "json", "xml", "log", "ini", "conf", "cfg", "yml", "yaml", "toml", "properties",
        "kt", "kts", "java", "py", "js", "ts", "tsx", "jsx", "css", "html", "htm", "sh", "bat", "ps1", "c", "h", "cpp", "hpp",
        "cs", "go", "rs", "swift", "rb", "php", "sql", "gradle", "srt", "vtt",
    )
    private val CSV = setOf("csv", "tsv")
    private val ZIP = setOf("zip", "jar")

    fun kind(name: String, mime: String?): String? {
        val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val m = mime?.lowercase(Locale.ROOT).orEmpty()
        return when {
            ext in IMAGE -> "image"
            ext in VIDEO -> "video"
            ext in AUDIO -> "audio"
            ext == "pdf" -> "pdf"
            ext in CSV -> "csv"
            ext in ZIP -> "zip"
            ext in TEXT -> "text"
            ext == "docx" -> "docx"
            ext == "xlsx" || ext == "xlsm" -> "xlsx"
            ext == "pptx" -> "pptx"
            m == "image/svg+xml" -> null
            m.startsWith("image/") -> "image"
            m.startsWith("video/") -> "video"
            m.startsWith("audio/") -> "audio"
            m == "application/pdf" -> "pdf"
            m == "text/csv" || m == "text/tab-separated-values" -> "csv"
            m == "application/zip" -> "zip"
            m.startsWith("text/") || m == "application/json" || m == "application/xml" -> "text"
            else -> null
        }
    }

    /** CSV・TSV を読む（"…" の中の区切り・改行・"" に対応）。rows・cols を超えた分は読まない。 */
    fun parseCsv(text: String, tab: Boolean, rows: Int = 2000, cols: Int = 60): List<List<String>> {
        val sep = if (tab) '\t' else ','
        val out = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        fun endCell() { if (row.size < cols) row.add(cell.toString()); cell.clear() }
        fun endRow() { endCell(); out.add(row); row = mutableListOf() }
        while (i < text.length && out.size < rows) {
            val c = text[i]
            when {
                quoted && c == '"' && i + 1 < text.length && text[i + 1] == '"' -> { cell.append('"'); i++ }
                c == '"' && (quoted || cell.isEmpty()) -> quoted = !quoted
                quoted -> cell.append(c)
                c == sep -> endCell()
                c == '\r' -> Unit
                c == '\n' -> endRow()
                else -> cell.append(c)
            }
            i++
        }
        if (out.size < rows && (cell.isNotEmpty() || row.isNotEmpty())) endRow()
        return out
    }

    /** 文字を読む：UTF-8（BOM 付きも）として読めなければ Shift_JIS（Windows の日本語のファイル）。 */
    fun decode(bytes: ByteArray, truncated: Boolean = false): String {
        val body = if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) bytes.copyOfRange(3, bytes.size) else bytes
        val utf8 = Charsets.UTF_8.newDecoder()
            .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
        return runCatching { utf8.decode(java.nio.ByteBuffer.wrap(body)).toString() }.getOrElse {
            // 途中で切ったとき、最後の 1 文字が壊れているだけなら UTF-8 のまま
            (if (truncated) (1..3).firstNotNullOfOrNull { cut ->
                runCatching { utf8.reset(); utf8.decode(java.nio.ByteBuffer.wrap(body, 0, (body.size - cut).coerceAtLeast(0))).toString() }.getOrNull()
            } else null) ?: String(body, charset("MS932"))
        }
    }
}
