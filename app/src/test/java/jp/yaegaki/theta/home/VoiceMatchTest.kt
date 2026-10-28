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

    @Test
    fun nickname() {
        val names = mapOf("MoneyForward ME" to listOf("かけいぼ"), "カメラ" to emptyList())
        assertEquals("MoneyForward ME", VoiceMatch.pickAny(names.keys.toList(), { listOf(it) + names[it]!! }, listOf("家計簿", "かけいぼ開いて")))
        assertEquals(true, VoiceMatch.contains("かけいぼ", "カケイ"))
    }
}
