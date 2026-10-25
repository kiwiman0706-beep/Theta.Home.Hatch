package jp.yaegaki.theta.home

import jp.yaegaki.theta.home.data.Alert
import jp.yaegaki.theta.home.data.Alerts
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 気象庁の area.json・警報の JSON の形（抜粋）で、場所の決め方と読み取りを確かめる。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AlertsTest {
    private val areas = JSONObject(
        """
        {"offices":{"130000":{"name":"東京都"},"140000":{"name":"神奈川県"}},
         "class10s":{"130010":{"name":"東京地方","parent":"130000"},"140010":{"name":"東部","parent":"140000"}},
         "class15s":{"130011":{"name":"23区東部","parent":"130010"},"140011":{"name":"横浜・川崎","parent":"140010"}},
         "class20s":{"1310400":{"name":"新宿区","parent":"130011"},"1410000":{"name":"横浜市","parent":"140011"}}}
        """,
    )

    @Test
    fun findArea() {
        val shinjuku = Alerts.findArea(areas, "13104")!!
        assertEquals("1310400", shinjuku.code)
        assertEquals("130000", shinjuku.office)
        assertEquals("130010", shinjuku.class10)
        // 政令市の区（横浜市中区 14104）は市（1410000）で
        assertEquals("横浜市", Alerts.findArea(areas, "14104")!!.name)
        assertNull(Alerts.findArea(areas, "99999"))
    }

    @Test
    fun parse() {
        val area = Alerts.findArea(areas, "13104")!!
        val text = """
            {"reportDatetime":"2026-10-25T10:00:00+09:00","areaTypes":[
              {"areas":[{"code":"130010","warnings":[{"code":"14","status":"継続"}]}]},
              {"areas":[
                {"code":"1310100","warnings":[{"code":"03","status":"発表"}]},
                {"code":"1310400","warnings":[{"code":"14","status":"継続"},{"code":"03","status":"発表"},{"code":"15","status":"解除"},{"code":"99","status":"発表"}]}
              ]}]}
        """
        // 解除は出さない、分からないコードも出さない、強いものから
        assertEquals(listOf(Alert("大雨警報", 2), Alert("雷注意報", 1)), Alerts.parse(text, area))
        val none = """{"areaTypes":[{"areas":[{"code":"1310400","warnings":[{"status":"発表警報・注意報はなし"}]}]}]}"""
        assertEquals(emptyList<Alert>(), Alerts.parse(none, area))
    }
}
