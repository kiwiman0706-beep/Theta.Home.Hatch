package jp.yaegaki.theta.home

import jp.yaegaki.theta.home.data.Office
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipFile

/** python-docx・openpyxl・python-pptx で作った見本（src/test/resources/office）を読む。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OfficeTest {
    private fun zip(name: String) = ZipFile(File(javaClass.classLoader!!.getResource("office/$name")!!.toURI()))

    @Test
    fun xlsx() {
        val sheets = zip("sample.xlsx").use { Office.xlsx(it) }
        assertEquals(listOf("家計簿", "メモ"), sheets.map { it.name })
        val rows = sheets[0].rows
        assertEquals(listOf("日付", "項目", "金額"), rows[0].take(3))
        // 日付の書式のセルは日付に、整数は .0 を付けない
        assertEquals(listOf("2026/10/01", "食費", "1200"), rows[1].take(3))
        assertEquals("230.5", rows[2][2])
        assertEquals(listOf(listOf("", ""), listOf("", "こんにちは")), sheets[1].rows)
    }

    @Test
    fun docx() {
        val blocks = zip("sample.docx").use { Office.docx(it) }
        val paras = blocks.filterIsInstance<Office.Block.Para>().filter { it.text.isNotBlank() }
        assertEquals(Office.Block.Para("会議メモ", level = 1), paras[0])
        assertEquals("10月25日の打ち合わせのまとめです。", paras[1].text)
        assertEquals(2, paras.first { it.text == "決まったこと" }.level)
        assertTrue(paras.first { it.text == "中でファイルを開く" }.list)
        assertEquals(listOf(listOf("担当", "期限", "状態"), listOf("山田", "11/1", "進行中")), blocks.filterIsInstance<Office.Block.Table>().single().rows)
        assertTrue(blocks.filterIsInstance<Office.Block.Image>().single().path.startsWith("word/media/"))
    }

    @Test
    fun pptx() {
        val slides = zip("sample.pptx").use { Office.pptx(it) }
        assertEquals(2, slides.size)
        assertEquals(listOf("Theta Home Hatch", "タブは 2 種類", "中でファイルを開く"), slides[0].texts)
        assertEquals("画面", slides[1].texts.first())
        assertTrue(slides[1].images.single().startsWith("ppt/media/"))
    }

    @Test
    fun helpers() {
        assertEquals(28, Office.colIndex("AB12"))
        assertEquals("2026/10/25 12:00", Office.serialDate("46320.5"))
        assertEquals("ppt/media/a.png", Office.resolve("ppt/slides/", "../media/a.png"))
        assertEquals("xl/worksheets/sheet1.xml", Office.resolve("xl/", "/xl/worksheets/sheet1.xml"))
    }
}
