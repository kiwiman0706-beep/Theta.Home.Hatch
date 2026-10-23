package jp.yaegaki.theta.home.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * タブの種類。group は端のタブの色分け（0=フォルダ, 1=ウィジェット, 2=起動）。key は物理キーボードの Alt＋キー。
 * フォルダのタブは背景のファイラーの場所を切り替え、それ以外はドロワーとしてスライドインする。
 * ウィジェットはタブ 1 つに 1 つ（[WidgetTab]）で、画面ごとに別に持つ。
 */
enum class TabKind(val group: Int, val defaultLabel: String, val key: Char?) {
    FOLDER(0, "フォルダ", null),
    CALENDAR(1, "予定", 'C'),
    MAIL(1, "メール", 'M'),
    LINE(1, "LINE", 'L'),
    PEOPLE(1, "人", 'P'),
    APPS(2, "アプリ", 'A'),
    BOOKMARKS(2, "栞", 'B'),
    CLIP(2, "Clip", 'X'),
    CAPTURE(2, "記録", 'R'),
    HATCH(2, "Hatch", 'H'),
}

/** folderUri は端末のフォルダ（SAF のツリー URI）か、NAS のフォルダ（"nas:" ＋ パス）。 */
data class TabSpec(val id: String, val kind: TabKind, val label: String, val folderUri: String? = null) {
    val nasPath: String? get() = folderUri?.takeIf { it.startsWith(NAS_PREFIX) }?.removePrefix(NAS_PREFIX)

    companion object {
        const val NAS_PREFIX = "nas:"
    }
}

/** アプリのカテゴリー（仕分け先）。 */
data class Category(val id: String, val name: String)

/** ウィジェット 1 つ分のタブ。w・h は dp。 */
data class WidgetTab(val id: Int, val label: String, val w: Int, val h: Int)

data class RecentFile(val uri: String, val name: String, val mime: String?, val where: String, val time: Long)

data class Bookmark(val id: String, val name: String, val url: String)

data class ClipItem(
    val id: String,
    val text: String?,
    val fileName: String?,
    val mime: String?,
    val time: Long,
) {
    val isFile get() = fileName != null
    val isUrl get() = text != null && (text.startsWith("http://") || text.startsWith("https://"))
}

/** 設定と小さな一覧を SharedPreferences に JSON で保存する。端末固有の情報だけを持つ。 */
class Store(context: Context) {
    val prefs: SharedPreferences = context.getSharedPreferences("home", Context.MODE_PRIVATE)

    fun tabs(): List<TabSpec> {
        val raw = prefs.getString("tabs", null) ?: return defaultTabs()
        return runCatching {
            val a = JSONArray(raw)
            // 0.2.0 の「Widget」タブ（複数を縦に並べる）のような、今は無い種類は読み飛ばす
            (0 until a.length()).mapNotNull { i ->
                val o = a.getJSONObject(i)
                val kind = runCatching { TabKind.valueOf(o.getString("kind")) }.getOrNull() ?: return@mapNotNull null
                TabSpec(
                    id = o.getString("id"),
                    kind = kind,
                    label = o.getString("label"),
                    folderUri = o.optString("folder").ifEmpty { null },
                )
            }
        }.getOrElse { defaultTabs() }
    }

    fun saveTabs(tabs: List<TabSpec>) {
        val a = JSONArray()
        tabs.forEach {
            a.put(JSONObject().put("id", it.id).put("kind", it.kind.name).put("label", it.label).put("folder", it.folderUri ?: ""))
        }
        prefs.edit().putString("tabs", a.toString()).apply()
    }

    var railLeft: Boolean
        get() = prefs.getBoolean("railLeft", false)
        set(v) = prefs.edit().putBoolean("railLeft", v).apply()

    /** "dark"・"light"・"system"（端末に合わせる）。0.4.0 までの「グレーの配色」はライトとして読む。 */
    var themeMode: String
        get() = prefs.getString("theme", null) ?: if (prefs.getBoolean("gray", false)) "light" else "dark"
        set(v) = prefs.edit().putString("theme", v).apply()

    fun categories(): List<Category> = objects("categories") { Category(it.getString("id"), it.getString("name")) }

    fun saveCategories(list: List<Category>) = putObjects("categories", list) { JSONObject().put("id", it.id).put("name", it.name) }

    /** アプリ（ComponentName の文字列）→ カテゴリーの id。 */
    fun appCategories(): Map<String, String> = runCatching {
        val o = JSONObject(prefs.getString("appCats", "{}") ?: "{}")
        o.keys().asSequence().associateWith { o.getString(it) }
    }.getOrDefault(emptyMap())

    fun saveAppCategories(map: Map<String, String>) {
        prefs.edit().putString("appCats", JSONObject(map).toString()).apply()
    }

    var monoIcons: Boolean
        get() = prefs.getBoolean("monoIcons", true)
        set(v) = prefs.edit().putBoolean("monoIcons", v).apply()

    var favoriteApps: List<String>
        get() = strings("favApps")
        set(v) = putStrings("favApps", v)

    /** 背景のファイラーで最後に見ていた場所（"tab:<id>"・"nas"・"recent"）。 */
    var location: String
        get() = prefs.getString("location", "") ?: ""
        set(v) = prefs.edit().putString("location", v).apply()

    /** profile は "narrow"（Fold5 カバー・Titan など幅 600dp 未満）か "wide"。 */
    fun widgetTabs(profile: String): List<WidgetTab> {
        migrateWidgets()
        return objects("wtabs_$profile") { WidgetTab(it.getInt("id"), it.getString("label"), it.getInt("w"), it.getInt("h")) }
    }

    fun saveWidgetTabs(profile: String, list: List<WidgetTab>) = putObjects("wtabs_$profile", list) {
        JSONObject().put("id", it.id).put("label", it.label).put("w", it.w).put("h", it.h)
    }

    /** 0.2.0 の「Widget」タブにあったウィジェットを、両方の画面のタブへ移す。 */
    private fun migrateWidgets() {
        val old = strings("widgets").mapNotNull { it.toIntOrNull() }
        if (old.isEmpty() || prefs.contains("wtabs_narrow")) return
        val list = old.map { WidgetTab(it, "Widget", 320, 200) }
        saveWidgetTabs("narrow", list)
        saveWidgetTabs("wide", list)
        prefs.edit().remove("widgets").apply()
    }

    var pendingWidget: Int
        get() = prefs.getInt("pendingWidget", -1)
        set(v) = prefs.edit().putInt("pendingWidget", v).apply()

    fun recents(): List<RecentFile> = objects("recents") {
        RecentFile(it.getString("uri"), it.getString("name"), it.optString("mime").ifEmpty { null }, it.getString("where"), it.getLong("time"))
    }

    fun saveRecents(list: List<RecentFile>) = putObjects("recents", list) {
        JSONObject().put("uri", it.uri).put("name", it.name).put("mime", it.mime ?: "").put("where", it.where).put("time", it.time)
    }

    fun bookmarks(): List<Bookmark> = objects("bookmarks") { Bookmark(it.getString("id"), it.getString("name"), it.getString("url")) }

    fun saveBookmarks(list: List<Bookmark>) = putObjects("bookmarks", list) {
        JSONObject().put("id", it.id).put("name", it.name).put("url", it.url)
    }

    fun clips(): List<ClipItem> = objects("clips") {
        ClipItem(
            id = it.getString("id"),
            text = if (it.has("text")) it.getString("text") else null,
            fileName = if (it.has("file")) it.getString("file") else null,
            mime = if (it.has("mime")) it.getString("mime") else null,
            time = it.getLong("time"),
        )
    }

    fun saveClips(list: List<ClipItem>) = putObjects("clips", list) {
        JSONObject().put("id", it.id).put("time", it.time).apply {
            it.text?.let { t -> put("text", t) }
            it.fileName?.let { f -> put("file", f) }
            it.mime?.let { m -> put("mime", m) }
        }
    }

    private fun strings(key: String): List<String> = runCatching {
        val a = JSONArray(prefs.getString(key, "[]"))
        (0 until a.length()).map { a.getString(it) }
    }.getOrDefault(emptyList())

    private fun putStrings(key: String, v: List<String>) {
        prefs.edit().putString(key, JSONArray(v).toString()).apply()
    }

    private fun <T> objects(key: String, read: (JSONObject) -> T): List<T> = runCatching {
        val a = JSONArray(prefs.getString(key, "[]"))
        (0 until a.length()).mapNotNull { runCatching { read(a.getJSONObject(it)) }.getOrNull() }
    }.getOrDefault(emptyList())

    private fun <T> putObjects(key: String, list: List<T>, write: (T) -> JSONObject) {
        val a = JSONArray()
        list.forEach { a.put(write(it)) }
        prefs.edit().putString(key, a.toString()).apply()
    }

    companion object {
        fun newId(): String = UUID.randomUUID().toString().take(8)

        fun defaultTabs(): List<TabSpec> = listOf(
            TabSpec("work", TabKind.FOLDER, "仕事"),
            TabSpec("personal", TabKind.FOLDER, "個人"),
            TabSpec("photo", TabKind.FOLDER, "写真"),
            TabSpec("cal", TabKind.CALENDAR, "予定"),
            TabSpec("mail", TabKind.MAIL, "メール"),
            TabSpec("line", TabKind.LINE, "LINE"),
            TabSpec("people", TabKind.PEOPLE, "人"),
            TabSpec("apps", TabKind.APPS, "アプリ"),
            TabSpec("marks", TabKind.BOOKMARKS, "栞"),
            TabSpec("clip", TabKind.CLIP, "Clip"),
            TabSpec("capture", TabKind.CAPTURE, "記録"),
            TabSpec("hatch", TabKind.HATCH, "Hatch"),
        )
    }
}
