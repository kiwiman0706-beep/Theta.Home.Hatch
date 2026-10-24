package jp.yaegaki.theta.home

import jp.yaegaki.theta.home.data.VoiceMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceMatchTest {
    private val apps = listOf("LINE", "LINE WORKS", "カメラ", "Gmail", "Google マップ", "電話", "メッセージ")

    private fun pick(vararg heard: String) = VoiceMatch.pick(apps, { it }, heard.toList())

    @Test
    fun exactAndPolite() {
        assertEquals("LINE", pick("ライン", "LINE"))
        assertEquals("カメラ", pick("カメラ開いて"))
        assertEquals("カメラ", pick("かめらを起動して"))
        assertEquals("Gmail", pick("gmail"))
        assertEquals("電話", pick("電話"))
    }

    @Test
    fun prefix() {
        assertEquals("LINE WORKS", pick("line works"))
        assertEquals("Google マップ", pick("グーグルマップ", "Google マップ"))
    }

    @Test
    fun ambiguousOrUnknownIsNull() {
        assertNull(pick("天気"))
    }

    @Test
    fun core() {
        assertEquals("カメラ", VoiceMatch.core("かめら を開いて"))
    }
}
