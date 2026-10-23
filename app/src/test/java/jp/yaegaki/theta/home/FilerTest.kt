package jp.yaegaki.theta.home

import jp.yaegaki.theta.home.data.TabKind
import jp.yaegaki.theta.home.data.TabSpec
import jp.yaegaki.theta.home.data.ViewSpec
import jp.yaegaki.theta.home.nas.Nas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class FilerTest {
    @Test
    fun viewSpec() {
        assertEquals(ViewSpec(true, 3), ViewSpec.decode("grid:3"))
        assertEquals(ViewSpec(false, 0), ViewSpec.decode(ViewSpec(false, 0).encode()))
        assertEquals(ViewSpec(true, 3), ViewSpec.decode("grid:9"))
        assertNull(ViewSpec.decode("tiles:1"))
        assertNull(ViewSpec.decode(null))
    }

    @Test
    fun nasAddress() {
        assertEquals("http://100.64.1.2:5000", Nas.normalize("100.64.1.2"))
        assertEquals("http://nas.tail1234.ts.net:5000", Nas.normalize("nas.tail1234.ts.net/"))
        assertEquals("http://192.168.1.10:5001", Nas.normalize("192.168.1.10:5001"))
        assertEquals("https://nas.example.com", Nas.normalize("https://nas.example.com/"))
        assertEquals("http://100.64.1.2:5000/sub", Nas.normalize("100.64.1.2/sub"))
    }

    @Test
    fun driveTab() {
        val t = TabSpec("w", TabKind.FOLDER, "WORK", TabSpec.DRIVE_PREFIX + "1AbC:仕事: 2026")
        assertEquals("1AbC" to "仕事: 2026", t.driveFolder)
        assertNull(t.nasPath)
        assertFalse(t.isLocal)
    }
}

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35])
class QueryTest {
    private val now = 1_761_200_000_000L // 2025-10-23T06:13:20Z

    @Test
    fun driveQuery() {
        val q = jp.yaegaki.theta.home.data.SavedQuery(
            "q-1", "x", "drive", kinds = setOf("pdf"), nameContains = "O'Neil", days = 7, starred = true, folderId = "F1",
        )
        assertEquals(
            "trashed = false and mimeType != 'application/vnd.google-apps.folder' and (mimeType = 'application/pdf')" +
                " and name contains 'O\\'Neil' and modifiedTime > '2025-10-16T06:13:20Z' and starred = true and 'F1' in parents",
            q.driveQuery(now),
        )
    }

    @Test
    fun nasKinds() {
        val q = jp.yaegaki.theta.home.data.SavedQuery("q-2", "x", "nas", kinds = setOf("image", "sheet"))
        org.junit.Assert.assertTrue(q.matchesKind("IMG_0001.JPG"))
        org.junit.Assert.assertTrue(q.matchesKind("家計.xlsx"))
        assertFalse(q.matchesKind("memo.pdf"))
        org.junit.Assert.assertTrue(q.copy(kinds = emptySet()).matchesKind("anything.bin"))
    }

    @Test
    fun json() {
        val q = jp.yaegaki.theta.home.data.SavedQuery("q-3", "請求書", "drive", setOf("pdf"), "請求", "合計", 30, false, true, "F", "経理")
        assertEquals(q, jp.yaegaki.theta.home.data.SavedQuery.fromJson(q.toJson()))
    }
}
