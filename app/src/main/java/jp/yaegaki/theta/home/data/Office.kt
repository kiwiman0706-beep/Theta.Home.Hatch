package jp.yaegaki.theta.home.data

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile

/**
 * Word・Excel・PowerPoint（docx・xlsx・pptx）のかんたんなプレビュー。中身は zip の中の XML なので、
 * ライブラリを足さずに読んで、文字・表・画像だけを取り出す（見た目どおりの体裁にはしない）。
 */
object Office {
    /** Excel のシート 1 枚。 */
    data class Sheet(val name: String, val rows: List<List<String>>)

    /** Word の中身の 1 かたまり。 */
    sealed class Block {
        /** level は見出しの段（1〜、0 は本文）。list は箇条書き。 */
        data class Para(val text: String, val level: Int = 0, val list: Boolean = false) : Block()
        data class Table(val rows: List<List<String>>) : Block()
        /** zip の中の画像の場所。 */
        data class Image(val path: String) : Block()
    }

    /** PowerPoint のスライド 1 枚：文字（1 つ目を題に）と画像の場所。 */
    data class Slide(val texts: List<String>, val images: List<String>)

    // ---- Excel ----

    fun xlsx(zip: ZipFile, maxRows: Int = 2000, maxCols: Int = 60): List<Sheet> {
        val strings = zip.read("xl/sharedStrings.xml")?.let(::sharedStrings).orEmpty()
        val dateStyles = zip.read("xl/styles.xml")?.let(::dateStyles).orEmpty()
        val rels = zip.read("xl/_rels/workbook.xml.rels")?.let(::rels).orEmpty()
        val sheets = mutableListOf<Pair<String, String>>()
        zip.read("xl/workbook.xml")?.let { s ->
            parse(s) { p ->
                if (p.eventType == XmlPullParser.START_TAG && p.name == "sheet") {
                    val target = rels[p.relId()] ?: return@parse
                    sheets += p.attr("name") to resolve("xl/", target)
                }
            }
        }
        return sheets.map { (name, path) ->
            Sheet(name, zip.read(path)?.let { sheet(it, strings, dateStyles, maxRows, maxCols) }.orEmpty())
        }
    }

    private fun sharedStrings(s: InputStream): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var inT = false
        // ふりがな（rPh）の中の文字は飛ばす
        var inPh = false
        parse(s) { p ->
            when (p.eventType) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "si" -> cur.clear()
                    "rPh" -> inPh = true
                    "t" -> inT = !inPh
                }
                XmlPullParser.END_TAG -> when (p.name) {
                    "si" -> out += cur.toString()
                    "rPh" -> inPh = false
                    "t" -> inT = false
                }
                XmlPullParser.TEXT -> if (inT) cur.append(p.text)
            }
        }
        return out
    }

    /** 日付の書式の付いたスタイルの番号（cellXfs の何番目か）。 */
    private fun dateStyles(s: InputStream): Set<Int> {
        val custom = mutableMapOf<Int, String>()
        val xfs = mutableListOf<Int>()
        var inXfs = false
        parse(s) { p ->
            if (p.eventType == XmlPullParser.START_TAG) when (p.name) {
                "numFmt" -> p.attr("numFmtId").toIntOrNull()?.let { custom[it] = p.attr("formatCode") }
                "cellXfs" -> inXfs = true
                "xf" -> if (inXfs) xfs += p.attr("numFmtId").toIntOrNull() ?: 0
            } else if (p.eventType == XmlPullParser.END_TAG && p.name == "cellXfs") inXfs = false
        }
        fun isDate(id: Int) = id in 14..22 || id in 45..47 || custom[id]?.let { f ->
            val plain = f.replace(Regex("\"[^\"]*\"|\\[[^]]*]"), "").lowercase()
            plain.contains('y') || plain.contains('d') || (plain.contains('m') && !plain.contains('0'))
        } == true
        return xfs.withIndex().filter { isDate(it.value) }.map { it.index }.toSet()
    }

    private fun sheet(s: InputStream, strings: List<String>, dates: Set<Int>, maxRows: Int, maxCols: Int): List<List<String>> {
        val rows = sortedMapOf<Int, MutableMap<Int, String>>()
        var rowNo = 0
        var col = 0
        var type = ""
        var style = 0
        var inV = false
        var inIs = false
        val value = StringBuilder()
        parse(s) { p ->
            when (p.eventType) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "row" -> rowNo = p.attr("r").toIntOrNull() ?: (rowNo + 1)
                    "c" -> {
                        val ref = p.attr("r")
                        col = if (ref.isNotEmpty()) colIndex(ref) else col + 1
                        type = p.attr("t")
                        style = p.attr("s").toIntOrNull() ?: 0
                        value.clear()
                    }
                    "v" -> inV = true
                    "is" -> inIs = true
                    "t" -> if (inIs) inV = true
                }
                XmlPullParser.TEXT -> if (inV) value.append(p.text)
                XmlPullParser.END_TAG -> when (p.name) {
                    "v", "t" -> inV = false
                    "is" -> inIs = false
                    "c" -> if (rowNo in 1..maxRows && col in 1..maxCols && value.isNotEmpty()) {
                        val raw = value.toString()
                        val text = when (type) {
                            "s" -> raw.toIntOrNull()?.let { strings.getOrNull(it) } ?: raw
                            "b" -> if (raw == "1") "TRUE" else "FALSE"
                            "str", "inlineStr", "e" -> raw
                            else -> if (style in dates) serialDate(raw) else number(raw)
                        }
                        rows.getOrPut(rowNo) { mutableMapOf() }[col] = text
                    }
                }
            }
        }
        if (rows.isEmpty()) return emptyList()
        val last = rows.lastKey().coerceAtMost(maxRows)
        val width = rows.values.maxOf { it.keys.maxOrNull() ?: 0 }
        return (1..last).map { r -> (1..width).map { c -> rows[r]?.get(c).orEmpty() } }
    }

    /** "AB12" の列（1 始まり）。 */
    fun colIndex(ref: String): Int = ref.takeWhile { it.isLetter() }.fold(0) { acc, ch -> acc * 26 + (ch.uppercaseChar() - 'A' + 1) }

    /** Excel の日付（1899-12-30 からの日数）を 2026/10/25 の形に。時刻があれば時:分も。 */
    fun serialDate(raw: String): String {
        val v = raw.toDoubleOrNull() ?: return raw
        val days = kotlin.math.floor(v).toLong()
        val date = java.time.LocalDate.of(1899, 12, 30).plusDays(days)
        val minutes = Math.round((v - days) * 24 * 60)
        val d = "%d/%02d/%02d".format(date.year, date.monthValue, date.dayOfMonth)
        return if (minutes == 0L) d else "$d %02d:%02d".format(minutes / 60, minutes % 60)
    }

    /** 1.0 は 1、浮動小数の誤差（0.30000000000000004）は丸める。 */
    private fun number(raw: String): String {
        val v = raw.toDoubleOrNull() ?: return raw
        if (v == kotlin.math.floor(v) && kotlin.math.abs(v) < 1e15) return v.toLong().toString()
        return java.math.BigDecimal(v).round(java.math.MathContext(12)).stripTrailingZeros().toPlainString()
    }

    // ---- Word ----

    fun docx(zip: ZipFile, maxBlocks: Int = 3000): List<Block> {
        val (headings, lists) = zip.read("word/styles.xml")?.let(::paraStyles) ?: (emptyMap<String, Int>() to emptySet())
        val rels = zip.read("word/_rels/document.xml.rels")?.let(::rels).orEmpty()
        val out = mutableListOf<Block>()
        val doc = zip.read("word/document.xml") ?: return out
        val para = StringBuilder()
        var level = 0
        var list = false
        var tableDepth = 0
        var table = mutableListOf<MutableList<String>>()
        var cell = StringBuilder()
        var inT = false
        // 文字の中のタブだけ（段落の書式のタブ位置は飛ばす）
        var inRun = false
        val images = mutableListOf<String>()
        parse(doc) { p ->
            if (out.size >= maxBlocks) return@parse
            when (p.eventType) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "p" -> { para.clear(); level = 0; list = false; images.clear() }
                    "pStyle" -> p.attr("val").let { level = headings[it] ?: 0; if (it in lists) list = true }
                    "numPr" -> list = true
                    "r" -> inRun = true
                    "t" -> inT = true
                    "tab" -> if (inRun) para.append('\t')
                    "br", "cr" -> if (inRun) para.append('\n')
                    "blip" -> rels[p.attr("embed")]?.let { images += resolve("word/", it) }
                    "tbl" -> { if (tableDepth == 0) table = mutableListOf(); tableDepth++ }
                    "tr" -> if (tableDepth == 1) table.add(mutableListOf())
                    "tc" -> if (tableDepth == 1) cell = StringBuilder()
                }
                XmlPullParser.TEXT -> if (inT) para.append(p.text)
                XmlPullParser.END_TAG -> when (p.name) {
                    "t" -> inT = false
                    "r" -> inRun = false
                    "p" -> {
                        val text = para.toString()
                        if (tableDepth > 0) {
                            // 表の中の段落はセルの中で改行
                            if (cell.isNotEmpty() && text.isNotEmpty()) cell.append('\n')
                            cell.append(text)
                        } else {
                            if (text.isNotBlank() || images.isEmpty()) out += Block.Para(text, level, list)
                            images.forEach { out += Block.Image(it) }
                        }
                    }
                    "tc" -> if (tableDepth == 1) table.lastOrNull()?.add(cell.toString())
                    "tbl" -> { tableDepth--; if (tableDepth == 0 && table.isNotEmpty()) out += Block.Table(table) }
                }
            }
        }
        // 空の段落が続くところは 1 つに
        return out.filterIndexed { i, b -> !(b is Block.Para && b.text.isBlank() && (out.getOrNull(i - 1) as? Block.Para)?.text?.isBlank() == true) }
    }

    /**
     * 段落のスタイル：見出し（styleId → 段）と、箇条書きのスタイル（番号付けを持つもの・名前が list）。
     * 日本語の Word は styleId が "1" などなので、名前（heading 1・Title）で見る。
     */
    private fun paraStyles(s: InputStream): Pair<Map<String, Int>, Set<String>> {
        val headings = mutableMapOf<String, Int>()
        val lists = mutableSetOf<String>()
        var id = ""
        parse(s) { p ->
            if (p.eventType != XmlPullParser.START_TAG) return@parse
            when (p.name) {
                "style" -> id = p.attr("styleId")
                "numPr" -> lists += id
                "name" -> {
                    val n = p.attr("val").lowercase()
                    when {
                        n == "title" -> headings[id] = 1
                        n.startsWith("heading") -> headings[id] = (n.removePrefix("heading").trim().toIntOrNull() ?: 1).coerceIn(1, 6)
                        n.startsWith("list bullet") || n.startsWith("list number") -> lists += id
                    }
                }
            }
        }
        return headings to lists
    }

    // ---- PowerPoint ----

    fun pptx(zip: ZipFile, maxSlides: Int = 300): List<Slide> {
        val rels = zip.read("ppt/_rels/presentation.xml.rels")?.let(::rels).orEmpty()
        val order = mutableListOf<String>()
        zip.read("ppt/presentation.xml")?.let { s ->
            parse(s) { p -> if (p.eventType == XmlPullParser.START_TAG && p.name == "sldId") rels[p.relId()]?.let { order += resolve("ppt/", it) } }
        }
        return order.take(maxSlides).map { path ->
            val slideRels = zip.read(path.substringBeforeLast('/') + "/_rels/" + path.substringAfterLast('/') + ".rels")?.let(::rels).orEmpty()
            val texts = mutableListOf<String>()
            val images = mutableListOf<String>()
            val para = StringBuilder()
            var inT = false
            zip.read(path)?.let { s ->
                parse(s) { p ->
                    when (p.eventType) {
                        XmlPullParser.START_TAG -> when (p.name) {
                            "p" -> para.clear()
                            "t" -> inT = true
                            "br" -> para.append('\n')
                            "blip" -> slideRels[p.attr("embed")]?.let { images += resolve(path.substringBeforeLast('/') + "/", it) }
                        }
                        XmlPullParser.TEXT -> if (inT) para.append(p.text)
                        XmlPullParser.END_TAG -> when (p.name) {
                            "t" -> inT = false
                            "p" -> if (para.isNotBlank()) texts += para.toString()
                        }
                    }
                }
            }
            Slide(texts, images.distinct())
        }
    }

    // ---- 共通 ----

    /** 関係（rId → 行き先）。 */
    private fun rels(s: InputStream): Map<String, String> {
        val out = mutableMapOf<String, String>()
        parse(s) { p -> if (p.eventType == XmlPullParser.START_TAG && p.name == "Relationship") out[p.attr("Id")] = p.attr("Target") }
        return out
    }

    /** 関係の行き先を zip の中の場所に（"../media/a.png" や "/xl/worksheets/sheet1.xml" も）。 */
    fun resolve(base: String, target: String): String {
        if (target.startsWith("/")) return target.removePrefix("/")
        val parts = (base.trimEnd('/').split('/') + target.split('/')).toMutableList()
        val out = ArrayDeque<String>()
        parts.forEach { when (it) { "", "." -> Unit; ".." -> out.removeLastOrNull(); else -> out.addLast(it) } }
        return out.joinToString("/")
    }

    private fun ZipFile.read(path: String): InputStream? = getEntry(path)?.let { getInputStream(it) }

    /** 名前空間を見ずに、ローカル名で読む（w:t も a:t も "t"）。 */
    private fun parse(s: InputStream, each: (XmlPullParser) -> Unit) {
        s.use {
            val p = Xml.newPullParser()
            p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            p.setInput(it, null)
            while (p.next() != XmlPullParser.END_DOCUMENT) each(p)
        }
    }

    /** 関係の id（r:id）。sldId のように名前空間の無い id も持つものがあるので、名前空間の付いた方を。 */
    private fun XmlPullParser.relId(): String {
        for (i in 0 until attributeCount) if (getAttributeName(i) == "id" && !getAttributeNamespace(i).isNullOrEmpty()) return getAttributeValue(i)
        return ""
    }

    /** 名前空間を問わず属性を引く（w:val・r:embed など）。 */
    private fun XmlPullParser.attr(local: String): String {
        for (i in 0 until attributeCount) if (getAttributeName(i) == local) return getAttributeValue(i)
        return ""
    }

    /** 画面で開くために、content:// のファイルを端末の中に写す（zip は途中を読むので）。 */
    fun copyToCache(ctx: android.content.Context, uri: android.net.Uri): File {
        val dir = File(ctx.cacheDir, "office").apply { mkdirs() }
        dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(5)?.forEach { it.delete() }
        val f = File(dir, uri.toString().hashCode().toString() + ".zip")
        ctx.contentResolver.openInputStream(uri)!!.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
        return f
    }
}
