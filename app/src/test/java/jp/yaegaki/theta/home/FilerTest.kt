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
