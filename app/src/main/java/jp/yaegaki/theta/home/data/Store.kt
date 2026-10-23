package jp.yaegaki.theta.home.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** タブの種類。group は端のタブの色分け（0=フォルダ, 1=ウィジェット, 2=起動）。key は物理キーボードの Alt＋キー。 */
enum class TabKind(val group: Int, val defaultLabel: String, val key: Char?) {
    FOLDER(0, "フォルダ", null),
    CALENDAR(1, "予定", 'C'),
    MAIL(1, "メール", 'M'),
    LINE(1, "LINE", 'L'),
    PEOPLE(1, "人", 'P'),
    WIDGETS(1, "Widget", 'W'),
    APPS(2, "アプリ", 'A'),
    BOOKMARKS(2, "栞", 'B'),
    CLIP(2, "Clip", 'X'),
    CAPTURE(2, "記録", 'R'),
    HATCH(2, "Hatch", 'H'),
}

data class TabSpec(val id: String, val kind: TabKind, val label: String, val folderUri: String? = null)

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
    private val prefs = context.getSharedPreferences("home", Context.MODE_PRIVATE)

    fun tabs(): List<TabSpec> {
        val raw = prefs.getString("tabs", null) ?: return defaultTabs()
        return runCatching {
            val a = JSONArray(raw)
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                TabSpec(
                    id = o.getString("id"),
                    kind = TabKind.valueOf(o.getString("kind")),
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

    var grayTheme: Boolean
        get() = prefs.getBoolean("gray", false)
        set(v) = prefs.edit().putBoolean("gray", v).apply()

    var monoIcons: Boolean
        get() = prefs.getBoolean("monoIcons", true)
        set(v) = prefs.edit().putBoolean("monoIcons", v).apply()

    var favoriteApps: List<String>
        get() = strings("favApps")
        set(v) = putStrings("favApps", v)

    var widgetIds: List<Int>
        get() = strings("widgets").mapNotNull { it.toIntOrNull() }
        set(v) = putStrings("widgets", v.map { it.toString() })

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
            TabSpec("widgets", TabKind.WIDGETS, "Widget"),
            TabSpec("apps", TabKind.APPS, "アプリ"),
            TabSpec("marks", TabKind.BOOKMARKS, "栞"),
            TabSpec("clip", TabKind.CLIP, "Clip"),
            TabSpec("capture", TabKind.CAPTURE, "記録"),
            TabSpec("hatch", TabKind.HATCH, "Hatch"),
        )
    }
}
