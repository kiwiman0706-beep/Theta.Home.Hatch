package app.thetahatch.home.hub

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/**
 * シリーズの共通設定（THETA_SHARED_SETTINGS §4.1）の「だれが・いつ変えたか」。
 * ホームの画面で変えたら by = "home"、ほかのアプリが書き戻したらそのアプリの id。
 */
object SeriesMeta {
    private const val META = "seriesMeta"
    private const val EXTRA = "seriesExtra"
    private const val LENT = "seriesLent"

    /** info で返す項目（設定）。 */
    val INFO = listOf("ui.skin", "gemini.model", "nas.url", "nas.user", "nas.shareRoot", "google.account")

    /** secret で返す項目（秘密。読まれたら知らせる）。 */
    val SECRETS = listOf("gemini.key", "nas.password", "nas.deviceToken")

    /** 秘密らしい名前（info に書かせない・返さない）。 */
    fun looksSecret(key: String) = key in SECRETS || listOf("key", "token", "secret", "password").any { key.lowercase().contains(it) }

    @JvmStatic
    @JvmOverloads
    fun stamp(prefs: SharedPreferences, key: String, by: String = "home") {
        val o = meta(prefs)
        o.put(key, JSONObject().put("by", by).put("at", Instant.now().toString()))
        prefs.edit().putString(META, o.toString()).apply()
    }

    fun by(prefs: SharedPreferences, key: String): String = meta(prefs).optJSONObject(key)?.optString("by").orEmpty()
    fun at(prefs: SharedPreferences, key: String): String = meta(prefs).optJSONObject(key)?.optString("at").orEmpty()

    private fun meta(prefs: SharedPreferences) = runCatching { JSONObject(prefs.getString(META, "{}") ?: "{}") }.getOrDefault(JSONObject())

    /** ホームでは使わないが預かる項目（nas.shareRoot・知らない項目）。 */
    fun extra(prefs: SharedPreferences, key: String): String? =
        runCatching { JSONObject(prefs.getString(EXTRA, "{}") ?: "{}") }.getOrNull()?.takeIf { it.has(key) }?.optString(key)

    fun extraKeys(prefs: SharedPreferences): List<String> =
        runCatching { JSONObject(prefs.getString(EXTRA, "{}") ?: "{}").keys().asSequence().toList() }.getOrDefault(emptyList())

    fun putExtra(prefs: SharedPreferences, key: String, value: String) {
        val o = runCatching { JSONObject(prefs.getString(EXTRA, "{}") ?: "{}") }.getOrDefault(JSONObject())
        prefs.edit().putString(EXTRA, o.put(key, value).toString()).apply()
    }

    /** 秘密を貸した記録（新しい順に 20 件）。 */
    data class Lent(val app: String, val key: String, val at: String)

    fun lent(prefs: SharedPreferences): List<Lent> = runCatching {
        val a = JSONArray(prefs.getString(LENT, "[]"))
        (0 until a.length()).map { a.getJSONObject(it) }.map { Lent(it.optString("app"), it.optString("key"), it.optString("at")) }
    }.getOrDefault(emptyList())

    fun addLent(prefs: SharedPreferences, app: String, key: String) {
        val list = listOf(Lent(app, key, Instant.now().toString())) + lent(prefs)
        val a = JSONArray()
        list.take(20).forEach { a.put(JSONObject().put("app", it.app).put("key", it.key).put("at", it.at)) }
        prefs.edit().putString(LENT, a.toString()).apply()
    }
}
