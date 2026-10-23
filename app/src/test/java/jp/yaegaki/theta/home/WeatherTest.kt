package jp.yaegaki.theta.home

import jp.yaegaki.theta.home.data.Weather
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Open-Meteo の応答の見本（app/src/test/resources/weather-sample.json）を、時刻を今に合わせて返す。 */
fun weatherSample(now: Long): String {
    val o = JSONObject(WeatherTest::class.java.getResourceAsStream("/weather-sample.json")!!.bufferedReader().readText())
    val day0 = jp.yaegaki.theta.home.data.Calendar.startOfDay(now) / 1000
    val h = o.getJSONObject("hourly")
    h.put("time", JSONArray((0 until 48).map { day0 + it * 3600L }))
    o.getJSONObject("daily").put("time", JSONArray((0 until 7).map { day0 + it * 86400L }))
    return o.toString()
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WeatherTest {
    @Test
    fun parse() {
        val now = System.currentTimeMillis()
        val f = Weather.parse(weatherSample(now), now)
        assertEquals(18.4, f.temp, 0.01)
        assertEquals(62, f.humidity)
        assertEquals(7, f.days.size)
        assertEquals(24, f.hours.size)
        assertTrue(f.hours.first().time >= now - 3600_000)
        assertEquals(80, f.days[1].rain)
    }

    @Test
    fun codes() {
        assertEquals("clear", Weather.kind(0))
        assertEquals("rain", Weather.kind(63))
        assertEquals("snow", Weather.kind(73))
        assertEquals("thunder", Weather.kind(95))
        assertEquals("雨", Weather.label(63))
    }
}
