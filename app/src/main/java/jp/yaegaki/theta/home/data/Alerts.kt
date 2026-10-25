package jp.yaegaki.theta.home.data

import android.content.Context
import org.json.JSONObject
import java.io.File

/** 警報・注意報 1 つ。level は 3＝特別警報・2＝警報・1＝注意報。 */
data class Alert(val name: String, val level: Int)

/** 天気の場所の、今出ている警報・注意報。area は気象庁の市町村の名前。 */
data class AlertState(val area: String, val list: List<Alert>, val fetchedAt: Long)

/**
 * 気象庁の警報・注意報（日本の中だけ）。場所は次の順で決める：
 * 国土地理院の逆ジオコーダで緯度経度 → 市区町村コード、気象庁の地域の一覧（area.json）で府県予報区まで辿る。
 * 政令市の区は、気象庁では市でまとまっているので、区のコードが無ければ市のコードで探す。
 * 地域の一覧は 30 日ごとに取り直し、端末に置いておく。
 */
object Alerts {
    private const val AREA_URL = "https://www.jma.go.jp/bosai/common/const/area.json"

    /** 気象庁の警報・注意報のコード。分からないコードは出さない（改正で増えたもの）。 */
    val NAMES = mapOf(
        "02" to ("暴風雪警報" to 2), "03" to ("大雨警報" to 2), "04" to ("洪水警報" to 2), "05" to ("暴風警報" to 2),
        "06" to ("大雪警報" to 2), "07" to ("波浪警報" to 2), "08" to ("高潮警報" to 2),
        "10" to ("大雨注意報" to 1), "12" to ("大雪注意報" to 1), "13" to ("風雪注意報" to 1), "14" to ("雷注意報" to 1),
        "15" to ("強風注意報" to 1), "16" to ("波浪注意報" to 1), "17" to ("融雪注意報" to 1), "18" to ("洪水注意報" to 1),
        "19" to ("高潮注意報" to 1), "20" to ("濃霧注意報" to 1), "21" to ("乾燥注意報" to 1), "22" to ("なだれ注意報" to 1),
        "23" to ("低温注意報" to 1), "24" to ("霜注意報" to 1), "25" to ("着氷注意報" to 1), "26" to ("着雪注意報" to 1),
        "32" to ("暴風雪特別警報" to 3), "33" to ("大雨特別警報" to 3), "35" to ("暴風特別警報" to 3),
        "36" to ("大雪特別警報" to 3), "37" to ("波浪特別警報" to 3), "38" to ("高潮特別警報" to 3),
    )

    /** 地域：市町村（class20）のコードと名前、府県予報区（office）のコード、一次細分区域（class10）のコード。 */
    data class Area(val code: String, val name: String, val office: String, val class10: String)

    fun fetch(ctx: Context, lat: Double, lon: Double): AlertState {
        val area = resolve(ctx, lat, lon) ?: throw java.io.IOException("日本の外か、場所が分かりませんでした")
        val text = Weather.get("https://www.jma.go.jp/bosai/warning/data/warning/${area.office}.json")
        return AlertState(area.name, parse(text, area), System.currentTimeMillis())
    }

    /** 緯度経度から気象庁の地域へ。 */
    fun resolve(ctx: Context, lat: Double, lon: Double): Area? {
        val geo = JSONObject(Weather.get("https://mreversegeocoder.gsi.go.jp/reverse-geocoder/LonLatToAddress?lat=$lat&lon=$lon"))
        val muni = geo.optJSONObject("results")?.optString("muniCd").orEmpty().padStart(5, '0')
        if (muni.isBlank() || muni == "00000") return null
        return findArea(areaJson(ctx), muni)
    }

    /** 市区町村コード（5 桁）から地域を探す。政令市の区（14104 など）は市（14100）で。 */
    fun findArea(areas: JSONObject, muni: String): Area? {
        val class20s = areas.getJSONObject("class20s")
        val code = listOf(muni + "00", muni.take(4) + "0" + "00").firstOrNull { class20s.has(it) } ?: return null
        val c20 = class20s.getJSONObject(code)
        val c15 = areas.getJSONObject("class15s").optJSONObject(c20.optString("parent")) ?: return null
        val c10code = c15.optString("parent")
        val c10 = areas.getJSONObject("class10s").optJSONObject(c10code) ?: return null
        return Area(code, c20.optString("name"), c10.optString("parent"), c10code)
    }

    /** その市町村に出ている（発表・継続）警報・注意報。市町村の欄が無ければ一次細分区域の欄で。強いものから。 */
    fun parse(text: String, area: Area): List<Alert> {
        val o = JSONObject(text)
        val types = o.optJSONArray("areaTypes") ?: return emptyList()
        val all = (0 until types.length()).flatMap { i ->
            val a = types.getJSONObject(i).optJSONArray("areas") ?: return@flatMap emptyList()
            (0 until a.length()).map { a.getJSONObject(it) }
        }
        val mine = all.firstOrNull { it.optString("code") == area.code } ?: all.firstOrNull { it.optString("code") == area.class10 } ?: return emptyList()
        val w = mine.optJSONArray("warnings") ?: return emptyList()
        return (0 until w.length()).map { w.getJSONObject(it) }
            .filter { it.optString("status") in setOf("発表", "継続") }
            .mapNotNull { NAMES[it.optString("code")]?.let { (name, level) -> Alert(name, level) } }
            .distinct()
            .sortedByDescending { it.level }
    }

    /** 気象庁の地域の一覧。端末に置いたものを 30 日使う。 */
    private fun areaJson(ctx: Context): JSONObject {
        val f = File(ctx.filesDir, "jma-area.json")
        if (!f.exists() || System.currentTimeMillis() - f.lastModified() > 30L * 86_400_000) {
            runCatching { Weather.get(AREA_URL) }.onSuccess { f.writeText(it) }
        }
        return JSONObject(f.readText())
    }
}
