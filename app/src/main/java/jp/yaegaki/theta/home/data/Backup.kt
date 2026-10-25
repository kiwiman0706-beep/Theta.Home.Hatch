package jp.yaegaki.theta.home.data

import android.appwidget.AppWidgetManager
import android.content.Context
import android.net.Uri
import android.os.Build
import jp.yaegaki.theta.home.hatch.HatchCatalog
import org.json.JSONArray
import org.json.JSONObject

/** 入っていないアプリ。hatch なら Play ストアではなく Hatch のドロワーから入れる。 */
data class MissingApp(val packageName: String, val label: String, val hatch: Boolean)

/** 読み込みの結果：入っていないアプリ、作り直しが要るウィジェット、選び直しが要るフォルダ、NAS のパスワード。 */
data class ImportReport(
    val missing: List<MissingApp>,
    val widgets: List<String>,
    val folders: List<String>,
    val nasNeedsPassword: Boolean,
)

/**
 * すべての設定を 1 つの JSON にまとめて書き出す・読み込む（新しいスマホへの引っ越し用）。
 * 端末ごとの許可（フォルダの許可・ウィジェットの割り当て）とパスワード・API キーは移せないので、読み込み後に案内する。
 */
object Backup {
    const val FORMAT = "theta-home-settings"
    const val VERSION = 1

    fun fileName(): String = "theta-home-settings-" + java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US).format(java.util.Date()) + ".json"

    fun export(context: Context, store: Store, apps: List<AppEntry>): String {
        val awm = AppWidgetManager.getInstance(context)
        val o = JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("exportedAt", System.currentTimeMillis())
            .put("device", Build.MANUFACTURER + " " + Build.MODEL)
            .put("tabs", JSONArray(store.prefs.getString("tabs", null) ?: tabsJson(Store.defaultTabs())))
            .put("railLeft", store.railLeft)
            .put("theme", store.themeMode)
            .put("monoIcons", store.monoIcons)
            .put("iconMode", store.iconMode)
            .put("iconColors", store.iconColors)
            .put("voiceApps", store.voiceApps)
            .put("noticeOrder", store.noticeOrder)
            .put("noticeLeft", store.noticeLeft)
            .put("geminiModel", store.geminiModel)
            .put("favoriteApps", JSONArray(store.favoriteApps))
            .put("location", store.location)
            .put("categories", JSONArray(store.categories().map { JSONObject().put("id", it.id).put("name", it.name) }))
            .put("appCategories", JSONObject(store.appCategories()))
            .put("categoryPresets", JSONArray(store.categoryPresets))
            .put("categoryDefaults", JSONArray(store.categoryDefaults))
            .put("categoryAliases", JSONObject(store.categoryAliases))
            .put("bookmarks", JSONArray(store.bookmarks().map { JSONObject().put("id", it.id).put("name", it.name).put("url", it.url) }))
            .put("folderViews", JSONObject(store.folderViews()))
            .put("queries", JSONArray(store.queries().map { it.toJson() }))
            .put("appOrder", JSONObject(store.appOrder().mapValues { JSONArray(it.value) }))
            .put("tabOpts", JSONObject(store.prefs.getString("tabOpts", "{}") ?: "{}"))
            .put("nas", JSONObject().put("url", store.prefs.getString("nasUrl", "")).put("account", store.prefs.getString("nasAccount", "")))
            .put("apps", JSONArray(apps.map { JSONObject().put("package", it.packageName).put("component", it.key).put("label", it.label) }))
        val widgets = JSONObject()
        for (profile in listOf("narrow", "wide")) {
            widgets.put(profile, JSONArray(store.widgetTabs(profile).map { t ->
                JSONObject().put("label", t.label).put("w", t.w).put("h", t.h)
                    .put("provider", awm.getAppWidgetInfo(t.id)?.provider?.flattenToString() ?: "")
            }))
        }
        o.put("widgets", widgets)
        return o.toString(2)
    }

    /** 設定を上書きする。ウィジェットは今の端末のものを残す。 */
    fun import(context: Context, store: Store, text: String): ImportReport {
        val o = JSONObject(text)
        require(o.optString("format") == FORMAT) { "Theta Home Hatch の設定ファイルではありません" }
        val e = store.prefs.edit()
        o.optJSONArray("tabs")?.let { e.putString("tabs", it.toString()) }
        e.putBoolean("railLeft", o.optBoolean("railLeft", store.railLeft))
        e.putString("theme", o.optString("theme", store.themeMode))
        e.putBoolean("monoIcons", o.optBoolean("monoIcons", store.monoIcons))
        if (o.has("iconMode")) e.putString("iconMode", o.optString("iconMode"))
        if (o.has("iconColors")) e.putString("iconColors", o.optString("iconColors"))
        if (o.has("voiceApps")) e.putString("voiceApps", o.optString("voiceApps"))
        if (o.has("noticeOrder")) e.putString("noticeOrder", o.optString("noticeOrder"))
        if (o.has("noticeLeft")) e.putString("noticeLeft", o.optString("noticeLeft"))
        if (o.has("geminiModel")) e.putString("geminiModel", o.optString("geminiModel"))
        o.optJSONArray("favoriteApps")?.let { e.putString("favApps", it.toString()) }
        o.optJSONArray("categories")?.let { e.putString("categories", it.toString()) }
        o.optJSONObject("appCategories")?.let { e.putString("appCats", it.toString()) }
        o.optJSONArray("categoryPresets")?.let { e.putString("catPresets", it.toString()) }
        o.optJSONArray("categoryDefaults")?.let { e.putString("catDefaults", it.toString()) }
        o.optJSONObject("categoryAliases")?.let { e.putString("catAlias", it.toString()) }
        o.optJSONArray("bookmarks")?.let { e.putString("bookmarks", it.toString()) }
        o.optJSONObject("folderViews")?.let { e.putString("views", it.toString()) }
        o.optJSONObject("appOrder")?.let { e.putString("appOrder", it.toString()) }
        o.optJSONObject("tabOpts")?.let { e.putString("tabOpts", it.toString()) }
        o.optJSONArray("queries")?.let { e.putString("queries", it.toString()).putBoolean("queriesSeeded", true) }
        e.putString("location", o.optString("location", ""))
        val nas = o.optJSONObject("nas")
        val nasUrl = nas?.optString("url").orEmpty()
        if (nasUrl.isNotEmpty() && nasUrl != store.prefs.getString("nasUrl", "")) {
            e.putString("nasUrl", nasUrl).putString("nasAccount", nas?.optString("account").orEmpty()).remove("nasPassword")
        }
        e.apply()

        val hatch = HatchCatalog.apps.mapNotNull { it.packageName }.toSet()
        val missing = (o.optJSONArray("apps") ?: JSONArray()).let { a ->
            (0 until a.length()).map { a.getJSONObject(it) }
                .map { MissingApp(it.optString("package"), it.optString("label"), it.optString("package") in hatch) }
                .filter { it.packageName.isNotEmpty() && it.packageName != context.packageName && !Apps.isInstalled(context, it.packageName) }
                .distinctBy { it.packageName }
                .sortedWith(compareBy({ !it.hatch }, { it.label }))
        }
        val widgets = o.optJSONObject("widgets")?.let { w ->
            listOf("narrow", "wide").flatMap { p -> w.optJSONArray(p)?.let { a -> (0 until a.length()).map { a.getJSONObject(it).optString("label") } }.orEmpty() }
        }.orEmpty().distinct()
        val folders = store.tabs().filter { t ->
            t.kind == TabKind.FOLDER && t.isLocal && !Files.hasGrant(context, Uri.parse(t.folderUri))
        }.map { it.label }
        return ImportReport(missing, widgets, folders, nasUrl.isNotEmpty() && store.prefs.getString("nasPassword", "").isNullOrEmpty())
    }

    private fun tabsJson(tabs: List<TabSpec>): String = JSONArray(tabs.map {
        JSONObject().put("id", it.id).put("kind", it.kind.name).put("label", it.label).put("folder", it.folderUri ?: "")
    }).toString()

    fun playStoreUri(packageName: String): Uri = Uri.parse("market://details?id=$packageName")

    fun playStoreWebUri(packageName: String): Uri = Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
}
