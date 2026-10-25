package jp.yaegaki.theta.home.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** 天気を出す場所。useLocation なら端末のおおよその位置（ACCESS_COARSE_LOCATION）で、lat・lon はその都度更新。 */
data class WeatherPlace(val name: String, val lat: Double, val lon: Double, val useLocation: Boolean = false)

data class Hour(val time: Long, val temp: Double, val rain: Int, val code: Int)
data class Day(val date: Long, val code: Int, val max: Double, val min: Double, val rain: Int)

data class Forecast(
    val temp: Double,
    val feels: Double,
    val humidity: Int,
    val wind: Double,
    val code: Int,
    val isDay: Boolean,
    val hours: List<Hour>,
    val days: List<Day>,
    val fetchedAt: Long,
)

/**
 * 天気予報（Open-Meteo。API キー不要・無料）。位置の名前探しも Open-Meteo のジオコーディング。
 * 端末から送るのは緯度・経度（小数 2 桁に丸める）だけ。
 */
object Weather {
    /** 取った JSON もそのまま返す（キャッシュ用）。 */
    fun fetch(place: WeatherPlace): Pair<Forecast, String> {
        val lat = "%.2f".format(java.util.Locale.US, place.lat)
        val lon = "%.2f".format(java.util.Locale.US, place.lon)
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,apparent_temperature,relative_humidity_2m,wind_speed_10m,weather_code,is_day" +
            "&hourly=temperature_2m,precipitation_probability,weather_code" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
            "&timezone=auto&forecast_days=7&timeformat=unixtime&wind_speed_unit=ms"
        val text = get(url)
        return parse(text, System.currentTimeMillis()) to text
    }

    /** 地名から場所を探す（日本語可）。 */
    fun search(name: String): List<WeatherPlace> {
        val o = JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?count=8&language=ja&name=" + URLEncoder.encode(name.trim(), "UTF-8")))
        val a = o.optJSONArray("results") ?: return emptyList()
        return (0 until a.length()).map { a.getJSONObject(it) }.map { r ->
            val label = listOfNotNull(r.optString("name").ifEmpty { null }, r.optString("admin1").ifEmpty { null }).distinct().joinToString(" / ")
            WeatherPlace(label, r.getDouble("latitude"), r.getDouble("longitude"))
        }
    }

    fun parse(text: String, now: Long): Forecast {
        val o = JSONObject(text)
        val c = o.getJSONObject("current")
        val h = o.getJSONObject("hourly")
        val d = o.getJSONObject("daily")
        val ht = h.getJSONArray("time")
        val hours = (0 until ht.length()).map { i ->
            Hour(ht.getLong(i) * 1000, h.getJSONArray("temperature_2m").optDouble(i), h.getJSONArray("precipitation_probability").optInt(i), h.getJSONArray("weather_code").optInt(i))
        }.filter { it.time >= now - 3600_000 }.take(24)
        val dt = d.getJSONArray("time")
        val days = (0 until dt.length()).map { i ->
            Day(
                dt.getLong(i) * 1000, d.getJSONArray("weather_code").optInt(i),
                d.getJSONArray("temperature_2m_max").optDouble(i), d.getJSONArray("temperature_2m_min").optDouble(i),
                d.getJSONArray("precipitation_probability_max").optInt(i),
            )
        }
        return Forecast(
            temp = c.optDouble("temperature_2m"),
            feels = c.optDouble("apparent_temperature"),
            humidity = c.optInt("relative_humidity_2m"),
            wind = c.optDouble("wind_speed_10m"),
            code = c.optInt("weather_code"),
            isDay = c.optInt("is_day", 1) == 1,
            hours = hours,
            days = days,
            fetchedAt = now,
        )
    }

    /** WMO の天気コード → 種類（絵を選ぶ）と日本語。 */
    fun kind(code: Int): String = when (code) {
        0, 1 -> "clear"
        2 -> "partly"
        3 -> "cloudy"
        45, 48 -> "fog"
        in 51..67, in 80..82 -> "rain"
        in 71..77, 85, 86 -> "snow"
        in 95..99 -> "thunder"
        else -> "cloudy"
    }

    fun label(code: Int): String = when (code) {
        0 -> "快晴"
        1 -> "晴れ"
        2 -> "晴れ時々くもり"
        3 -> "くもり"
        45, 48 -> "霧"
        51, 53, 55 -> "霧雨"
        56, 57, 66, 67 -> "着氷性の雨"
        61 -> "小雨"
        63 -> "雨"
        65 -> "強い雨"
        71, 73, 75, 77 -> "雪"
        80, 81 -> "にわか雨"
        82 -> "激しいにわか雨"
        85, 86 -> "にわか雪"
        95 -> "雷雨"
        96, 99 -> "雷雨（ひょう）"
        else -> "—"
    }

    internal fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 10_000
        if (c.responseCode / 100 != 2) throw java.io.IOException("HTTP ${c.responseCode}")
        return c.inputStream.use { String(it.readBytes(), Charsets.UTF_8) }
    }
}
