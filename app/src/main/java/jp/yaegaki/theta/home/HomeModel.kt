package jp.yaegaki.theta.home

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import jp.yaegaki.theta.home.capture.Capture
import jp.yaegaki.theta.home.capture.CaptureKind
import jp.yaegaki.theta.home.data.AppEntry
import jp.yaegaki.theta.home.data.Apps
import jp.yaegaki.theta.home.data.Bookmark
import jp.yaegaki.theta.home.data.Calendar
import jp.yaegaki.theta.home.data.ClipItem
import jp.yaegaki.theta.home.data.Contacts
import jp.yaegaki.theta.home.data.Entry
import jp.yaegaki.theta.home.data.Event
import jp.yaegaki.theta.home.data.Files
import jp.yaegaki.theta.home.data.Person
import jp.yaegaki.theta.home.data.RecentFile
import jp.yaegaki.theta.home.data.Store
import jp.yaegaki.theta.home.data.TabKind
import jp.yaegaki.theta.home.data.TabSpec
import jp.yaegaki.theta.home.hatch.HatchApp
import jp.yaegaki.theta.home.hatch.HatchCatalog
import jp.yaegaki.theta.home.hatch.HatchInstaller
import jp.yaegaki.theta.home.hatch.Release
import jp.yaegaki.theta.home.nas.Nas
import jp.yaegaki.theta.home.data.WidgetTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** フォルダ・タブ 1 つ分の表示状態。stack の末尾が今いるフォルダ。 */
data class FolderState(
    val stack: List<Pair<Uri, String>> = emptyList(),
    val entries: List<Entry> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
) {
    val current: Uri? get() = stack.lastOrNull()?.first
    val path: String get() = stack.joinToString(" › ") { it.second }
}

data class NasState(
    val path: String = "",
    val entries: List<Nas.Item> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

data class HatchStatus(
    val installed: String? = null,
    val latest: Release? = null,
    val checking: Boolean = false,
    val progress: Float? = null,
    val error: String? = null,
)

class HomeModel(app: Application) : AndroidViewModel(app) {
    private val ctx: Context get() = getApplication()
    private val store: Store = (app as HomeApp).store

    var tabs by mutableStateOf(store.tabs()); private set
    var railLeft by mutableStateOf(store.railLeft); private set
    var gray by mutableStateOf(store.grayTheme); private set
    var monoIcons by mutableStateOf(store.monoIcons); private set

    /** 重ねて開いているドロワー（タブ id）。 */
    var open by mutableStateOf<String?>(null); private set
    /** 画面に並べて出しっぱなしにしているドロワー。 */
    var docked by mutableStateOf<List<String>>(emptyList()); private set
    var lastOpened by mutableStateOf<String?>(null); private set
    var settingsOpen by mutableStateOf(false)
    var query by mutableStateOf("")
    var focusSearch by mutableStateOf(0); private set

    var apps by mutableStateOf<List<AppEntry>>(emptyList()); private set
    var favoriteApps by mutableStateOf(store.favoriteApps); private set
    var people by mutableStateOf<List<Person>>(emptyList()); private set
    var events by mutableStateOf<List<Event>>(emptyList()); private set
    var recents by mutableStateOf(store.recents()); private set
    var bookmarks by mutableStateOf(store.bookmarks()); private set
    var clips by mutableStateOf(store.clips()); private set
    var captures by mutableStateOf<List<Pair<CaptureKind, File>>>(emptyList()); private set

    /** 背景のファイラーで見ている場所："tab:<id>"（端末のフォルダ）・"nas"・"recent"。 */
    var location by mutableStateOf(store.location.ifEmpty { tabs.firstOrNull { it.kind == TabKind.FOLDER }?.let { "tab:${it.id}" } ?: "recent" }); private set
    /** NAS を開いたフォルダのタブ（タブから開いたときだけ）。 */
    var nasTab by mutableStateOf<String?>(null); private set
    var nasState by mutableStateOf(NasState()); private set
    val nas = Nas(app, store.prefs)
    var status by mutableStateOf<Nas.Status?>(null); private set
    var checking by mutableStateOf(false); private set

    /** ウィジェットの設定は画面ごと："narrow"（幅 600dp 未満）・"wide"。 */
    var profile by mutableStateOf("narrow"); private set
    var widgetTabs by mutableStateOf(store.widgetTabs("narrow")); private set

    val folders = mutableStateMapOf<String, FolderState>()
    val hatch = mutableStateMapOf<String, HatchStatus>()

    init {
        refreshAll()
    }

    fun has(permission: String) = ctx.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    fun refreshAll() {
        refreshApps()
        refreshPeople()
        refreshEvents()
        refreshCaptures()
        refreshHatchInstalled()
        refreshStatus(force = true)
        val t = tab(location.removePrefix("tab:"))
        if (t != null && t.kind == TabKind.FOLDER) showFolder(t, closeDrawer = false)
    }

    fun refreshApps() = io { Apps.load(ctx) }.then { apps = it }

    fun refreshPeople() {
        if (!has(Manifest.permission.READ_CONTACTS)) return
        io { Contacts.load(ctx) }.then { people = it }
    }

    fun refreshEvents() {
        if (!has(Manifest.permission.READ_CALENDAR)) return
        io { Calendar.upcoming(ctx) }.then { events = it }
    }

    fun refreshCaptures() = io { Capture.all(ctx) }.then { captures = it }

    // ---- ドロワー ----

    fun tab(id: String?) = tabs.firstOrNull { it.id == id }

    fun widgetTab(id: String?) = widgetTabs.firstOrNull { "w:${it.id}" == id }

    fun toggle(id: String) {
        tab(id)?.takeIf { it.kind == TabKind.FOLDER }?.let { showFolder(it); return }
        if (id in docked) {
            docked = docked - id
            return
        }
        open = if (open == id) null else id
        if (open != null) {
            lastOpened = id
            onOpened(tab(id))
        }
    }

    fun close() {
        open = null
    }

    /** ピン留め：重ねていたドロワーを横に並べる。maxDocked を超えたら古い方から閉じる。 */
    fun pin(id: String, maxDocked: Int) {
        if (maxDocked <= 0) return
        docked = (docked - id + id).takeLast(maxDocked)
        if (open == id) open = null
    }

    fun unpin(id: String) {
        docked = docked - id
    }

    fun openLast() {
        val id = lastOpened ?: tabs.firstOrNull()?.id ?: return
        if (open != id && id !in docked) toggle(id)
    }

    fun openByKey(c: Char): Boolean {
        val upper = c.uppercaseChar()
        val t = if (upper in '1'..'9') {
            tabs.filter { it.kind == TabKind.FOLDER }.getOrNull(upper - '1')
        } else {
            tabs.firstOrNull { it.kind.key == upper }
        } ?: return false
        if (t.kind == TabKind.FOLDER) showFolder(t) else toggle(t.id)
        return true
    }

    fun onHome() {
        open = null
        settingsOpen = false
        query = ""
    }

    fun typeToSearch(c: Char) {
        query += c
        focusSearch++
    }

    private fun onOpened(t: TabSpec?) {
        when (t?.kind) {
            TabKind.CALENDAR -> refreshEvents()
            TabKind.PEOPLE -> refreshPeople()
            TabKind.CAPTURE -> refreshCaptures()
            TabKind.HATCH -> checkHatchUpdates()
            else -> Unit
        }
    }

    // ---- 背景のファイラー ----

    /** フォルダのタブ：背景のファイラーをそのフォルダに切り替える（ドロワーは閉じる）。 */
    fun showFolder(t: TabSpec, closeDrawer: Boolean = true) {
        if (closeDrawer) open = null
        val nasPath = t.nasPath
        if (nasPath != null) {
            openNas(nasPath, t.id)
            return
        }
        moveTo("tab:${t.id}")
        if (folders[t.id]?.stack.isNullOrEmpty() || folders[t.id]?.error != null) openRoot(t)
    }

    fun showRecent() {
        open = null
        moveTo("recent")
    }

    private fun moveTo(l: String) {
        location = l
        store.location = l
    }

    fun openNas(path: String = nasState.path, fromTab: String? = null) {
        open = null
        nasTab = fromTab
        moveTo("nas")
        loadNas(path)
    }

    fun loadNas(path: String) {
        nasState = NasState(path = path, entries = if (path == nasState.path) nasState.entries else emptyList(), loading = true)
        viewModelScope.launch {
            val r = runCatching { withContext(Dispatchers.IO) { nas.list(path) } }
            if (nasState.path != path) return@launch
            nasState = r.fold(
                { NasState(path, it) },
                { NasState(path, error = if (status?.nas != Nas.OK && status != null) status!!.message else "読み込めませんでした（${it.message}）") },
            )
            if (r.isFailure) refreshStatus(force = true)
        }
    }

    /** NAS の上のフォルダへ。共有フォルダの一覧より上は無い。 */
    fun nasUp(): Boolean {
        val p = nasState.path
        if (p.isEmpty()) return false
        val cut = p.lastIndexOf('/')
        loadNas(if (cut <= 0) "" else p.substring(0, cut))
        return true
    }

    /** NAS のファイルを端末（キャッシュ）に取得して渡す。 */
    fun fetchNas(item: Nas.Item, then: (File) -> Unit) {
        viewModelScope.launch {
            val r = runCatching {
                withContext(Dispatchers.IO) {
                    val dir = File(ctx.cacheDir, "nas").apply { mkdirs() }
                    // 取得したファイルは新しいものから 30 個だけ残す
                    dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(30)?.forEach { it.delete() }
                    File(dir, item.name.replace(Regex("[\\\\/:*?\"<>|]"), "_")).also { nas.download(item.path, it) }
                }
            }
            r.onSuccess(then).onFailure { (ctx as HomeApp).toast("取得できませんでした：${it.message}") }
        }
    }

    fun assignNasFolder(tabId: String, path: String) {
        updateTabs(tabs.map { if (it.id == tabId) it.copy(folderUri = TabSpec.NAS_PREFIX + path) else it })
    }

    /** ネットワーク・Tailscale・NAS の状態。force でなければ 20 秒以内の結果を使い回す。 */
    fun refreshStatus(force: Boolean = false) {
        val last = status
        if (checking || !force && last != null && System.currentTimeMillis() - last.checkedAt < 20_000) return
        checking = true
        viewModelScope.launch {
            status = withContext(Dispatchers.IO) { nas.check() }
            checking = false
        }
    }

    fun saveNas(url: String, account: String, password: String) {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { nas.save(url, account, password) } }
            refreshStatus(force = true)
            if (location == "nas") loadNas("")
        }
    }

    // ---- フォルダ ----

    fun openRoot(t: TabSpec) {
        val tree = t.folderUri?.let(Uri::parse) ?: run {
            folders[t.id] = FolderState()
            return
        }
        if (!Files.hasGrant(ctx, tree)) {
            folders[t.id] = FolderState(error = "フォルダへのアクセス許可がなくなりました。選び直してください。")
            return
        }
        load(t.id, listOf(Files.rootOf(tree) to t.label))
    }

    fun enter(tabId: String, e: Entry) {
        val s = folders[tabId] ?: return
        load(tabId, s.stack + (e.uri to e.name))
    }

    /** 上のフォルダへ。もう上がなければ false。 */
    fun up(tabId: String): Boolean {
        val s = folders[tabId] ?: return false
        if (s.stack.size <= 1) return false
        load(tabId, s.stack.dropLast(1))
        return true
    }

    fun jump(tabId: String, depth: Int) {
        val s = folders[tabId] ?: return
        if (depth < s.stack.size - 1) load(tabId, s.stack.take(depth + 1))
    }

    private fun load(tabId: String, stack: List<Pair<Uri, String>>) {
        folders[tabId] = FolderState(stack = stack, entries = folders[tabId]?.entries.orEmpty(), loading = true)
        viewModelScope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { Files.list(ctx, stack.last().first) } }
            if (folders[tabId]?.stack != stack) return@launch
            folders[tabId] = result.fold(
                { FolderState(stack = stack, entries = it) },
                { FolderState(stack = stack, error = "読み込めませんでした（${it.javaClass.simpleName}）") },
            )
        }
    }

    fun assignFolder(tabId: String, tree: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { ctx.contentResolver.takePersistableUriPermission(tree, flags) }
            .recoverCatching { ctx.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        updateTabs(tabs.map { if (it.id == tabId) it.copy(folderUri = tree.toString()) else it })
        folders.remove(tabId)
        tab(tabId)?.let(::openRoot)
    }

    fun rememberOpened(tabLabel: String, e: Entry) {
        val r = RecentFile(e.uri.toString(), e.name, e.mime, tabLabel, System.currentTimeMillis())
        recents = (listOf(r) + recents.filter { it.uri != r.uri }).take(12)
        store.saveRecents(recents)
    }

    // ---- 設定 ----

    fun updateTabs(list: List<TabSpec>) {
        tabs = list
        store.saveTabs(list)
        docked = docked.filter { id -> list.any { it.id == id } }
        if (list.none { it.id == open }) open = null
    }

    fun addTab(kind: TabKind) {
        val label = if (kind == TabKind.FOLDER) "フォルダ" else kind.defaultLabel
        updateTabs(tabs + TabSpec(Store.newId(), kind, label))
    }

    fun changeRailLeft(v: Boolean) { railLeft = v; store.railLeft = v }
    fun changeGray(v: Boolean) { gray = v; store.grayTheme = v }
    fun changeMonoIcons(v: Boolean) { monoIcons = v; store.monoIcons = v }

    fun toggleFavorite(app: AppEntry) {
        favoriteApps = if (app.key in favoriteApps) favoriteApps - app.key else (favoriteApps + app.key).take(8)
        store.favoriteApps = favoriteApps
    }

    // ---- 栞と Clip ----

    fun addBookmark(name: String, url: String) {
        bookmarks = bookmarks + Bookmark(Store.newId(), name.ifBlank { Uri.parse(url).host ?: url }, url)
        store.saveBookmarks(bookmarks)
    }

    fun removeBookmark(b: Bookmark) {
        bookmarks = bookmarks - b
        store.saveBookmarks(bookmarks)
    }

    fun reloadClips() {
        clips = store.clips()
    }

    fun removeClip(c: ClipItem) {
        c.fileName?.let { File(clipDir(ctx), it).delete() }
        clips = clips.filter { it.id != c.id }
        store.saveClips(clips)
    }

    // ---- Widget（1 タブ 1 ウィジェット、画面ごと） ----

    fun changeProfile(p: String) {
        if (p == profile) return
        profile = p
        widgetTabs = store.widgetTabs(p)
        if (open?.startsWith("w:") == true && widgetTab(open) == null) open = null
        docked = docked.filter { !it.startsWith("w:") || widgetTab(it) != null }
    }

    fun otherProfile() = if (profile == "wide") "narrow" else "wide"

    fun otherWidgetTabs() = store.widgetTabs(otherProfile())

    private fun saveWidgetTabs(list: List<WidgetTab>) {
        widgetTabs = list
        store.saveWidgetTabs(profile, list)
    }

    fun addWidgetTab(t: WidgetTab) {
        saveWidgetTabs(widgetTabs.filter { it.id != t.id } + t)
        open = "w:${t.id}"
        lastOpened = open
    }

    /** なぞって大きさを変えている途中の幅。ドロワーの幅もこれに合わせる。 */
    val liveWidth = mutableStateMapOf<Int, Int>()

    fun resizeLive(id: Int, w: Int) {
        liveWidth[id] = w
    }

    fun widgetWidth(t: WidgetTab) = liveWidth[t.id] ?: t.w

    fun updateWidgetTab(t: WidgetTab) {
        liveWidth.remove(t.id)
        saveWidgetTabs(widgetTabs.map { if (it.id == t.id) t else it })
    }

    fun moveWidgetTab(id: Int, delta: Int) {
        val i = widgetTabs.indexOfFirst { it.id == id }
        val j = i + delta
        if (i < 0 || j !in widgetTabs.indices) return
        saveWidgetTabs(widgetTabs.toMutableList().also { it.add(j, it.removeAt(i)) })
    }

    /** この画面のタブから外す。もう一方の画面でも使っていなければウィジェット自体を削除する。 */
    fun removeWidgetTab(id: Int) {
        saveWidgetTabs(widgetTabs.filter { it.id != id })
        if (open == "w:$id") open = null
        docked = docked - "w:$id"
        if (otherWidgetTabs().none { it.id == id }) (ctx as HomeApp).widgets.release(id)
    }

    /** もう一方の画面のウィジェットを取り込む。replace なら今の設定と置き換える。 */
    /** 割り当てが済んだウィジェットを、この画面のタブとして足す。大きさはウィジェットの最小サイズから。 */
    fun addWidget(id: Int) {
        val host = (ctx as HomeApp).widgets
        val info = host.info(id) ?: return
        val d = ctx.resources.displayMetrics.density
        addWidgetTab(WidgetTab(id, host.label(info), (info.minWidth / d).toInt().coerceIn(200, 600), (info.minHeight / d).toInt().coerceIn(120, 600)))
    }

    fun importWidgets(replace: Boolean) {
        val other = otherWidgetTabs()
        val before = widgetTabs
        val next = if (replace) other else before + other.filter { o -> before.none { it.id == o.id } }
        saveWidgetTabs(next)
        if (replace) before.filter { b -> next.none { it.id == b.id } }.forEach { (ctx as HomeApp).widgets.release(it.id) }
        if (open?.startsWith("w:") == true && widgetTab(open) == null) open = null
    }

    // ---- Hatch ----

    fun refreshHatchInstalled() {
        HatchCatalog.apps.forEach { a ->
            val v = a.packageName?.let { Apps.versionName(ctx, it) }
            hatch[a.id] = (hatch[a.id] ?: HatchStatus()).copy(installed = v)
        }
    }

    fun checkHatchUpdates() {
        refreshHatchInstalled()
        HatchCatalog.apps.filter { it.repo != null && it.packageName != null }.forEach { a ->
            if (hatch[a.id]?.checking == true || hatch[a.id]?.latest != null) return@forEach
            hatch[a.id] = hatch[a.id]!!.copy(checking = true, error = null)
            viewModelScope.launch {
                val r = runCatching { HatchInstaller.latest(a) }.getOrNull()
                hatch[a.id] = hatch[a.id]!!.copy(checking = false, latest = r)
            }
        }
    }

    /** 取得とハッシュ照合までをここで行い、インストール確認画面は onReady で開く。 */
    fun downloadHatch(a: HatchApp, onReady: (File) -> Unit) {
        val r = hatch[a.id]?.latest ?: return
        hatch[a.id] = hatch[a.id]!!.copy(progress = 0f, error = null)
        viewModelScope.launch {
            var shown = 0
            val result = runCatching {
                HatchInstaller.download(ctx, r) { p ->
                    // 1% 進むごとに表示を更新する
                    val pct = (p * 100).toInt()
                    if (pct > shown) {
                        shown = pct
                        viewModelScope.launch { hatch[a.id] = hatch[a.id]!!.copy(progress = p) }
                    }
                }
            }
            hatch[a.id] = hatch[a.id]!!.copy(progress = null, error = result.exceptionOrNull()?.message)
            result.getOrNull()?.let(onReady)
        }
    }

    // ---- 補助 ----

    private fun <T> io(block: suspend () -> T) = block

    private fun <T> (suspend () -> T).then(apply: (T) -> Unit) {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { this@then() } }.onSuccess(apply)
        }
    }

    companion object {
        fun clipDir(context: Context): File = File(context.filesDir, "Clip").apply { mkdirs() }

        /** 共有メニューから受け取ったものを Clip に入れる。ファイルはアプリ内にコピーする。 */
        fun addToClip(context: Context, text: String?, streams: List<Uri>) {
            val store = (context.applicationContext as HomeApp).store
            val now = System.currentTimeMillis()
            val added = ArrayList<ClipItem>()
            text?.trim()?.takeIf { it.isNotEmpty() }?.let { added += ClipItem(Store.newId(), it, null, null, now) }
            for (uri in streams) {
                val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "shared_$now"
                val safe = name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                val target = File(clipDir(context), "${Store.newId()}_$safe")
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } }
                }
                if (target.exists() && target.length() > 0) {
                    added += ClipItem(Store.newId(), null, target.name, context.contentResolver.getType(uri) ?: Files.mimeOf(safe), now)
                }
            }
            if (added.isNotEmpty()) store.saveClips((added + store.clips()).take(100))
        }
    }
}
