package jp.yaegaki.theta.home.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * タブの種類。group は端のタブの色分け（0=フォルダ, 1=ウィジェット, 2=起動）。key は物理キーボードの Alt＋キー。
 * どのタブもドロワーとしてスライドインする（フォルダのタブはドロワーの中にフォルダの中身）。
 * ウィジェットは [WidgetTab] で持ち、並び・中身は両方の画面で共通、大きさだけ画面ごと。
 */
enum class TabKind(val group: Int, val defaultLabel: String, val key: Char?) {
    FOLDER(0, "FOLDER", null),
    CALENDAR(1, "CALENDAR", 'C'),
    MAIL(1, "MAIL", 'M'),
    LINE(1, "LINE", 'L'),
    PEOPLE(1, "PEOPLE", 'P'),
    APPS(2, "APPS", 'A'),
    BOOKMARKS(2, "LINKS", 'B'),
    CLIP(2, "CLIP", 'X'),
    CAPTURE(2, "CAPTURE", 'R'),
    HATCH(2, "HATCH", 'H'),
    /** アプリのカテゴリー 1 つ分のアプリ。TabSpec.folderUri にカテゴリーの id を入れる。 */
    CATEGORY(2, "CATEGORY", null),
    /** 決めた URL のページをタブの中に出す。TabSpec.folderUri に URL を入れる。 */
    WEB(1, "WEB", null),
    /** 決めたファイル（画像・PDF）をタブの中に出す。TabSpec.folderUri に content:// の URI を入れる。 */
    FILE(1, "FILE", null),
    /** ふつうのタブ：ウィジェット・フォルダ・ファイル・ページを好きな数だけ縦に並べる（[BoardItem]）。 */
    BOARD(1, "BOARD", null),
}

/** folderUri は端末のフォルダ（SAF のツリー URI）か、NAS のフォルダ（"nas:" ＋ パス）。 */
data class TabSpec(val id: String, val kind: TabKind, val label: String, val folderUri: String? = null) {
    val nasPath: String? get() = folderUri?.takeIf { it.startsWith(NAS_PREFIX) }?.removePrefix(NAS_PREFIX)

    /** Google ドライブのフォルダ（ID と名前）。 */
    val driveFolder: Pair<String, String>? get() = folderUri?.takeIf { it.startsWith(DRIVE_PREFIX) }?.removePrefix(DRIVE_PREFIX)?.let {
        val cut = it.indexOf(':')
        if (cut < 0) it to it else it.substring(0, cut) to it.substring(cut + 1)
    }

    /** 端末のフォルダ（SAF）か。 */
    val isLocal: Boolean get() = folderUri != null && nasPath == null && driveFolder == null

    companion object {
        const val NAS_PREFIX = "nas:"
        const val DRIVE_PREFIX = "gdrive:"
    }
}

/** アプリのカテゴリー（仕分け先）。 */
data class Category(val id: String, val name: String)

/** タブの中のウィジェット 1 つ。w・h は dp。 */
data class WidgetCell(val id: Int, val w: Int, val h: Int)

/**
 * ウィジェットのタブ。1 つ目のウィジェットの id がタブの id（"w:<id>"）。2 つ目からは extra に縦に並べる。
 * w・h は 1 つ目の大きさ（dp）。
 */
data class WidgetTab(val id: Int, val label: String, val w: Int, val h: Int, val extra: List<WidgetCell> = emptyList()) {
    val cells: List<WidgetCell> get() = listOf(WidgetCell(id, w, h)) + extra

    /** cells から作り直す（1 つ目が消えたときは 2 つ目がタブの id になる）。 */
    fun withCells(list: List<WidgetCell>): WidgetTab? = list.firstOrNull()?.let { f -> copy(id = f.id, w = f.w, h = f.h, extra = list.drop(1)) }
}

data class RecentFile(val uri: String, val name: String, val mime: String?, val where: String, val time: Long)

data class Bookmark(val id: String, val name: String, val url: String)

/**
 * ふつうのタブ（BOARD）に置くもの 1 つ。type は WIDGET（ref＝ウィジェットの id）・FOLDER（ref＝フォルダのタブと同じ書き方）・
 * FILE（ref＝content:// の URI）・WEB（ref＝URL）。half なら幅半分（となりの半分と横に並ぶ）。h は画面ごとの高さ（dp）。
 */
data class BoardItem(
    val id: String,
    val type: String,
    val ref: String = "",
    val label: String = "",
    val half: Boolean = false,
    val h: Map<String, Int> = emptyMap(),
    /** 縦いっぱい（残りの高さ）。カレンダー・フォルダなど 1 つだけ置くタブ向け。 */
    val fill: Boolean = false,
) {
    fun height(profile: String) = h[profile] ?: h.values.firstOrNull() ?: defaultHeight(type)

    /** ショートカットキー・初めの名前・色分けに使う、元のタブの種類。 */
    val kind: TabKind? get() = PANELS[type] ?: when (type) {
        FOLDER -> TabKind.FOLDER
        FILE -> TabKind.FILE
        WEB -> TabKind.WEB
        else -> null
    }

    fun toJson(): JSONObject = JSONObject().put("id", id).put("type", type).put("ref", ref).put("label", label)
        .put("half", half).put("fill", fill).put("h", JSONObject(h.mapValues { it.value }))

    companion object {
        const val WIDGET = "widget"
        const val FOLDER = "folder"
        const val FILE = "file"
        const val WEB = "web"

        /** 今までの専用のタブの中身。置くとそのドロワーと同じものが出る。 */
        val PANELS = mapOf(
            "calendar" to TabKind.CALENDAR, "mail" to TabKind.MAIL, "line" to TabKind.LINE, "people" to TabKind.PEOPLE,
            "links" to TabKind.BOOKMARKS, "clip" to TabKind.CLIP, "capture" to TabKind.CAPTURE, "hatch" to TabKind.HATCH,
        )

        /** 元のタブの種類から、置くものの種類へ。 */
        fun typeOf(kind: TabKind): String? = when (kind) {
            TabKind.FOLDER -> FOLDER
            TabKind.FILE -> FILE
            TabKind.WEB -> WEB
            else -> PANELS.entries.firstOrNull { it.value == kind }?.key
        }

        fun defaultHeight(type: String) = when (type) {
            WIDGET -> 200
            "calendar" -> 480
            FILE -> 280
            WEB -> 420
            else -> 320
        }

        fun from(o: JSONObject) = BoardItem(
            id = o.getString("id"),
            type = o.getString("type"),
            ref = o.optString("ref"),
            label = o.optString("label"),
            half = o.optBoolean("half", false),
            h = o.optJSONObject("h")?.let { m -> m.keys().asSequence().associateWith { m.getInt(it) } }.orEmpty(),
            fill = o.optBoolean("fill", false),
        )
    }
}

/**
 * タブごとの設定。
 * show："both"（両方の画面）・"narrow"（閉じた画面だけ）・"wide"（開いた画面だけ）。
 * width：画面ごとのドロワーの幅（dp。無ければ決まった幅）。apps：タブに置いたアプリ（AppEntry.key）。
 * split：カテゴリーのタブで、ショートカットをアプリと別の段にする。
 * icon：端のタブの印。""（中身から自動）・"none"（出さない）・"g:<記号の名前>"・"app:<AppEntry.key>"。
 */
data class TabOpts(
    val show: String = "both",
    val width: Map<String, Int> = emptyMap(),
    val apps: List<String> = emptyList(),
    val split: Boolean = false,
    val icon: String = "",
) {
    fun shownOn(profile: String) = show == "both" || show == profile

    fun toJson(): JSONObject = JSONObject().put("show", show).put("split", split).put("icon", icon)
        .put("apps", JSONArray(apps)).put("width", JSONObject(width.mapValues { it.value }))

    companion object {
        fun from(o: JSONObject) = TabOpts(
            show = o.optString("show", "both"),
            width = o.optJSONObject("width")?.let { w -> w.keys().asSequence().associateWith { w.getInt(it) } }.orEmpty(),
            apps = o.optJSONArray("apps")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty(),
            split = o.optBoolean("split", false),
            icon = o.optString("icon", ""),
        )
    }
}

/** フォルダの見せ方：一覧（LIST）かグリッド（GRID）か、大きさ 0〜3（S・M・L・XL）。 */
data class ViewSpec(val grid: Boolean, val size: Int) {
    fun encode() = (if (grid) "grid" else "list") + ":" + size

    companion object {
        const val SIZES = 4
        val NAMES = listOf("S", "M", "L", "XL")
        val LIST = ViewSpec(false, 1)
        val GRID = ViewSpec(true, 1)

        fun decode(s: String?): ViewSpec? {
            val parts = s?.split(':') ?: return null
            if (parts.size != 2 || parts[0] !in setOf("grid", "list")) return null
            return ViewSpec(parts[0] == "grid", (parts[1].toIntOrNull() ?: 1).coerceIn(0, SIZES - 1))
        }
    }
}

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
        val raw = prefs.getString("tabs", null) ?: return migratePanels(migrateBoards(defaultTabs()))
        val list = runCatching {
            val a = JSONArray(raw)
            // 0.2.0 の「Widget」タブ（複数を縦に並べる）のような、今は無い種類は読み飛ばす
            (0 until a.length()).mapNotNull { i ->
                val o = a.getJSONObject(i)
                val kind = runCatching { TabKind.valueOf(o.getString("kind")) }.getOrNull() ?: return@mapNotNull null
                val label = o.getString("label")
                TabSpec(
                    id = o.getString("id"),
                    kind = kind,
                    label = OLD_LABELS[label] ?: label,
                    folderUri = o.optString("folder").ifEmpty { null },
                )
            }
        }.getOrElse { defaultTabs() }
        // 0.10.0 から、アプリの一覧はタブではなく画面上部のボタンで開く（タブは「＋」で戻せる）
        if (!prefs.getBoolean("appsTabMoved", false)) {
            prefs.edit().putBoolean("appsTabMoved", true).apply()
            if (list.any { it.kind == TabKind.APPS }) return migratePanels(migrateBoards(list.filter { it.kind != TabKind.APPS }.also(::saveTabs)))
        }
        return migratePanels(migrateBoards(list))
    }

    /**
     * 0.21.0 までの専用のタブ（フォルダ・予定・メール・LINE・人・栞・Clip・記録・Hatch・WEB・FILE）を、
     * 中身が 1 つ（縦いっぱい）のふつうのタブに移す。タブの id はそのまま（置いたアプリ・ショートカット・設定が残る）。
     * 置いたものの id は "m-<タブの id>"。背景のファイラーがそのフォルダを見ていたら、置いたものの id に付け替える。
     */
    private fun migratePanels(list: List<TabSpec>): List<TabSpec> {
        val old = list.filter { BoardItem.typeOf(it.kind) != null }
        if (old.isEmpty()) return list
        val boards = boards().toMutableMap()
        old.forEach { t ->
            val item = BoardItem("m-" + t.id, BoardItem.typeOf(t.kind)!!, t.folderUri.orEmpty(), t.label, fill = true)
            boards[t.id] = listOf(item) + boards[t.id].orEmpty().filter { it.id != item.id }
        }
        saveBoards(boards)
        old.firstOrNull { "tab:" + it.id == location }?.let { location = "tab:m-" + it.id }
        return list.map { t -> if (t in old) TabSpec(t.id, TabKind.BOARD, t.label) else t }.also(::saveTabs)
    }

    /**
     * 0.20.0 までのウィジェットのタブを、ふつうのタブ（BOARD）に移す。タブは今のタブの後ろに足し、
     * ウィジェットの高さ・ドロワーの幅は画面ごとのものを、置いたショートカットとタブの設定も引き継ぐ。
     */
    private fun migrateBoards(list: List<TabSpec>): List<TabSpec> {
        if (prefs.getBoolean("boardsMigrated", false)) return list
        val narrow = widgetTabs("narrow")
        val wide = widgetTabs("wide")
        val boards = boards().toMutableMap()
        val opts = tabOpts().toMutableMap()
        var scs = shortcuts()
        val added = narrow.map { t ->
            val id = "b" + newId()
            val wt = wide.firstOrNull { it.id == t.id } ?: t
            boards[id] = t.cells.map { c ->
                val cw = wt.cells.firstOrNull { it.id == c.id } ?: c
                BoardItem(newId(), BoardItem.WIDGET, c.id.toString(), h = mapOf("narrow" to c.h, "wide" to cw.h))
            }
            val o = opts.remove("w:${t.id}") ?: TabOpts()
            opts[id] = o.copy(width = mapOf("narrow" to t.cells.maxOf { it.w } + 16, "wide" to wt.cells.maxOf { it.w } + 16) + o.width)
            scs = scs.map { if (it.catId == "tab:w:${t.id}") it.copy(catId = "tab:$id") else it }
            TabSpec(id, TabKind.BOARD, t.label)
        }
        saveBoards(boards)
        saveTabOpts(opts)
        saveShortcuts(scs)
        prefs.edit().putString("wtabs", "[]").putBoolean("boardsMigrated", true).apply()
        if (added.isEmpty()) return list
        return (list + added).also(::saveTabs)
    }

    /** ふつうのタブの中身。キーはタブの id。 */
    fun boards(): Map<String, List<BoardItem>> = runCatching {
        val o = JSONObject(prefs.getString("boards", "{}") ?: "{}")
        o.keys().asSequence().associateWith { k -> o.getJSONArray(k).let { a -> (0 until a.length()).map { BoardItem.from(a.getJSONObject(it)) } } }
    }.getOrDefault(emptyMap())

    fun saveBoards(map: Map<String, List<BoardItem>>) {
        val o = JSONObject()
        map.forEach { (k, v) -> o.put(k, JSONArray(v.map { it.toJson() })) }
        prefs.edit().putString("boards", o.toString()).apply()
    }

    /** ウィジェットを足している途中のタブ（設定画面から戻ったときに使う）。"" なら新しいタブ。 */
    var pendingBoard: String
        get() = prefs.getString("pendingBoard", "") ?: ""
        set(v) = prefs.edit().putString("pendingBoard", v).apply()

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

    /** 仕分けガイドの候補（規定のカテゴリー）。名前の変更・削除・追加ができる。未設定なら初期の候補。 */
    var categoryPresets: List<String>
        get() = if (prefs.contains("catPresets")) strings("catPresets") else Categories.presets
        set(v) = putStrings("catPresets", v)

    /** 候補のうち、ガイドで初めから選んでおくもの。 */
    var categoryDefaults: List<String>
        get() = if (prefs.contains("catDefaults")) strings("catDefaults") else Categories.defaults
        set(v) = putStrings("catDefaults", v)

    /** 初期の候補名 → 変えた後の名前。おすすめ（初期の候補名で出る）を今の名前に読み替える。 */
    var categoryAliases: Map<String, String>
        get() = runCatching {
            val o = JSONObject(prefs.getString("catAlias", "{}") ?: "{}")
            o.keys().asSequence().associateWith { o.getString(it) }
        }.getOrDefault(emptyMap())
        set(v) = prefs.edit().putString("catAlias", JSONObject(v).toString()).apply()

    fun resetCategoryPresets() {
        prefs.edit().remove("catPresets").remove("catDefaults").remove("catAlias").apply()
    }

    /** アプリ（ComponentName の文字列）→ カテゴリーの id。 */
    fun appCategories(): Map<String, String> = runCatching {
        val o = JSONObject(prefs.getString("appCats", "{}") ?: "{}")
        o.keys().asSequence().associateWith { o.getString(it) }
    }.getOrDefault(emptyMap())

    fun saveAppCategories(map: Map<String, String>) {
        prefs.edit().putString("appCats", JSONObject(map).toString()).apply()
    }

    /** アプリのアイコンの見せ方："original"・"mono"・"local"（端末内でツートーン）・"gemini"。0.6.0 までのモノクロ設定から引き継ぐ。 */
    var iconMode: String
        get() = prefs.getString("iconMode", null) ?: if (prefs.getBoolean("monoIcons", true)) "mono" else "original"
        set(v) = prefs.edit().putString("iconMode", v).apply()

    var geminiModel: String
        get() = prefs.getString("geminiModel", null) ?: IconStyle.DEFAULT_MODEL
        set(v) = prefs.edit().putString("geminiModel", v.trim().ifEmpty { IconStyle.DEFAULT_MODEL }).apply()

    /** Gemini の API キー。端末の鍵で暗号化して保存し、書き出し（バックアップ）には含めない。 */
    var geminiKey: String
        get() = jp.yaegaki.theta.home.nas.Secret.open(prefs.getString("geminiKey", ""))
        set(v) = prefs.edit().putString("geminiKey", if (v.isBlank()) "" else jp.yaegaki.theta.home.nas.Secret.seal(v.trim())).apply()

    /** 起動・復帰のときに更新を確かめる（1 日 1 回まで）。 */
    var autoUpdate: Boolean
        get() = prefs.getBoolean("autoUpdate", true)
        set(v) = prefs.edit().putBoolean("autoUpdate", v).apply()

    var lastUpdateCheck: Long
        get() = prefs.getLong("lastUpdateCheck", 0)
        set(v) = prefs.edit().putLong("lastUpdateCheck", v).apply()

    var monoIcons: Boolean
        get() = prefs.getBoolean("monoIcons", true)
        set(v) = prefs.edit().putBoolean("monoIcons", v).apply()

    /**
     * ピン留め（端の下・すべてのアプリの上）。キーはアプリ（AppEntry.key）・"pkg:<パッケージ>"（そのアプリ）・
     * "sc:<ショートカットの id>"・"act:<機能>"（Google の音声検索・MemoHatch の音声メモ）。
     * 一度も決めていなければ、LINE・Google の音声検索・MemoHatch の音声メモ（入っているものだけ出る）。
     */
    var favoriteApps: List<String>
        get() = if (prefs.contains("favApps")) strings("favApps") else DEFAULT_PINS
        set(v) = putStrings("favApps", v)

    /** 背景のファイラーで最後に見ていた場所（"tab:<id>"・"nas"・"recent"）。 */
    var location: String
        get() = prefs.getString("location", "") ?: ""
        set(v) = prefs.edit().putString("location", v).apply()

    /**
     * 0.20.0 までのウィジェットのタブ（ふつうのタブへ移すときに読む）。並び・中身は両方の画面で共通、大きさだけ画面ごと（"wsize_<profile>"）。
     * profile は "narrow"（Fold5 カバー・Titan など幅 600dp 未満）か "wide"。
     */
    fun widgetTabs(profile: String): List<WidgetTab> {
        migrateWidgets()
        mergeWidgetProfiles()
        val sizes = widgetSizes(profile)
        fun sized(c: WidgetCell) = sizes[c.id]?.let { (w, h) -> c.copy(w = w, h = h) } ?: c
        return readWidgetTabs("wtabs").mapNotNull { t -> t.withCells(t.cells.map(::sized))?.copy(label = t.label) }
    }

    private fun readWidgetTabs(key: String): List<WidgetTab> = objects(key) {
        val extra = it.optJSONArray("extra")?.let { a -> (0 until a.length()).map { i -> a.getJSONObject(i).let { c -> WidgetCell(c.getInt("id"), c.getInt("w"), c.getInt("h")) } } }.orEmpty()
        WidgetTab(it.getInt("id"), it.getString("label"), it.getInt("w"), it.getInt("h"), extra)
    }

    private fun writeWidgetTabs(key: String, list: List<WidgetTab>) = putObjects(key, list) {
        JSONObject().put("id", it.id).put("label", it.label).put("w", it.w).put("h", it.h)
            .put("extra", JSONArray(it.extra.map { c -> JSONObject().put("id", c.id).put("w", c.w).put("h", c.h) }))
    }

    private fun widgetSizes(profile: String): Map<Int, Pair<Int, Int>> = runCatching {
        val o = JSONObject(prefs.getString("wsize_$profile", "{}") ?: "{}")
        o.keys().asSequence().associate { k -> o.getJSONArray(k).let { a -> k.toInt() to (a.getInt(0) to a.getInt(1)) } }
    }.getOrDefault(emptyMap())

    private fun saveWidgetSizes(profile: String, map: Map<Int, Pair<Int, Int>>) {
        val o = JSONObject()
        map.forEach { (id, wh) -> o.put(id.toString(), JSONArray(listOf(wh.first, wh.second))) }
        prefs.edit().putString("wsize_$profile", o.toString()).apply()
    }

    /**
     * 0.19.0 までの、画面ごとに別だったウィジェットのタブを 1 つにまとめる。閉じた画面の並びを先に、開いた画面にだけあったものを後ろに。
     * 同じウィジェットが両方にあれば 1 つにし、大きさはそれぞれの画面のものを残す。
     */
    private fun mergeWidgetProfiles() {
        if (prefs.contains("wtabs")) return
        val narrow = readWidgetTabs("wtabs_narrow")
        val wide = readWidgetTabs("wtabs_wide")
        val seen = mutableSetOf<Int>()
        val merged = (narrow + wide).mapNotNull { t ->
            val cells = t.cells.filter { seen.add(it.id) }
            t.withCells(cells)?.copy(label = t.label)
        }
        writeWidgetTabs("wtabs", merged)
        saveWidgetSizes("narrow", narrow.flatMap { it.cells }.associate { it.id to (it.w to it.h) })
        saveWidgetSizes("wide", wide.flatMap { it.cells }.associate { it.id to (it.w to it.h) })
    }

    /** タブごとの設定（キーはタブの id。ウィジェットのタブは "w:<id>"）。 */
    fun tabOpts(): Map<String, TabOpts> = runCatching {
        val o = JSONObject(prefs.getString("tabOpts", "{}") ?: "{}")
        o.keys().asSequence().associateWith { k -> TabOpts.from(o.getJSONObject(k)) }
    }.getOrDefault(emptyMap())

    fun saveTabOpts(map: Map<String, TabOpts>) {
        val o = JSONObject()
        map.filterValues { it != TabOpts() }.forEach { (k, v) -> o.put(k, v.toJson()) }
        prefs.edit().putString("tabOpts", o.toString()).apply()
    }

    /** 0.2.0 の「Widget」タブにあったウィジェットを、両方の画面のタブへ移す。 */
    private fun migrateWidgets() {
        val old = strings("widgets").mapNotNull { it.toIntOrNull() }
        if (old.isEmpty() || prefs.contains("wtabs_narrow")) return
        val list = old.map { WidgetTab(it, "Widget", 320, 200) }
        writeWidgetTabs("wtabs_narrow", list)
        writeWidgetTabs("wtabs_wide", list)
        prefs.edit().remove("widgets").apply()
    }

    var pendingWidget: Int
        get() = prefs.getInt("pendingWidget", -1)
        set(v) = prefs.edit().putInt("pendingWidget", v).apply()

    /** フォルダごとの見せ方。キーは "doc:<uri>"・"nas:<path>"・"recent"、値は ViewSpec.encode()。 */
    fun folderViews(): Map<String, String> = runCatching {
        val o = JSONObject(prefs.getString("views", "{}") ?: "{}")
        o.keys().asSequence().associateWith { o.getString(it) }
    }.getOrDefault(emptyMap())

    fun saveFolderViews(map: Map<String, String>) = prefs.edit().putString("views", JSONObject(map).toString()).apply()

    /** 保存した検索（仮想フォルダ）。初回だけドライブの候補を置く。 */
    fun queries(): List<SavedQuery> {
        if (!prefs.getBoolean("queriesSeeded", false)) {
            saveQueries(SavedQuery.defaults())
            prefs.edit().putBoolean("queriesSeeded", true).apply()
        }
        return objects("queries") { SavedQuery.fromJson(it) }
    }

    fun saveQueries(list: List<SavedQuery>) = putObjects("queries", list) { it.toJson() }

    var weatherPlace: WeatherPlace?
        get() = runCatching {
            val o = JSONObject(prefs.getString("weatherPlace", null) ?: return null)
            WeatherPlace(o.getString("name"), o.getDouble("lat"), o.getDouble("lon"), o.optBoolean("loc"))
        }.getOrNull()
        set(v) = prefs.edit().putString("weatherPlace", v?.let { JSONObject().put("name", it.name).put("lat", it.lat).put("lon", it.lon).put("loc", it.useLocation).toString() }).apply()

    /** 最後に取った予報（JSON のまま）と取った時刻。起動直後でもすぐ出せるように。 */
    var weatherCache: String?
        get() = prefs.getString("weatherCache", null)
        set(v) = prefs.edit().putString("weatherCache", v).putLong("weatherAt", System.currentTimeMillis()).apply()

    val weatherAt: Long
        get() = prefs.getLong("weatherAt", 0)

    /** 出さないカレンダーの id。where は "tab"（カレンダーのタブ）か "home"（今日の予定）。 */
    fun hiddenCalendars(where: String): Set<Long> = strings("calHidden_$where").mapNotNull { it.toLongOrNull() }.toSet()

    fun saveHiddenCalendars(where: String, ids: Set<Long>) = putStrings("calHidden_$where", ids.map { it.toString() })

    /** アイコンの 3 色："hud"・"orange"・"solid"・"mono"、または "custom:地,基本,アクセント"（ARGB の 16 進）。 */
    var iconColors: String
        get() = prefs.getString("iconColors", "hud") ?: "hud"
        set(v) = prefs.edit().putString("iconColors", v).apply()

    /** アプリの一覧を開いたときの音声起動："off"・"tap"（押したとき）・"long"（長押ししたとき）。 */
    var voiceApps: String
        get() = prefs.getString("voiceApps", "long") ?: "long"
        set(v) = prefs.edit().putString("voiceApps", v).apply()

    /** カテゴリーのタブの通知の並び："time"（時系列）・"app"（アプリ別）。 */
    /** 通知を左にスワイプしたとき："reply"（返信）・"snooze"（1 時間後にまた）・"dismiss"（消す）・"open"（アプリで開く）。 */
    var noticeLeft: String
        get() = prefs.getString("noticeLeft", "reply") ?: "reply"
        set(v) = prefs.edit().putString("noticeLeft", v).apply()

    var noticeOrder: String
        get() = prefs.getString("noticeOrder", "time") ?: "time"
        set(v) = prefs.edit().putString("noticeOrder", v).apply()

    /** カレンダーのドロワーの表示："month"・"week"・"day"。 */
    var calendarMode: String
        get() = prefs.getString("calMode", "month") ?: "month"
        set(v) = prefs.edit().putString("calMode", v).apply()

    /** エッジ：どのアプリの上でも、画面の端からタブを引き出す。side は "left"・"right"、pos は取っ手の上端（画面の高さの %）。 */
    /** ファイルをホームの中（ポップアップ）で開く。オフなら今までどおりほかのアプリで。 */
    var inAppViewer: Boolean
        get() = prefs.getBoolean("inAppViewer", true)
        set(v) = prefs.edit().putBoolean("inAppViewer", v).apply()

    var edgeOn: Boolean
        get() = prefs.getBoolean("edgeOn", false)
        set(v) = prefs.edit().putBoolean("edgeOn", v).apply()

    var edgeSide: String
        get() = prefs.getString("edgeSide", "left") ?: "left"
        set(v) = prefs.edit().putString("edgeSide", v).apply()

    var edgePos: Int
        get() = prefs.getInt("edgePos", 35)
        set(v) = prefs.edit().putInt("edgePos", v).apply()

    fun appOrder(): Map<String, List<String>> = runCatching {
        val o = JSONObject(prefs.getString("appOrder", "{}") ?: "{}")
        o.keys().asSequence().associateWith { k -> o.getJSONArray(k).let { a -> (0 until a.length()).map { a.getString(it) } } }
    }.getOrDefault(emptyMap())

    fun saveAppOrder(map: Map<String, List<String>>) =
        prefs.edit().putString("appOrder", JSONObject(map.mapValues { JSONArray(it.value) }).toString()).apply()

    fun shortcuts(): List<PinnedShortcut> = objects("shortcuts") {
        PinnedShortcut(it.getString("id"), it.getString("pkg"), it.getString("sid"), it.getString("label"), it.optString("cat"))
    }

    fun saveShortcuts(list: List<PinnedShortcut>) = putObjects("shortcuts", list) {
        JSONObject().put("id", it.id).put("pkg", it.packageName).put("sid", it.shortcutId).put("label", it.label).put("cat", it.catId)
    }

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

        val DEFAULT_PINS = listOf("pkg:jp.naver.line.android", "act:voice", "act:memo-audio")

        /** 0.5.x までの日本語の初期名。名前を変えていなければ英語の初期名に置き換える。 */
        private val OLD_LABELS = mapOf(
            "仕事" to "WORK", "個人" to "PRIVATE", "写真" to "PHOTO", "予定" to "CALENDAR", "メール" to "MAIL",
            "人" to "PEOPLE", "アプリ" to "APPS", "栞" to "LINKS", "Clip" to "CLIP", "記録" to "CAPTURE", "Hatch" to "HATCH",
            "Widget" to "WIDGET", "フォルダ" to "FOLDER",
        )

        fun defaultTabs(): List<TabSpec> = listOf(
            TabSpec("work", TabKind.FOLDER, "WORK"),
            TabSpec("personal", TabKind.FOLDER, "PRIVATE"),
            TabSpec("photo", TabKind.FOLDER, "PHOTO"),
            TabSpec("cal", TabKind.CALENDAR, "CALENDAR"),
            TabSpec("mail", TabKind.MAIL, "MAIL"),
            TabSpec("line", TabKind.LINE, "LINE"),
            TabSpec("people", TabKind.PEOPLE, "PEOPLE"),
            TabSpec("marks", TabKind.BOOKMARKS, "LINKS"),
            TabSpec("clip", TabKind.CLIP, "CLIP"),
            TabSpec("capture", TabKind.CAPTURE, "CAPTURE"),
            TabSpec("hatch", TabKind.HATCH, "HATCH"),
        )
    }
}
