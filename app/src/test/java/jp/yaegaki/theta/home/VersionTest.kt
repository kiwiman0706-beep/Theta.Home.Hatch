package jp.yaegaki.theta.home

import jp.yaegaki.theta.home.ui.isNewer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionTest {
    @Test
    fun comparesNumericParts() {
        assertTrue(isNewer("1.10.0", "1.9.2"))
        assertTrue(isNewer("0.1.1", "0.1.0"))
        assertTrue(isNewer("2", "1.9.9"))
    }

    @Test
    fun sameOrOlderIsNotNewer() {
        assertFalse(isNewer("1.3.0", "1.3.0"))
        assertFalse(isNewer("1.3", "1.3.0"))
        assertFalse(isNewer("0.9.0", "1.0.0"))
    }

    @Test
    fun ignoresSuffixes() {
        assertFalse(isNewer("0.2.0-alpha", "0.2.0"))
        assertTrue(isNewer("0.3.0-beta.1", "0.2.0"))
    }
}
