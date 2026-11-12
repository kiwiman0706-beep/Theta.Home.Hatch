package app.thetahatch.home

import app.thetahatch.home.data.Readings
import app.thetahatch.home.data.VoiceMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Gemini が付けたアプリの読み：ひらがなだけ残し、名前と同じものは外す。声の照合に使える。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReadingsTest {
    @Test
    fun parse() {
        val labels = mapOf("a" to "Gemini", "b" to "カメラ", "c" to "Slack")
        val got = Readings.parse(
            """[{"key":"a","readings":["じぇみに","ジェミナイ","じぇみない","Gemini"]},{"key":"b","readings":["かめら"]},{"key":"x","readings":["ほか"]}]""",
            labels,
        )
        assertEquals(listOf("じぇみに", "じぇみない"), got["a"])
        assertEquals(emptyList<String>(), got["b"])
        assertTrue("x" !in got)
        // 聞き取りの「ジェミニ」と合う
        assertTrue(VoiceMatch.contains(got.getValue("a")[0], "ジェミニ"))
    }

    @Test
    fun requestListsApps() {
        val r = Readings.request(listOf(Triple("k1", "Slack", "com.Slack")))
        val text = r.getJSONArray("contents").getJSONObject(0).getJSONArray("parts").getJSONObject(0).getString("text")
        assertTrue(text.contains("k1\tSlack\tcom.Slack"))
    }
}
