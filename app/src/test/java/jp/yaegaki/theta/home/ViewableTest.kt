package jp.yaegaki.theta.home

import jp.yaegaki.theta.home.data.Viewable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ViewableTest {
    @Test
    fun kinds() {
        assertEquals("image", Viewable.kind("IMG_0001.HEIC", null))
        assertEquals("video", Viewable.kind("clip.mkv", "application/octet-stream"))
        assertEquals("audio", Viewable.kind("rec.m4a", null))
        assertEquals("pdf", Viewable.kind("a", "application/pdf"))
        assertEquals("csv", Viewable.kind("data.tsv", null))
        assertEquals("text", Viewable.kind("main.kt", null))
        assertEquals("zip", Viewable.kind("x.zip", null))
        assertEquals("docx", Viewable.kind("report.docx", null))
        assertNull(Viewable.kind("old.doc", null))
        assertNull(Viewable.kind("logo.svg", "image/svg+xml"))
    }

    @Test
    fun csv() {
        val rows = Viewable.parseCsv("名前,メモ\r\n\"山田, 太郎\",\"1行目\n2行目\"\n\"\"\"引用\"\"\",x\n", tab = false)
        assertEquals(listOf(listOf("名前", "メモ"), listOf("山田, 太郎", "1行目\n2行目"), listOf("\"引用\"", "x")), rows)
        assertEquals(listOf(listOf("a", "b")), Viewable.parseCsv("a\tb", tab = true))
    }

    @Test
    fun decode() {
        assertEquals("日本語", Viewable.decode("日本語".toByteArray(Charsets.UTF_8)))
        assertEquals("日本語", Viewable.decode(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "日本語".toByteArray(Charsets.UTF_8)))
        assertEquals("日本語", Viewable.decode("日本語".toByteArray(charset("MS932"))))
        // 途中で切った UTF-8 は、壊れた最後の 1 文字を落とす
        val cut = "あいう".toByteArray(Charsets.UTF_8).let { it.copyOf(it.size - 1) }
        assertEquals("あい", Viewable.decode(cut, truncated = true))
    }
}
