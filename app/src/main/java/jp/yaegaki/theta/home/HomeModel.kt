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
import androidx.compose.ui.graphics.luminance
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
import jp.yaegaki.theta.home.data.ViewSpec
import jp.yaegaki.theta.home.data.SavedQuery
import jp.yaegaki.theta.home.data.Forecast
import jp.yaegaki.theta.home.data.Weather
import jp.yaegaki.theta.home.data.WeatherPlace
import jp.yaegaki.theta.home.hatch.HatchApp
import jp.yaegaki.theta.home.hatch.HatchCatalog
import jp.yaegaki.theta.home.hatch.HatchInstaller
import jp.yaegaki.theta.home.hatch.Release
import jp.yaegaki.theta.home.nas.Nas
import jp.yaegaki.theta.home.drive.GDrive
import android.app.PendingIntent
import jp.yaegaki.theta.home.data.BoardItem
import jp.yaegaki.theta.home.data.Category
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

/** Google ドライブで見ている場所。stack の末尾が今いるフォルダ（ID と名前）。 */
data class DriveState(
    val stack: List<Pair<String, String>> = emptyList(),
    val entries: List<GDrive.Item> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

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
    /** ふつうのタブの中身（タブを読んだあと：古いタブを移すときに書き換わるので）。 */
    var boards by mutableStateOf(store.boards()); private set
    var tabOpts by mutableStateOf(store.tabOpts()); private set
    var railLeft by mutableStateOf(store.railLeft); private set
    /** "dark"・"light"・"system"。 */
    var themeMode by mutableStateOf(store.themeMode); private set
    var monoIcons by mutableStateOf(store.monoIcons); private set

    /** 重ねて開いているドロワー（タブ id）。 */
    var open by mutableStateOf<String?>(null); private set
    /** 画面に並べて出しっぱなしにしているドロワー。 */
    var docked by mutableStateOf<List<String>>(emptyList()); private set
    var lastOpened by mutableStateOf<String?>(null); private set
    /** セーフモード（レスキュー画面から）：ウィジェット・天気を読み込まない。 */
    var safeMode by mutableStateOf(false)

    var iconColors by mutableStateOf(store.iconColors); private set
    fun changeIconColors(v: String) { iconColors = v; store.iconColors = v }

    var voiceApps by mutableStateOf(store.voiceApps); private set
    fun changeVoiceApps(v: String) { voiceApps = v; store.voiceApps = v }

    // ---- エッジ（どのアプリの上でも画面の端からタブを引き出す） ----
    var edgeOn by mutableStateOf(store.edgeOn); private set
    var edgeSide by mutableStateOf(store.edgeSide); private set
    var edgePos by mutableStateOf(store.edgePos); private set
    /** 「ほかのアプリの上に表示」を許可しているか。設定から戻ったときに見直す。 */
    var edgeAllowed by mutableStateOf(android.provider.Settings.canDrawOverlays(ctx)); private set

    fun changeEdge(on: Boolean) { edgeOn = on; store.edgeOn = on; syncEdge() }
    fun changeEdgeSide(v: String) { edgeSide = v; store.edgeSide = v; syncEdge() }
    fun changeEdgePos(v: Int) { edgePos = v; store.edgePos = v; syncEdge() }

    /** アプリの一覧の「タブを出す」「すぐメモ」（モードとルーチンなどから開く入口）を出しているか。 */
    private fun aliasOn(name: String) =
        ctx.packageManager.getComponentEnabledSetting(android.content.ComponentName(ctx, "jp.yaegaki.theta.home.$name")) !=
            android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED

    private fun setAlias(name: String, v: Boolean) = ctx.packageManager.setComponentEnabledSetting(
        android.content.ComponentName(ctx, "jp.yaegaki.theta.home.$name"),
        if (v) android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DEFAULT else android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
        android.content.pm.PackageManager.DONT_KILL_APP,
    )

    var showTabsIcon by mutableStateOf(aliasOn("ShowTabs")); private set
    fun changeShowTabsIcon(v: Boolean) { showTabsIcon = v; setAlias("ShowTabs", v) }

    var showQuickIcon by mutableStateOf(aliasOn("QuickInput")); private set
    fun changeShowQuickIcon(v: Boolean) { showQuickIcon = v; setAlias("QuickInput", v) }

    fun syncEdge() {
        edgeAllowed = android.provider.Settings.canDrawOverlays(ctx)
        edgeOn = store.edgeOn
        jp.yaegaki.theta.home.edge.EdgeService.sync(ctx)
    }

    // ---- 中で開く（ポップアップのビューア） ----
    var inAppViewer by mutableStateOf(store.inAppViewer); private set
    fun changeInAppViewer(v: Boolean) { inAppViewer = v; store.inAppViewer = v }

    /** 開いているファイルの並びと、初めに出すもの。null なら閉じている。 */
    var viewer by mutableStateOf<Pair<List<jp.yaegaki.theta.home.data.ViewItem>, Int>?>(null); private set
    fun openViewer(items: List<jp.yaegaki.theta.home.data.ViewItem>, index: Int) { viewer = items to index }
    fun closeViewer() { viewer = null }

    var noticeLeft by mutableStateOf(store.noticeLeft); private set
    fun changeNoticeLeft(v: String) { noticeLeft = v; store.noticeLeft = v }

    var noticeOrder by mutableStateOf(store.noticeOrder); private set
    fun changeNoticeOrder(v: String) { noticeOrder = v; store.noticeOrder = v }

    /** アプリの呼び名（声で開く・検索）。 */
    var nicknames by mutableStateOf(store.nicknames); private set

    fun nicknamesOf(app: AppEntry): List<String> = nicknames[app.key].orEmpty()

    /** 呼び名を決める。「、」「,」・改行で区切っていくつでも。空なら消す。 */
    fun setNicknames(app: AppEntry, text: String) {
        val list = text.split(',', '、', '，', '\n').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        nicknames = (nicknames - app.key).let { if (list.isEmpty()) it else it + (app.key to list) }
        store.nicknames = nicknames
    }

    /** アプリの名前と呼び名。 */
    fun namesOf(app: AppEntry): List<String> = listOf(app.label) + nicknamesOf(app)

    /** 検索の言葉がアプリの名前か呼び名に合うか。 */
    fun appMatches(app: AppEntry, q: String): Boolean =
        q.isBlank() || app.label.contains(q, ignoreCase = true) || nicknamesOf(app).any { jp.yaegaki.theta.home.data.VoiceMatch.contains(it, q) }

    /** アプリの一覧を開いたら、すぐ音声で聞き取る（一覧の側が始めたら false に戻す）。 */
    var voicePending by mutableStateOf(false)

    /** アプリの一覧を開く。voice なら同時に聞き取りを始める。 */
    fun openApps(voice: Boolean) {
        val id = appsId()
        if (open != id) toggle(id)
        if (voice) voicePending = true
    }

    /** START・アプリのボタンを押した（long は長押し）。設定に合わせて、音声も一緒に始める。 */
    fun pressApps(long: Boolean) {
        val voice = when (voiceApps) {
            "tap" -> !long
            "long" -> long
            else -> false
        }
        if (!voice && !long && open == appsId()) {
            close()
            return
        }
        openApps(voice)
    }

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
    var location by mutableStateOf(store.location.ifEmpty { "recent" }); private set
    /** NAS を開いたフォルダのタブ（タブから開いたときだけ）。 */
    var nasTab by mutableStateOf<String?>(null); private set
    /** NAS の場所ごとの中身。キーは DESK（背景のファイラー）か、フォルダのタブの id（ドロワー）。 */
    val nasStates = androidx.compose.runtime.mutableStateMapOf<String, NasState>()
    val nasState: NasState get() = nasStates[DESK] ?: NasState()
    fun nasState(key: String) = nasStates[key] ?: NasState()
    val nas = Nas(app, store.prefs)
    var status by mutableStateOf<Nas.Status?>(null); private set
    val drive = GDrive(app, store.prefs)
    /** ドライブの場所ごとの中身。キーは NAS と同じ。 */
    val driveStates = androidx.compose.runtime.mutableStateMapOf<String, DriveState>()
    val driveState: DriveState get() = driveStates[DESK] ?: DriveState()
    fun driveState(key: String) = driveStates[key] ?: DriveState()
    /** Google ドライブを開いたフォルダのタブ（タブから開いたときだけ）。 */
    var driveTab by mutableStateOf<String?>(null); private set
    var driveEmail by mutableStateOf(drive.email); private set
    var driveOn by mutableStateOf(drive.enabled); private set
    /** 出してほしい Google の許可の画面。画面側が開いて null に戻す。 */
    var driveConsent by mutableStateOf<PendingIntent?>(null)
    var checking by mutableStateOf(false); private set

    /** ウィジェットの設定は画面ごと："narrow"（幅 600dp 未満）・"wide"。 */
    var profile by mutableStateOf("narrow"); private set

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
        place(location.removePrefix("tab:"))?.let { showFolder(it, closeDrawer = false) }
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

    // ---- 今日の予定とカレンダー ----

    /** 今日の予定（終わったものも含む。終日が先）。 */
    val todayEvents: List<Event>
        get() {
            val from = Calendar.startOfDay()
            val to = Calendar.addDays(from, 1)
            return events.filter { it.overlaps(from, to) && it.calendarId !in hiddenHome }.sortedWith(compareBy({ !it.allDay }, { it.localBegin }))
        }

    /** ホームの「通知」の区画の条件。 */
    var noticeRules by mutableStateOf(store.noticeRules); private set
    fun saveNoticeRule(r: jp.yaegaki.theta.home.notify.NoticeRule) {
        noticeRules = if (noticeRules.any { it.id == r.id }) noticeRules.map { if (it.id == r.id) r else it } else noticeRules + r
        store.noticeRules = noticeRules
    }
    fun removeNoticeRule(id: String) { noticeRules = noticeRules.filterNot { it.id == id }; store.noticeRules = noticeRules }

    // ---- Web 検索 ----

    /**
     * Google で探す。q が空なら Google の検索画面（voice なら音声検索）を開く。
     * Google アプリが無ければブラウザで google.com を開く。探したら箱は空にする。
     */
    fun webSearch(q: String, voice: Boolean = false, start: (Intent) -> Unit) {
        val text = q.trim()
        val google = apps.any { it.packageName == GOOGLE }
        val intent = when {
            voice -> Intent(android.speech.RecognizerIntent.ACTION_WEB_SEARCH).apply { if (google) setPackage(GOOGLE) }
            google -> Intent(Intent.ACTION_WEB_SEARCH).setPackage(GOOGLE).putExtra(android.app.SearchManager.QUERY, text)
            else -> Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/" + if (text.isEmpty()) "" else "search?q=" + Uri.encode(text)))
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        start(intent)
        query = ""
    }

    // ---- 初めの案内 ----

    /** 新しく入れたときだけ出す（更新して入れ直したときは出さない）。 */
    var welcome by mutableStateOf(!store.welcomed && freshInstall()); private set
    fun finishWelcome() { welcome = false; store.welcomed = true }

    private fun freshInstall(): Boolean = runCatching {
        val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        info.firstInstallTime == info.lastUpdateTime
    }.getOrDefault(false)

    // ---- ホームの区画 ----

    var homeSections by mutableStateOf(store.homeSections); private set
    var homeTwoColumns by mutableStateOf(store.homeTwoColumns); private set
    fun changeHomeTwoColumns(v: Boolean) { homeTwoColumns = v; store.homeTwoColumns = v }

    private fun saveSections(list: List<jp.yaegaki.theta.home.data.HomeSection>) { homeSections = list; store.homeSections = list }

    fun updateSection(s: jp.yaegaki.theta.home.data.HomeSection) = saveSections(homeSections.map { if (it.id == s.id) s else it })

    fun moveSection(id: String, delta: Int) {
        val i = homeSections.indexOfFirst { it.id == id }
        val j = i + delta
        if (i < 0 || j !in homeSections.indices) return
        saveSections(homeSections.toMutableList().also { it.add(j, it.removeAt(i)) })
    }

    fun resetSections() = saveSections(jp.yaegaki.theta.home.data.HomeSection.DEFAULTS)

    /** この画面で出す区画。 */
    fun sectionsHere(): List<jp.yaegaki.theta.home.data.HomeSection> = homeSections.filter { it.shownOn(profile) }

    /** ファイラーが区画としてホームに出ているか（出ていなければ、場所を押したときに全面で開く）。 */
    fun filerOnHome() = sectionsHere().any { it.id == jp.yaegaki.theta.home.data.HomeSection.FILER }

    // ---- 中で開く：ページのめくり方・本は DSBooks で ----

    var pdfPaged by mutableStateOf(store.pdfPaged); private set
    fun changePdfPaged(v: Boolean) { pdfPaged = v; store.pdfPaged = v }
    var pagesRtl by mutableStateOf(store.pagesRtl); private set
    fun changePagesRtl(v: Boolean) { pagesRtl = v; store.pagesRtl = v }

    fun dsbooksInstalled() = apps.any { it.packageName == DSBOOKS }

    /** DSBooks で開く（取り込んでから開く。0.8.7 から）。開けなければ false。 */
    fun openInDsbooks(uri: Uri, name: String, start: (Intent) -> Unit): Boolean {
        val mime = jp.yaegaki.theta.home.data.Viewable.bookMime(name) ?: return false
        val i = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).setPackage(DSBOOKS)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        if (i.resolveActivity(ctx.packageManager) == null) return false
        start(i)
        return true
    }

    /** 試験で、見本の予定を入れる。 */
    @androidx.annotation.VisibleForTesting
    internal fun setEventsForTest(list: List<Event>) { events = list }

    /** ホームの予定に出す日数（1・3・7）。予定は 7 日分読んでいる。 */
    var homeDays by mutableStateOf(store.homeDays); private set
    fun changeHomeDays(v: Int) { homeDays = v; store.homeDays = v }

    /** ホームの予定：今日から homeDays 日分を、日ごとに（予定の無い日は出さない。今日だけはいつも出す）。 */
    val homeEvents: List<Pair<Long, List<Event>>>
        get() {
            val today = Calendar.startOfDay()
            return (0 until homeDays.coerceIn(1, 7)).mapNotNull { i ->
                val from = Calendar.addDays(today, i)
                val to = Calendar.addDays(from, 1)
                val list = events.filter { it.overlaps(from, to) && it.calendarId !in hiddenHome }.sortedWith(compareBy({ !it.allDay }, { it.localBegin }))
                if (list.isEmpty() && i > 0) null else from to list
            }
        }

    /** 次の予定（まだ終わっていないもの。終日は今日のものだけ）。たたんだときの 1 行に使う。 */
    val nextEvent: Event?
        get() {
            val now = System.currentTimeMillis()
            return homeEvents.flatMap { it.second }.firstOrNull { if (it.allDay) it.overlaps(Calendar.startOfDay(), Calendar.addDays(Calendar.startOfDay(), 1)) else it.end > now }
        }

    /** たたんだホームの区画。 */
    var collapsedPanels by mutableStateOf(store.collapsedPanels); private set
    fun isCollapsed(code: String) = code in collapsedPanels
    fun toggleCollapsed(code: String) {
        collapsedPanels = if (code in collapsedPanels) collapsedPanels - code else collapsedPanels + code
        store.collapsedPanels = collapsedPanels
    }

    /** 出さないカレンダー：カレンダーのタブ（tab）と今日の予定（home）で別々。 */
    var hiddenTab by mutableStateOf(store.hiddenCalendars("tab")); private set
    var hiddenHome by mutableStateOf(store.hiddenCalendars("home")); private set
    var calendarSources by mutableStateOf<List<jp.yaegaki.theta.home.data.CalendarSource>>(emptyList()); private set

    fun loadCalendarSources() {
        if (!has(Manifest.permission.READ_CALENDAR)) return
        io { Calendar.sources(ctx) }.then { calendarSources = it }
    }

    fun setCalendarShown(where: String, id: Long, shown: Boolean) {
        if (where == "tab") {
            hiddenTab = if (shown) hiddenTab - id else hiddenTab + id
            store.saveHiddenCalendars("tab", hiddenTab)
        } else {
            hiddenHome = if (shown) hiddenHome - id else hiddenHome + id
            store.saveHiddenCalendars("home", hiddenHome)
        }
    }

    /** カレンダーのタブに出す予定（出さないカレンダーを除く）。 */
    val shownCalendarEvents: List<Event> get() = calendarEvents.filter { it.calendarId !in hiddenTab }

    var calendarMode by mutableStateOf(store.calendarMode); private set
    fun changeCalendarMode(m: String) { calendarMode = m; store.calendarMode = m }

    /** カレンダーのドロワーで見ている期間の予定。 */
    var calendarEvents by mutableStateOf<List<Event>>(emptyList()); private set
    private var calendarRange: Pair<Long, Long>? = null

    fun loadCalendar(from: Long, to: Long, force: Boolean = false) {
        if (!has(Manifest.permission.READ_CALENDAR)) return
        if (!force && calendarRange == (from to to)) return
        calendarRange = from to to
        io { Calendar.range(ctx, from, to) }.then { if (calendarRange == (from to to)) calendarEvents = it }
    }

    // ---- 天気（狭い画面のダッシュボード） ----

    var weatherPlace by mutableStateOf(store.weatherPlace); private set
    var forecast by mutableStateOf(store.weatherCache?.let { runCatching { Weather.parse(it, store.weatherAt) }.getOrNull() }); private set
    var weatherError by mutableStateOf<String?>(null); private set
    var weatherLoading by mutableStateOf(false); private set

    /** 30 分以内に取ったものがあれば取り直さない。 */
    fun refreshWeather(force: Boolean = false) {
        if (safeMode) return
        val place = weatherPlace ?: return
        val f = forecast
        if (weatherLoading || !force && f != null && System.currentTimeMillis() - f.fetchedAt < 30 * 60_000) {
            // 天気は取り直さなくても、警報・注意報は 10 分ごとに
            if (!weatherLoading) refreshAlerts()
            return
        }
        if (force) alerts = null
        weatherLoading = true
        viewModelScope.launch {
            val r = runCatching {
                withContext(Dispatchers.IO) {
                    val p = if (place.useLocation) locate()?.let { (la, lo) -> place.copy(lat = la, lon = lo) } ?: place else place
                    if (p != place) { weatherPlace = p; store.weatherPlace = p }
                    Weather.fetch(p)
                }
            }
            r.onSuccess { (fc, text) -> forecast = fc; weatherError = null; store.weatherCache = text }
                .onFailure { weatherError = "天気を取れませんでした（${it.message ?: it.javaClass.simpleName}）" }
            weatherLoading = false
            refreshAlerts()
        }
    }

    /** 気象庁の警報・注意報（日本の中だけ）。取れなければ null のまま（天気の欄には出さない）。 */
    var alerts by mutableStateOf<jp.yaegaki.theta.home.data.AlertState?>(null); internal set

    /** 天気と一緒に取る。10 分以内に取ったものがあれば取り直さない。 */
    private fun refreshAlerts() {
        val place = weatherPlace ?: return
        val a = alerts
        if (a != null && System.currentTimeMillis() - a.fetchedAt < 10 * 60_000) return
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { jp.yaegaki.theta.home.data.Alerts.fetch(ctx, place.lat, place.lon) } }
                .onSuccess { alerts = it }
        }
    }

    fun chooseWeatherPlace(p: WeatherPlace) {
        alerts = null
        weatherPlace = p
        store.weatherPlace = p
        forecast = null
        store.weatherCache = null
        refreshWeather(force = true)
    }

    /** 端末のおおよその位置（ネットワーク）。許可が無い・取れないときは null。 */
    @android.annotation.SuppressLint("MissingPermission")
    private suspend fun locate(): Pair<Double, Double>? {
        if (!has(Manifest.permission.ACCESS_COARSE_LOCATION)) return null
        val lm = ctx.getSystemService(android.location.LocationManager::class.java) ?: return null
        val provider = listOf(android.location.LocationManager.NETWORK_PROVIDER, android.location.LocationManager.GPS_PROVIDER, android.location.LocationManager.PASSIVE_PROVIDER)
            .firstOrNull { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) } ?: return null
        val loc = if (android.os.Build.VERSION.SDK_INT >= 30) {
            kotlinx.coroutines.withTimeoutOrNull(8000) {
                kotlinx.coroutines.suspendCancellableCoroutine<android.location.Location?> { cont ->
                    val cancel = android.os.CancellationSignal()
                    cont.invokeOnCancellation { cancel.cancel() }
                    lm.getCurrentLocation(provider, cancel, ctx.mainExecutor) { cont.resumeWith(Result.success(it)) }
                }
            }
        } else null
        val l = loc ?: runCatching { lm.getLastKnownLocation(provider) }.getOrNull() ?: return null
        return l.latitude to l.longitude
    }

    // ---- 狭い画面：背景はダッシュボード、ファイラーは開いたときだけ ----

    /** 狭い画面でファイラーを出しているか（広い画面では常に背景がファイラー）。 */
    var filerOpen by mutableStateOf(false)

    // ---- ドロワー ----

    /** アプリの一覧は、タブが無くても画面上部のボタン（Alt＋A）から開ける。 */
    fun tab(id: String?) = tabs.firstOrNull { it.id == id } ?: if (id == APPS_ID) TabSpec(APPS_ID, TabKind.APPS, "APPS") else null

    /** アプリの一覧を開く ID（APPS のタブがあればそれ）。 */
    fun appsId(): String = tabs.firstOrNull { it.kind == TabKind.APPS }?.id ?: APPS_ID


    fun toggle(id: String) {
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

    /** タブを開く（開いていれば何もしない）。並べて出しているタブはそのまま。 */
    fun openTab(id: String) {
        if (id in docked || open == id) return
        toggle(id)
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
        // 数字は n 番目のフォルダがあるタブ、文字はその中身（予定・メールなど）があるタブ
        val t = if (upper in '1'..'9') {
            folderTabs().getOrNull(upper - '1')
        } else {
            tabs.firstOrNull { keyOf(it) == upper }
        } ?: if (upper == 'A') tab(APPS_ID)!! else return false
        toggle(t.id)
        return true
    }

    fun onHome() {
        viewer = null
        quickOpen = null
        open = null
        settingsOpen = false
        query = ""
    }

    fun typeToSearch(c: Char) {
        query += c
        focusSearch++
    }

    /** 開いたタブの中身を読み直す（予定・連絡先・記録・Hatch の版）。 */
    private fun onOpened(t: TabSpec?) {
        t ?: return
        boardItems(t.id).forEach { item ->
            when (item.kind) {
                TabKind.FOLDER -> openFolderDrawer(specOf(item))
                TabKind.CALENDAR -> refreshEvents()
                TabKind.PEOPLE -> refreshPeople()
                TabKind.CAPTURE -> refreshCaptures()
                TabKind.HATCH -> checkHatchUpdates()
                else -> Unit
            }
        }
    }

    // ---- タブの中身から決まること（色・ショートカットキー・フォルダの場所） ----

    /** タブの中身でいちばん上のものの種類（色分け・ショートカット・見出し）。 */
    fun mainKind(t: TabSpec): TabKind = if (t.kind != TabKind.BOARD) t.kind else boardItems(t.id).firstNotNullOfOrNull { it.kind } ?: TabKind.BOARD

    /** 端のタブの色分け：0＝フォルダ、1＝ウィジェット・予定など、2＝起動。 */
    fun groupOf(t: TabSpec): Int = mainKind(t).group

    /** Alt＋文字（予定は C など）。 */
    fun keyOf(t: TabSpec): Char? = mainKind(t).key

    /** 端のタブの印を変える（"" で中身から自動に戻す）。 */
    fun setTabIcon(id: String, icon: String) = editOpts(id) { it.copy(icon = icon) }

    /**
     * 端のタブの印。決めていなければ中身から：フォルダ・予定・メールなどは記号、カテゴリーはいちばん前のアプリ、
     * ウィジェットだけのタブはグリッド。戻り値は記号の名前（"g:"）かアプリ（"app:"）、出さないときは null。
     */
    fun tabIcon(t: TabSpec): String? {
        val set = opts(t.id).icon
        if (set == "none") return null
        if (set.isNotEmpty()) return set
        if (t.kind == TabKind.CATEGORY) {
            val catId = t.folderUri ?: return "g:GRID"
            return ordered(catId, apps.filter { appCategories[it.key] == catId }).firstOrNull()?.let { "app:" + it.key } ?: "g:GRID"
        }
        return "g:" + when (mainKind(t)) {
            TabKind.FOLDER -> "FOLDER"
            TabKind.CALENDAR -> "CALENDAR"
            TabKind.MAIL -> "MAIL"
            TabKind.LINE -> "CHAT"
            TabKind.PEOPLE -> "PERSON"
            TabKind.BOOKMARKS -> "LINK"
            TabKind.CLIP -> "CLIP"
            TabKind.CAPTURE -> "CAMERA"
            TabKind.HATCH -> "HATCH"
            TabKind.WEB -> "GLOBE"
            TabKind.FILE -> "FILE"
            else -> "GRID"
        }
    }

    /** フォルダを置いたタブ（Alt＋1〜9 の順）。 */
    fun folderTabs(): List<TabSpec> = tabs.filter { t -> boardItems(t.id).any { it.type == BoardItem.FOLDER } }

    /** 置いたフォルダすべて（背景のファイラーの「場所」）。名前は置いたものの名前か、タブの名前。 */
    fun folderPlaces(): List<TabSpec> = tabs.flatMap { t ->
        boardItems(t.id).filter { it.type == BoardItem.FOLDER }.map { specOf(it).let { s -> if (it.label.isEmpty()) s.copy(label = t.label) else s } }
    }

    /** 背景のファイラーの場所の id から、フォルダを引く。 */
    fun place(id: String): TabSpec? = folderPlaces().firstOrNull { it.id == id }

    /** その中身（"calendar" など）があるタブ。 */
    fun tabWith(type: String): TabSpec? = tabs.firstOrNull { t -> boardItems(t.id).any { it.type == type } }

    /** 中身を 1 つ置いたタブを作る。名前は中身の種類から。 */
    fun addBoardTab(type: String, ref: String = "", fill: Boolean = true): Pair<TabSpec, BoardItem> {
        val label = (BoardItem.PANELS[type] ?: when (type) {
            BoardItem.FOLDER -> TabKind.FOLDER
            BoardItem.FILE -> TabKind.FILE
            BoardItem.WEB -> TabKind.WEB
            else -> TabKind.BOARD
        }).defaultLabel
        val t = TabSpec(Store.newId(), TabKind.BOARD, label)
        val item = BoardItem(Store.newId(), type, ref, fill = fill)
        updateTabs(tabs + t)
        addBoardItem(t.id, item)
        return t to item
    }

    // ---- 背景のファイラー ----

    /** フォルダのタブ：背景のファイラーをそのフォルダに切り替える（ドロワーは閉じる）。 */
    fun showFolder(t: TabSpec, closeDrawer: Boolean = true) {
        if (closeDrawer) {
            open = null
            filerOpen = true
        }
        val nasPath = t.nasPath
        if (nasPath != null) {
            openNas(nasPath, t.id)
            return
        }
        val df = t.driveFolder
        if (df != null) {
            openDrive(listOf(df), t.id)
            return
        }
        moveTo("tab:${t.id}")
        if (folders[t.id]?.stack.isNullOrEmpty() || folders[t.id]?.error != null) openRoot(t)
    }

    fun showRecent() {
        open = null
        filerOpen = true
        moveTo("recent")
    }

    private fun moveTo(l: String) {
        location = l
        store.location = l
    }

    fun openNas(path: String = nasState.path, fromTab: String? = null) {
        open = null
        filerOpen = true
        nasTab = fromTab
        moveTo("nas")
        loadNas(path)
    }

    fun loadNas(path: String, key: String = DESK) {
        val before = nasState(key)
        nasStates[key] = NasState(path = path, entries = if (path == before.path) before.entries else emptyList(), loading = true)
        viewModelScope.launch {
            val r = runCatching {
                withContext(Dispatchers.IO) {
                    when {
                        path.startsWith("?") -> {
                            val q = query(path.drop(1)) ?: throw java.io.IOException("この検索フォルダは消されました")
                            val from = if (q.days > 0) (System.currentTimeMillis() - q.days * 86_400_000L) / 1000 else 0
                            nas.search(listOfNotNull(q.folderId.ifEmpty { null }), q.nameContains, from, 500).filter { q.matchesKind(it.name) }
                        }
                        // 共有フォルダの一覧の上に、保存した検索を並べる
                        path.isEmpty() -> queries.filter { it.source == "nas" }.map { q ->
                            Nas.Item().apply { name = q.name; this.path = "?" + q.id; dir = true }
                        } + nas.list(path)
                        else -> nas.list(path)
                    }
                }
            }
            if (nasState(key).path != path) return@launch
            nasStates[key] = r.fold(
                { NasState(path, it) },
                { NasState(path, error = if (status?.nas != Nas.OK && status != null) status!!.message else "読み込めませんでした（${it.message}）") },
            )
            if (r.isFailure) refreshStatus(force = true)
        }
    }

    /** NAS の上のフォルダへ。共有フォルダの一覧より上は無い。 */
    fun nasUp(key: String = DESK): Boolean {
        val p = nasState(key).path
        if (p.isEmpty()) return false
        if (p.startsWith("?")) {
            loadNas("", key)
            return true
        }
        val cut = p.lastIndexOf('/')
        loadNas(if (cut <= 0) "" else p.substring(0, cut), key)
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
        setRef(tabId, TabSpec.NAS_PREFIX + path)
        nasStates.remove(tabId)
    }

    // ---- Google ドライブ ----

    fun openDrive(stack: List<Pair<String, String>> = driveState.stack.ifEmpty { listOf(GDrive.ROOT to "DRIVE") }, fromTab: String? = null) {
        open = null
        filerOpen = true
        driveTab = fromTab
        moveTo("drive")
        if (drive.enabled) loadDrive(stack) else driveStates[DESK] = DriveState(stack)
    }

    fun loadDrive(stack: List<Pair<String, String>>, key: String = DESK) {
        val before = driveState(key)
        driveStates[key] = DriveState(stack, entries = if (stack == before.stack) before.entries else emptyList(), loading = true)
        viewModelScope.launch {
            val id = stack.last().first
            val r = runCatching {
                val t = drive.token()
                withContext(Dispatchers.IO) {
                    when {
                        id.startsWith(SavedQuery.PREFIX) -> drive.search(t, query(id) ?: throw java.io.IOException("この検索フォルダは消されました"))
                        // 入口には、保存した検索も並べる
                        id == GDrive.ROOT -> drive.list(t, id) + queries.filter { it.source == "drive" }.map { GDrive.Item(it.id, it.name, GDrive.FOLDER, 0, 0, null, null) }
                        else -> drive.list(t, id)
                    }
                }
            }
            if (driveState(key).stack != stack) return@launch
            r.exceptionOrNull()?.let { if (it is GDrive.NeedConsent) driveConsent = it.intent }
            driveStates[key] = r.fold({ DriveState(stack, it) }, { DriveState(stack, error = it.message ?: it.javaClass.simpleName) })
        }
    }

    fun driveUp(key: String = DESK): Boolean {
        val st = driveState(key).stack
        if (st.size <= 1) return false
        loadDrive(st.dropLast(1), key)
        return true
    }

    /** Google でログインして、ドライブを読めるようにする。許可の画面が要れば driveConsent に置く。 */
    fun connectDrive() {
        viewModelScope.launch {
            val r = runCatching { drive.token(force = true) }
            val e = r.exceptionOrNull()
            when {
                e is GDrive.NeedConsent -> driveConsent = e.intent
                e != null -> (ctx as HomeApp).toast(e.message ?: "つなげませんでした")
                else -> afterDriveConnected()
            }
        }
    }

    /** 許可の画面から戻ったとき。 */
    fun driveConsentResult(ok: Boolean, data: android.content.Intent?) {
        if (!ok || !drive.acceptResult(data)) {
            (ctx as HomeApp).toast("Google ドライブにはつなぎませんでした")
            return
        }
        viewModelScope.launch { afterDriveConnected() }
    }

    private suspend fun afterDriveConnected() {
        driveOn = true
        runCatching { val t = drive.token(); withContext(Dispatchers.IO) { drive.about(t) } }.onSuccess {
            drive.email = it
            driveEmail = it
        }
        if (location == "drive") loadDrive(driveState.stack.ifEmpty { listOf(GDrive.ROOT to "DRIVE") })
    }

    fun disconnectDrive() {
        drive.signOut()
        driveOn = false
        driveEmail = ""
        driveStates.clear()
    }

    /** ドライブのファイルを端末（キャッシュ）に取得して渡す。Google ドキュメントはリンクで開く。 */
    fun fetchDrive(item: GDrive.Item, then: (File) -> Unit) {
        viewModelScope.launch {
            val r = runCatching {
                val t = drive.token()
                withContext(Dispatchers.IO) {
                    val dir = File(ctx.cacheDir, "nas").apply { mkdirs() }
                    dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(30)?.forEach { it.delete() }
                    File(dir, item.name.replace(Regex("[\\\\/:*?\"<>|]"), "_")).also { drive.download(t, item, it) }
                }
            }
            r.onSuccess(then).onFailure {
                if (it is GDrive.NeedConsent) driveConsent = it.intent
                else (ctx as HomeApp).toast("取得できませんでした：${it.message}")
            }
        }
    }

    fun assignDriveFolder(tabId: String, id: String, name: String) {
        setRef(tabId, TabSpec.DRIVE_PREFIX + id + ":" + name)
        driveStates.remove(tabId)
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

    // ---- 入力（声・文字・写真）→ 送る。MemoHatch にも残す ----

    /** 開いている入力の初めのやり方（"voice"・"text"・"photo"）。null なら閉じている。 */
    var quickOpen by mutableStateOf<String?>(null)
    var quickTargets by mutableStateOf(store.quickTargets); private set
    var quickMemo by mutableStateOf(store.quickMemo); private set
    fun changeQuickMemo(v: Boolean) { quickMemo = v; store.quickMemo = v }
    fun changeQuickTargets(list: List<String>) { quickTargets = list.distinct(); store.quickTargets = quickTargets }

    /** 送り先の名前。 */
    fun quickTargetLabel(t: String): String = when {
        t == "line" -> "LINE"
        t.startsWith("line:") -> t.substringAfter('|', "LINE")
        t == "mail" -> "メール"
        t == "share" -> "ほかへ共有"
        t == "clip" -> "Clip"
        else -> t
    }

    /** LINE の最近の会話（宛先に選べるもの）。このアプリが既定のホームのときだけ取れる。 */
    fun lineRecipients(): List<Pair<String, String>> =
        jp.yaegaki.theta.home.data.Shortcuts.shareTargets(ctx, LINE).map { it.id to jp.yaegaki.theta.home.data.Shortcuts.label(it) }

    fun memoInstalled() = apps.any { it.packageName == MEMO }

    /**
     * 送る。まず MemoHatch に残し（入っていなければこのホームの Clip に）、それから送り先へ。
     * target が "memo" なら残すだけ。photo は端末の中の写真（FileProvider の URI）。
     */
    fun sendQuick(text: String, photo: Uri?, target: String, start: (Intent) -> Unit) {
        val body = text.trim()
        if (body.isEmpty() && photo == null) return
        fun put(i: Intent): Intent {
            if (photo != null) {
                i.setType("image/jpeg").putExtra(Intent.EXTRA_STREAM, photo).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                i.clipData = android.content.ClipData.newRawUri("photo", photo)
            } else i.setType("text/plain")
            if (body.isNotEmpty()) i.putExtra(Intent.EXTRA_TEXT, body)
            return i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        // 必ず残す（MemoHatch は受け取るとそのまま保存して閉じる）
        if (quickMemo || target == "memo") {
            if (memoInstalled()) start(put(Intent(Intent.ACTION_SEND).setClassName(MEMO, "$MEMO.ShareActivity")))
            else {
                addToClip(ctx, body.ifEmpty { null }, listOfNotNull(photo))
                reloadClips()
            }
        }
        when {
            target == "memo" -> (ctx as HomeApp).toast(if (memoInstalled()) "MemoHatch に残しました" else "Clip に残しました（MemoHatch が入っていません）")
            target == "line" -> start(put(Intent(Intent.ACTION_SEND).setPackage(LINE)))
            // 決めた宛先：共有メニューの「直接共有」と同じく、ショートカットの id を付けて LINE に渡す
            target.startsWith("line:") -> start(put(Intent(Intent.ACTION_SEND).setPackage(LINE)).putExtra(Intent.EXTRA_SHORTCUT_ID, target.removePrefix("line:").substringBefore('|')))
            target == "mail" -> {
                val subject = body.lineSequence().firstOrNull().orEmpty().take(40)
                if (photo == null) start(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).putExtra(Intent.EXTRA_SUBJECT, subject).putExtra(Intent.EXTRA_TEXT, body).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                else start(put(Intent(Intent.ACTION_SEND)).putExtra(Intent.EXTRA_SUBJECT, subject).apply { selector = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")) })
            }
            target == "clip" -> {
                if (!(quickMemo && !memoInstalled())) { addToClip(ctx, body.ifEmpty { null }, listOfNotNull(photo)); reloadClips() }
                (ctx as HomeApp).toast("Clip に残しました")
            }
            else -> start(Intent.createChooser(put(Intent(Intent.ACTION_SEND)), "送る").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        quickOpen = null
    }

    // ---- 回線の速さ・再接続 ----

    var speed by mutableStateOf<jp.yaegaki.theta.home.data.Speed?>(null); private set
    var speedError by mutableStateOf<String?>(null); private set
    var measuring by mutableStateOf(false); private set
    var speedEvery by mutableStateOf(store.speedEvery); private set
    var speedOnMobile by mutableStateOf(store.speedOnMobile); private set
    fun changeSpeedEvery(v: Int) { speedEvery = v; store.speedEvery = v }
    fun changeSpeedOnMobile(v: Boolean) { speedOnMobile = v; store.speedOnMobile = v }

    private fun onWifi(): Boolean {
        val cm = ctx.getSystemService(android.net.ConnectivityManager::class.java) ?: return false
        return cm.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true
    }

    private fun online(): Boolean {
        val cm = ctx.getSystemService(android.net.ConnectivityManager::class.java) ?: return false
        return cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    /** 間隔が過ぎていれば測る（force ならすぐ）。モバイルデータで測らない設定なら、Wi-Fi のときだけ。 */
    fun refreshSpeed(force: Boolean = false) {
        if (safeMode || measuring) return
        if (!force) {
            if (speedEvery <= 0) return
            val last = speed?.at ?: 0L
            if (System.currentTimeMillis() - last < speedEvery * 60_000L) return
            if (!speedOnMobile && !onWifi()) return
        }
        if (!online()) {
            speedError = "つながっていません"
            return
        }
        measuring = true
        viewModelScope.launch {
            val wifi = onWifi()
            runCatching { withContext(Dispatchers.IO) { jp.yaegaki.theta.home.data.NetSpeed.measure(wifi) } }
                .onSuccess { speed = it; speedError = null }
                .onFailure { speedError = "測れませんでした（${it.message ?: it.javaClass.simpleName}）" }
            measuring = false
        }
    }

    /**
     * 再接続。普通のアプリはモバイルデータをオフ・オンできない（Android の決まり）ので、
     * 再接続用に置いたショートカット（Galaxy のルーチンなど）があればそれを、無ければ
     * 今の回線を「つながらない」と知らせて確かめ直させ、インターネットの設定パネル（モバイルデータの切り替え）を出す。
     */
    fun reconnect(start: (Intent) -> Unit) {
        shortcuts.firstOrNull { it.catId == RECONNECT }?.let { sc ->
            launchShortcut(sc)
            return
        }
        val cm = ctx.getSystemService(android.net.ConnectivityManager::class.java)
        runCatching { cm?.activeNetwork?.let { cm.reportNetworkConnectivity(it, false) } }
        start(Intent(android.provider.Settings.Panel.ACTION_INTERNET_CONNECTIVITY).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        // 戻ってきたら測り直す
        speed = null
        refreshStatus(force = true)
    }

    fun reconnectShortcut() = shortcuts.firstOrNull { it.catId == RECONNECT }

    /** 保存して、すぐ確かめる。otp は 2 段階認証のコード（求められたときだけ）。 */
    fun saveNas(url: String, account: String, password: String, otp: String = "") {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { nas.save(url, account, password); nas.setOtp(otp) } }
            recheckNas()
        }
    }

    /** 見せた指紋の証明書を信頼して、つなぎ直す。 */
    fun trustNasCert(fingerprint: String) {
        nas.trustCert(fingerprint)
        viewModelScope.launch { recheckNas() }
    }

    private suspend fun recheckNas() {
        checking = true
        status = withContext(Dispatchers.IO) { nas.check() }
        checking = false
        if (location == "nas") loadNas(if (status?.nas == Nas.OK) nasState.path else "")
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
        setRef(tabId, tree.toString())
        folders.remove(tabId)
        (tab(tabId) ?: boardItem(tabId)?.let(::specOf))?.let(::openRoot)
    }

    // ---- 保存した検索（仮想フォルダ） ----

    var queries by mutableStateOf(store.queries()); private set

    fun query(id: String): SavedQuery? = queries.firstOrNull { it.id == id }

    /** 追加か、同じ ID を置き換え。開いている一覧も読み直す。 */
    fun saveQuery(q: SavedQuery) {
        queries = if (queries.any { it.id == q.id }) queries.map { if (it.id == q.id) q else it } else queries + q
        store.saveQueries(queries)
        reloadPlace()
    }

    fun deleteQuery(id: String) {
        queries = queries.filter { it.id != id }
        store.saveQueries(queries)
        reloadPlace()
    }

    /** 検索フォルダを変えたら、開いている NAS・ドライブの一覧（背景とドロワー）を読み直す。 */
    private fun reloadPlace() {
        if (nas.configured()) nasStates.toMap().forEach { (k, st) -> if (k != DESK || location == "nas") loadNas(st.path, k) }
        if (drive.enabled) driveStates.toMap().forEach { (k, st) -> if (st.stack.isNotEmpty() && (k != DESK || location == "drive")) loadDrive(st.stack, k) }
    }

    // ---- フォルダの見せ方（フォルダごとに記憶） ----

    var folderViews by mutableStateOf(store.folderViews()); private set

    /**
     * keys は今のフォルダから上へ順に。最初に見つかった設定を使う（子のフォルダは親の設定を引き継ぐ）。
     * どこにも無ければ、写真・動画が多いフォルダはグリッド、それ以外は一覧。
     */
    fun viewFor(keys: List<String>, mostlyMedia: Boolean): ViewSpec =
        keys.firstNotNullOfOrNull { ViewSpec.decode(folderViews[it]) } ?: if (mostlyMedia) ViewSpec.GRID else ViewSpec.LIST

    fun setView(key: String, v: ViewSpec) {
        // 新しく触ったものを末尾に。多くなったら古いものから忘れる
        folderViews = (folderViews - key + (key to v.encode())).entries.toList().takeLast(500).associate { it.key to it.value }
        store.saveFolderViews(folderViews)
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

    fun addTab(kind: TabKind): TabSpec {
        val t = TabSpec(Store.newId(), kind, kind.defaultLabel)
        updateTabs(tabs + t)
        return t
    }

    /** アプリのカテゴリーをタブにする。名前はカテゴリーの名前（あとで変えられる）。 */
    fun addCategoryTab(c: Category) {
        updateTabs(tabs + TabSpec(Store.newId(), TabKind.CATEGORY, c.name.uppercase(), folderUri = c.id))
    }

    fun moveTab(id: String, delta: Int) {
        val i = tabs.indexOfFirst { it.id == id }
        val j = i + delta
        if (i < 0 || j !in tabs.indices) return
        updateTabs(tabs.toMutableList().also { it.add(j, it.removeAt(i)) })
    }

    fun renameTab(id: String, label: String) {
        if (label.isBlank()) return
        updateTabs(tabs.map { if (it.id == id) it.copy(label = label.trim()) else it })
    }

    fun removeTab(id: String) {
        val wasHere = location == "tab:$id" || boardItems(id).any { location == "tab:" + it.id }
        updateTabs(tabs.filter { it.id != id })
        if (open == id) open = null
        docked = docked - id
        saveOpts(tabOpts - id)
        boardItems(id).forEach(::releaseItem)
        if (boards.containsKey(id)) saveBoards(boards - id)
        folders.remove(id)
        nasStates.remove(id)
        driveStates.remove(id)
        if (wasHere) showRecent()
    }

    // ---- タブごとの設定（出す画面・幅・置いたアプリ） ----

    fun opts(id: String) = tabOpts[id] ?: jp.yaegaki.theta.home.data.TabOpts()

    private fun saveOpts(map: Map<String, jp.yaegaki.theta.home.data.TabOpts>) {
        tabOpts = map
        store.saveTabOpts(map)
    }

    private fun editOpts(id: String, f: (jp.yaegaki.theta.home.data.TabOpts) -> jp.yaegaki.theta.home.data.TabOpts) = saveOpts(tabOpts + (id to f(opts(id))))

    /** 今の画面（閉じた／開いた）で出すタブか。 */
    fun shownHere(id: String) = opts(id).shownOn(profile)

    /** 出す画面を変える。今の画面で隠したら、開いていれば閉じる。 */
    fun setTabShow(id: String, show: String) {
        editOpts(id) { it.copy(show = show) }
        if (!shownHere(id)) {
            if (open == id) open = null
            docked = docked - id
        }
    }

    /** この画面でのドロワーの幅（dp）。null なら決まった幅に戻す。 */
    fun setTabWidth(id: String, dp: Int?) = editOpts(id) { o -> o.copy(width = if (dp == null) o.width - profile else o.width + (profile to dp)) }

    fun tabWidth(id: String): Int? = opts(id).width[profile]

    /** なぞってドロワーの幅を変えている途中の幅（dp）。 */
    val liveDrawer = mutableStateMapOf<String, Int>()

    fun setTabApps(id: String, keys: List<String>) = editOpts(id) { it.copy(apps = keys.distinct()) }

    fun removeTabApp(id: String, key: String) = editOpts(id) { it.copy(apps = it.apps - key) }

    fun moveTabAppTo(id: String, from: String, to: String) = editOpts(id) { o ->
        val i = o.apps.indexOf(from)
        val j = o.apps.indexOf(to)
        if (i < 0 || j < 0 || i == j) o else o.copy(apps = o.apps.toMutableList().also { it.add(j, it.removeAt(i)) })
    }

    fun setTabSplit(id: String, split: Boolean) = editOpts(id) { it.copy(split = split) }

    // ---- 端のタブを片側・両側に ----

    var railMode by mutableStateOf(store.railMode); private set
    fun changeRailMode(v: String) { railMode = v; store.railMode = v }

    /** この画面で両側にタブを出すか。 */
    fun railsBoth(): Boolean = railMode == "both" || railMode == "auto" && profile == "wide"

    /** そのタブを左側に出すか（片側のときは START の側）。 */
    fun tabOnLeft(id: String?): Boolean {
        if (id == null || !railsBoth()) return railLeft
        return when (opts(id).side) {
            "left" -> true
            "right" -> false
            else -> if (tab(id)?.kind == TabKind.CATEGORY) !railLeft else railLeft
        }
    }

    /** 両側のとき、反対側へ移す。 */
    fun flipTabSide(id: String) { val left = tabOnLeft(id); editOpts(id) { it.copy(side = if (left) "right" else "left") } }

    /** ショートカットの置き場所のキー。カテゴリーのタブはカテゴリーの id、ほかのタブは "tab:<id>"。 */
    fun placeOf(id: String): String = tab(id)?.takeIf { it.kind == TabKind.CATEGORY }?.folderUri ?: "tab:$id"

    // ---- フォルダのタブ（ドロワーの中にフォルダの中身） ----

    /** 開いたときに、まだ読んでいなければ読む。NAS・ドライブはタブごとに別の場所を覚える。 */
    fun openFolderDrawer(t: TabSpec) {
        val nasPath = t.nasPath
        val df = t.driveFolder
        when {
            nasPath != null -> if (nasStates[t.id] == null || nasState(t.id).error != null) loadNas(nasPath, t.id)
            df != null -> if (drive.enabled && (driveStates[t.id] == null || driveState(t.id).error != null)) loadDrive(listOf(df), t.id)
            else -> if (folders[t.id]?.stack.isNullOrEmpty() || folders[t.id]?.error != null) openRoot(t)
        }
    }

    /** フォルダのドロワーで 1 つ上へ。もう上がなければ false（戻るでドロワーを閉じる）。 */
    fun drawerUp(t: TabSpec): Boolean {
        return when {
            t.nasPath != null -> nasUp(t.id)
            t.driveFolder != null -> driveUp(t.id)
            else -> up(t.id)
        }
    }

    /** WEB のタブの URL を決める。 */
    fun setTabUrl(id: String, url: String) = setRef(id, url.trim().let { if (it.contains("://")) it else "https://$it" })

    /** FILE のタブのファイルを決める（読み取りの許可を持ち続ける）。 */
    fun setTabFile(id: String, uri: Uri) {
        runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        setRef(id, uri.toString())
    }


    fun changeRailLeft(v: Boolean) { railLeft = v; store.railLeft = v }
    fun changeTheme(mode: String) { themeMode = mode; store.themeMode = mode }

    // ---- アプリのカテゴリーと仕分け ----

    var categories by mutableStateOf(store.categories()); private set
    var appCategories by mutableStateOf(store.appCategories()); private set
    /** 仕分けで「あとで」にしたアプリ（この起動の間だけ）。 */
    var skipped by mutableStateOf<Set<String>>(emptySet()); private set
    /** 仕分けの「戻す」用：直前に振り分けた（またはあとでにした）アプリ。 */
    private val sortHistory = ArrayList<String>()

    fun categoryOf(app: AppEntry): Category? = appCategories[app.key]?.let { id -> categories.firstOrNull { it.id == id } }

    /** まだどこにも振り分けていないアプリ。「あとで」にしたものは後ろへ回す。 */
    fun unsorted(): List<AppEntry> = apps.filter { categoryOf(it) == null }.sortedBy { it.key in skipped }

    fun addCategory(name: String): Category {
        val n = name.trim()
        categories.firstOrNull { it.name == n }?.let { return it }
        val c = Category(Store.newId(), n)
        categories = categories + c
        store.saveCategories(categories)
        return c
    }

    fun renameCategory(c: Category, name: String) {
        categories = categories.map { if (it.id == c.id) it.copy(name = name.trim().ifEmpty { it.name }) else it }
        store.saveCategories(categories)
    }

    fun moveCategory(c: Category, delta: Int) {
        val i = categories.indexOf(c)
        val j = i + delta
        if (i < 0 || j !in categories.indices) return
        categories = categories.toMutableList().also { it.add(j, it.removeAt(i)) }
        store.saveCategories(categories)
    }

    /** カテゴリーを消す。入っていたアプリは未分類に戻る。 */
    fun deleteCategory(c: Category) {
        categories = categories - c
        appCategories = appCategories.filterValues { it != c.id }
        store.saveCategories(categories)
        store.saveAppCategories(appCategories)
    }

    /** 候補の名前でカテゴリーをまとめて用意する（すでにあるものはそのまま）。 */
    fun ensureCategories(names: List<String>) = names.forEach { addCategory(it) }

    fun assign(app: AppEntry, c: Category?) {
        appCategories = if (c == null) appCategories - app.key else appCategories + (app.key to c.id)
        store.saveAppCategories(appCategories)
        skipped = skipped - app.key
        sortHistory += app.key
    }

    fun skip(app: AppEntry) {
        skipped = skipped + app.key
        sortHistory += app.key
    }

    /** 直前の仕分けを取り消す。取り消したアプリを返す。 */
    fun undoSort(): String? {
        val key = sortHistory.removeLastOrNull() ?: return null
        appCategories = appCategories - key
        store.saveAppCategories(appCategories)
        skipped = skipped - key
        return key
    }

    /** 仕分けガイドの候補（規定のカテゴリー）と、初めから選んでおくもの。 */
    var categoryPresets by mutableStateOf(store.categoryPresets); private set
    var categoryDefaults by mutableStateOf(store.categoryDefaults); private set

    private fun savePresets(list: List<String>, defaults: List<String> = categoryDefaults) {
        categoryPresets = list.distinct()
        categoryDefaults = defaults.filter { it in categoryPresets }
        store.categoryPresets = categoryPresets
        store.categoryDefaults = categoryDefaults
    }

    fun addPreset(name: String) {
        val n = name.trim()
        if (n.isNotEmpty()) savePresets(categoryPresets + n, categoryDefaults + n)
    }

    /** 候補の名前を変える。同じ名前のカテゴリーがすでにあれば、そちらも同じ名前に変える。 */
    fun renamePreset(old: String, name: String) {
        val n = name.trim()
        if (n.isEmpty() || n == old) return
        savePresets(categoryPresets.map { if (it == old) n else it }, categoryDefaults.map { if (it == old) n else it })
        val aliases = store.categoryAliases
        val original = aliases.entries.firstOrNull { it.value == old }?.key ?: old
        store.categoryAliases = aliases + (original to n)
        categories.firstOrNull { it.name == old }?.let { renameCategory(it, n) }
    }

    /** 候補から外す。作ってあるカテゴリーと中のアプリはそのまま。 */
    fun deletePreset(name: String) = savePresets(categoryPresets - name, categoryDefaults - name)

    fun toggleDefault(name: String) =
        savePresets(categoryPresets, if (name in categoryDefaults) categoryDefaults - name else categoryDefaults + name)

    fun movePreset(name: String, delta: Int) {
        val i = categoryPresets.indexOf(name)
        val j = i + delta
        if (i < 0 || j !in categoryPresets.indices) return
        savePresets(categoryPresets.toMutableList().also { it.add(j, it.removeAt(i)) })
    }

    fun resetPresets() {
        store.resetCategoryPresets()
        categoryPresets = store.categoryPresets
        categoryDefaults = store.categoryDefaults
    }

    // ---- このアプリの更新・ホームの登録 ----

    data class UpdateState(
        val latest: Release? = null,
        val newer: Boolean = false,
        val checking: Boolean = false,
        val progress: Float? = null,
        val error: String? = null,
        val checkedAt: Long = 0,
    )

    var update by mutableStateOf(UpdateState()); private set
    var autoUpdate by mutableStateOf(store.autoUpdate); private set
    /** 既定のホームアプリになっているか。onResume で調べ直す。 */
    var isDefaultHome by mutableStateOf(true); private set

    val versionName: String = jp.yaegaki.theta.home.BuildConfigInfo.versionName(app)

    fun changeAutoUpdate(v: Boolean) { autoUpdate = v; store.autoUpdate = v }

    fun refreshHomeRole() {
        val rm = ctx.getSystemService(android.app.role.RoleManager::class.java)
        isDefaultHome = rm == null || !rm.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME) || rm.isRoleHeld(android.app.role.RoleManager.ROLE_HOME)
    }

    /** 新しい版があるか確かめる。force でなければ自動更新がオンのときだけ、1 日 1 回まで。 */
    fun checkUpdate(force: Boolean = false) {
        if (update.checking) return
        // 使いながら版を重ねているので、1 時間ごとに確かめる（GitHub への問い合わせは軽い）
        if (!force && (!autoUpdate || System.currentTimeMillis() - store.lastUpdateCheck < 3600_000)) return
        update = update.copy(checking = true, error = null)
        viewModelScope.launch {
            val r = runCatching { jp.yaegaki.theta.home.hatch.SelfUpdate.latest() }
            store.lastUpdateCheck = System.currentTimeMillis()
            val latest = r.getOrNull()
            update = UpdateState(
                latest = latest,
                newer = latest != null && jp.yaegaki.theta.home.ui.isNewer(latest.version, versionName),
                error = r.exceptionOrNull()?.message,
                checkedAt = store.lastUpdateCheck,
            )
        }
    }

    /** 新しい版を取得して、ハッシュを照合し、インストールへ（onReady で確認画面を出す）。 */
    fun downloadUpdate(onReady: (File) -> Unit) {
        val r = update.latest ?: return
        if (update.progress != null) return
        update = update.copy(progress = 0f, error = null)
        viewModelScope.launch {
            var shown = 0
            val result = runCatching {
                HatchInstaller.download(ctx, r) { p ->
                    val pct = (p * 100).toInt()
                    if (pct > shown) { shown = pct; viewModelScope.launch { update = update.copy(progress = p) } }
                }
            }
            update = update.copy(progress = null, error = result.exceptionOrNull()?.message)
            result.getOrNull()?.let(onReady)
        }
    }

    // ---- アイコンのツートーン ----

    var iconMode by mutableStateOf(store.iconMode); private set
    var geminiModel by mutableStateOf(store.geminiModel); private set
    var hasGeminiKey by mutableStateOf(store.geminiKey.isNotEmpty()); private set
    /** 型を作り直したら増やす。アイコンを描き直すきっかけ。 */
    var iconVersion by mutableStateOf(0); private set

    data class IconJob(val done: Int, val total: Int, val current: String, val failed: Int, val lastError: String?, val running: Boolean)
    var iconJob by mutableStateOf<IconJob?>(null); private set
    private var iconWork: kotlinx.coroutines.Job? = null

    fun changeIconMode(mode: String) { iconMode = mode; store.iconMode = mode }

    fun saveGemini(key: String?, model: String) {
        if (key != null) store.geminiKey = key
        store.geminiModel = model
        geminiModel = store.geminiModel
        hasGeminiKey = store.geminiKey.isNotEmpty()
    }

    // ---- One UI（ホームの外）へ持ち出す：壁紙とアイコン ----

    /** 書き出しの進み具合（done / total）。null なら何もしていない。 */
    var artJob by mutableStateOf<Pair<Int, Int>?>(null); private set

    private fun palette() = jp.yaegaki.theta.home.ui.paletteFor(
        themeMode,
        (ctx.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES,
    )

    private fun isDark(): Boolean = palette().bg.luminance() < 0.5f

    /** HUD の壁紙を作って、ホームとロック画面に設定する（写真にも保存）。One UI はこの壁紙から色を取る。 */
    fun applyHudWallpaper(width: Int, height: Int, dark: Boolean = isDark()) {
        viewModelScope.launch {
            val r = runCatching {
                withContext(Dispatchers.IO) {
                    val bmp = jp.yaegaki.theta.home.data.HudArt.wallpaper(ctx, width, height, dark)
                    // 開いた画面とカバー画面で縦横が違うので、今の画面の大きさで作る
                    jp.yaegaki.theta.home.data.HudArt.savePng(ctx, bmp, "Wallpaper", "hud-wallpaper-" + (if (dark) "dark" else "light") + "-${width}x$height")
                    android.app.WallpaperManager.getInstance(ctx).setBitmap(bmp, null, true, android.app.WallpaperManager.FLAG_SYSTEM or android.app.WallpaperManager.FLAG_LOCK)
                }
            }
            (ctx as HomeApp).toast(if (r.isSuccess) "壁紙を HUD にしました（写真の ThetaHomeHatch にも保存）" else "壁紙を設定できませんでした：${r.exceptionOrNull()?.message}")
        }
    }

    /** すべてのアプリのアイコンを、今の見た目（3 色・配色）で PNG にして写真へ保存する。Theme Park でアプリごとに選べる。 */
    fun exportIcons(size: Int = 512) {
        if (artJob != null) return
        val list = apps
        val palette = palette()
        val (plate, base, accent) = jp.yaegaki.theta.home.ui.iconTriple(palette, iconColors).let { (a, b, c) ->
            Triple(a.toArgbInt(), b.toArgbInt(), c.toArgbInt())
        }
        val folder = "Icons-" + java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US).format(java.util.Date())
        val mode = iconMode
        artJob = 0 to list.size
        viewModelScope.launch {
            var saved = 0
            withContext(Dispatchers.IO) {
                list.forEachIndexed { i, app ->
                    runCatching {
                        val original = Apps.icon(ctx, app, size) ?: return@runCatching
                        val out = when (mode) {
                            jp.yaegaki.theta.home.data.IconStyle.LOCAL, jp.yaegaki.theta.home.data.IconStyle.GEMINI -> {
                                val mask = (if (mode == jp.yaegaki.theta.home.data.IconStyle.GEMINI) jp.yaegaki.theta.home.data.IconStyle.cached(ctx, app.key, iconFamily()) else null)
                                    ?: jp.yaegaki.theta.home.data.IconStyle.localMask(original)
                                jp.yaegaki.theta.home.data.HudArt.paintMask(mask, size, plate, base, accent)
                            }
                            else -> original
                        }
                        if (jp.yaegaki.theta.home.data.HudArt.savePng(ctx, out, folder, app.label) != null) saved++
                    }
                    artJob = (i + 1) to list.size
                }
            }
            artJob = null
            (ctx as HomeApp).toast("$saved 個のアイコンを 写真 › Pictures › ThetaHomeHatch › $folder に保存しました")
        }
    }

    private fun androidx.compose.ui.graphics.Color.toArgbInt(): Int = android.graphics.Color.argb((alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt())

    /** Gemini で加工済みのアプリの数。 */
    fun geminiDone(): Int = apps.count { jp.yaegaki.theta.home.data.IconStyle.cacheFile(ctx, it.key, iconFamily()).exists() }

    /** 今のテーマのアイコンの型の作り（Gemini に頼む描き方と保存場所）。 */
    fun iconFamily(): String = jp.yaegaki.theta.home.data.IconStyle.family(themeMode)

    /** 0.15 までの 2 色の型だけがあるアプリの数（3 色で作り直せる）。 */
    fun geminiLegacy(): Int = apps.count { !jp.yaegaki.theta.home.data.IconStyle.cacheFile(ctx, it.key).exists() && jp.yaegaki.theta.home.data.IconStyle.legacyFile(ctx, it.key).exists() }

    /** まだ型の無いアプリ（only を渡せばそれだけ）を、1 つずつ Gemini で加工する。 */
    fun startGeminiIcons(only: AppEntry? = null, upgrade: Boolean = false) {
        if (iconWork?.isActive == true) return
        val key = store.geminiKey
        if (key.isEmpty()) {
            (ctx as HomeApp).toast("先に Gemini の API キーを入れてください"); return
        }
        val family = iconFamily()
        val targets = only?.let { listOf(it) } ?: apps.filter {
            val done = jp.yaegaki.theta.home.data.IconStyle.cacheFile(ctx, it.key, family).exists()
            val legacy = family == jp.yaegaki.theta.home.data.IconStyle.FAMILY_HUD && jp.yaegaki.theta.home.data.IconStyle.legacyFile(ctx, it.key).exists()
            // ふだんは型の無いものだけ。upgrade なら 2 色の型しか無いものを 3 色で作り直す
            !done && (if (upgrade) legacy else !legacy)
        }
        if (targets.isEmpty()) {
            (ctx as HomeApp).toast("すべてのアプリを加工済みです"); return
        }
        iconWork = viewModelScope.launch {
            var failed = 0
            var lastError: String? = null
            targets.forEachIndexed { i, app ->
                iconJob = IconJob(i, targets.size, app.label, failed, lastError, true)
                val r = runCatching {
                    withContext(Dispatchers.IO) {
                        val icon = Apps.icon(ctx, app, 192) ?: error("アイコンを読めません")
                        val mask = jp.yaegaki.theta.home.data.IconStyle.geminiMask(key, store.geminiModel, icon, family)
                        jp.yaegaki.theta.home.data.IconStyle.cacheFile(ctx, app.key, family).outputStream().use { mask.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                    }
                }
                r.onFailure { failed++; lastError = "${app.label}：${it.message}" }
                r.onSuccess { iconVersion++ }
                // 混み合い（429）を避けるため、少し間をあける
                kotlinx.coroutines.delay(if (r.exceptionOrNull()?.message?.contains("429") == true) 20_000 else 1_500)
            }
            iconJob = IconJob(targets.size, targets.size, "", failed, lastError, false)
        }
    }

    fun cancelGeminiIcons() {
        iconWork?.cancel()
        iconJob = iconJob?.copy(running = false)
    }

    /** Gemini の型を消して、簡易加工の表示に戻す。app を渡せばそのアプリだけ。 */
    fun clearGeminiIcons(app: AppEntry? = null) {
        if (app != null) {
            // そのアプリは、今の作りの型だけ作り直す（hud なら前の 2 色の型も）
            jp.yaegaki.theta.home.data.IconStyle.cacheFile(ctx, app.key, iconFamily()).delete()
            if (iconFamily() == jp.yaegaki.theta.home.data.IconStyle.FAMILY_HUD) jp.yaegaki.theta.home.data.IconStyle.legacyFile(ctx, app.key).delete()
        } else {
            File(ctx.filesDir, "icons").listFiles()?.forEach { it.delete() }
            jp.yaegaki.theta.home.data.IconStyle.FAMILIES.forEach { f -> File(ctx.filesDir, jp.yaegaki.theta.home.data.IconStyle.dirOf(f)).listFiles()?.forEach { it.delete() } }
        }
        iconVersion++
    }

    // ---- 設定の書き出し・読み込み（引っ越し） ----

    /** 読み込み後の案内（入っていないアプリなど）。閉じるまで出しておく。 */
    var importReport by mutableStateOf<jp.yaegaki.theta.home.data.ImportReport?>(null)

    fun exportSettings(uri: Uri) {
        viewModelScope.launch {
            val ok = runCatching {
                withContext(Dispatchers.IO) {
                    val text = jp.yaegaki.theta.home.data.Backup.export(ctx, store, apps)
                    ctx.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) }
                }
            }
            (ctx as HomeApp).toast(if (ok.isSuccess) "設定を書き出しました" else "書き出せませんでした：${ok.exceptionOrNull()?.message}")
        }
    }

    fun importSettings(uri: Uri) {
        viewModelScope.launch {
            val r = runCatching {
                withContext(Dispatchers.IO) {
                    val text = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
                    jp.yaegaki.theta.home.data.Backup.import(ctx, store, text)
                }
            }
            r.onSuccess { reloadFromStore(); importReport = it }
                .onFailure { (ctx as HomeApp).toast("読み込めませんでした：${it.message}") }
        }
    }

    /** 保存してある設定を読み直して画面に反映する。 */
    fun reloadFromStore() {
        tabs = store.tabs()
        railLeft = store.railLeft
        railMode = store.railMode
        themeMode = store.themeMode
        monoIcons = store.monoIcons
        iconMode = store.iconMode
        iconColors = store.iconColors
        voiceApps = store.voiceApps
        noticeOrder = store.noticeOrder
        noticeLeft = store.noticeLeft
        geminiModel = store.geminiModel
        favoriteApps = store.favoriteApps
        categories = store.categories()
        appCategories = store.appCategories()
        categoryPresets = store.categoryPresets
        categoryDefaults = store.categoryDefaults
        bookmarks = store.bookmarks()
        folderViews = store.folderViews()
        queries = store.queries()
        appOrder = store.appOrder()
        tabOpts = store.tabOpts()
        shortcuts = store.shortcuts()
        boards = store.boards()
        folders.clear()
        open = null
        docked = emptyList()
        location = store.location.ifEmpty { "recent" }
        place(location.removePrefix("tab:"))?.let { showFolder(it, closeDrawer = false) }
        refreshApps()
        refreshStatus(force = true)
    }

    fun suggestedCategory(app: AppEntry): Category? =
        jp.yaegaki.theta.home.data.Categories.suggest(app)?.let { name ->
            val current = store.categoryAliases[name] ?: name
            categories.firstOrNull { it.name == current }
        }
    fun changeMonoIcons(v: Boolean) { monoIcons = v; store.monoIcons = v }

    // ---- 端の下に固定（よく使うアプリ・ショートカット。タブをスクロールしても隠れない） ----

    /** 端の下に置けるのは 8 つまで。キーは Store.favoriteApps の説明のとおり。 */
    fun toggleDock(key: String) = saveDock(if (key in favoriteApps) favoriteApps - key else (favoriteApps + key).take(DOCK_MAX))

    /** アプリをピン留めしているか（"pkg:" で置いた LINE なども）。 */
    fun isPinned(app: AppEntry) = app.key in favoriteApps || "pkg:" + app.packageName in favoriteApps

    fun toggleDockApp(app: AppEntry) {
        if (isPinned(app)) saveDock(favoriteApps - app.key - ("pkg:" + app.packageName)) else toggleDock(app.key)
    }

    /** ピン留めのアプリだけ選び直す（ショートカット・機能はそのまま）。 */
    fun setDockApps(appKeys: List<String>) {
        val kept = favoriteApps.filter { k ->
            k.startsWith("sc:") || k.startsWith("act:") || k in appKeys ||
                k.startsWith("pkg:") && apps.any { "pkg:" + it.packageName == k && it.key in appKeys }
        }
        val keptApps = kept.flatMap { k -> if (k.startsWith("pkg:")) apps.filter { "pkg:" + it.packageName == k }.map { it.key } else listOf(k) }
        saveDock((kept + appKeys.filter { it !in keptApps }).take(DOCK_MAX))
    }

    /** ピン留めの中身：アプリ・ショートカット・機能。入っていないアプリ・消えたショートカットは出さない。 */
    fun dockItems(): List<Pair<String, Any>> = favoriteApps.mapNotNull { key ->
        when {
            key.startsWith("sc:") -> shortcuts.firstOrNull { it.id == key.removePrefix("sc:") }
            key.startsWith("pkg:") -> apps.firstOrNull { it.packageName == key.removePrefix("pkg:") }
            key.startsWith("act:") -> DOCK_ACTIONS.firstOrNull { it.id == key.removePrefix("act:") }?.takeIf { a -> a.pkg == null || apps.any { it.packageName == a.pkg } || a.fallback }
            else -> apps.firstOrNull { it.key == key }
        }?.let { key to it }
    }

    /** ピン留めの機能を動かす。 */
    fun runDockAction(a: DockAction, start: (Intent) -> Unit) {
        when (a.id) {
            // Google の音声検索（Google アプリが無ければ、端末の音声入力の検索）
            "voice" -> {
                val google = Intent(android.speech.RecognizerIntent.ACTION_WEB_SEARCH).setPackage(GOOGLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (apps.any { it.packageName == GOOGLE }) start(google)
                else start(Intent(android.speech.RecognizerIntent.ACTION_WEB_SEARCH).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            // 入力：声・文字・写真を入れてから、送り先を選ぶ
            "quick" -> quickOpen = "voice"
            // 声でアプリを開く（呼び名でも）：すべてのアプリを開いて聞き取る
            "voice-app" -> openApps(voice = true)
            // MemoHatch：新しい音声メモ（録音から）
            "memo-audio" -> start(Intent("jp.yaegaki.theta.memo.NEW").setPackage(MEMO).putExtra("type", "audio").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun moveDock(key: String, delta: Int) {
        val i = favoriteApps.indexOf(key)
        val j = i + delta
        if (i < 0 || j !in favoriteApps.indices) return
        saveDock(favoriteApps.toMutableList().also { it.add(j, it.removeAt(i)) })
    }

    private fun saveDock(list: List<String>) {
        favoriteApps = list
        store.favoriteApps = list
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

    fun changeProfile(p: String) {
        if (p == profile) return
        profile = p
        // この画面では出さないタブは閉じる
        open?.let { if (!shownHere(it)) open = null }
        docked = docked.filter { shownHere(it) }
    }

    /** ドラッグで並べ替え：from を to の場所へ。 */
    fun moveTabTo(from: String, to: String) {
        val i = tabs.indexOfFirst { it.id == from }
        val j = tabs.indexOfFirst { it.id == to }
        if (i < 0 || j < 0 || i == j) return
        updateTabs(tabs.toMutableList().also { it.add(j, it.removeAt(i)) })
    }


    /** カテゴリーごとのアプリの並び（ドラッグで決めたもの）。キーはカテゴリーの id（未分類は ""）。 */
    var appOrder by mutableStateOf(store.appOrder()); private set

    /** 並びを決めていないアプリは、決めたものの後ろに名前順。 */
    fun ordered(catId: String, list: List<AppEntry>): List<AppEntry> {
        val order = appOrder[catId] ?: return list
        val pos = order.withIndex().associate { it.value to it.index }
        return list.sortedWith(compareBy({ pos[it.key] ?: Int.MAX_VALUE }, { it.label.lowercase() }))
    }

    /** shown は今見えている並び。from を to の場所へ動かして、そのカテゴリーの並びとして覚える。 */
    fun moveAppTo(catId: String, shown: List<AppEntry>, from: String, to: String) {
        val keys = ordered(catId, shown).map { it.key }.toMutableList()
        val i = keys.indexOf(from)
        val j = keys.indexOf(to)
        if (i < 0 || j < 0 || i == j) return
        keys.add(j, keys.removeAt(i))
        // 絞り込みで隠れているものの位置は前のまま残す
        val rest = appOrder[catId].orEmpty().filter { it !in keys }
        appOrder = appOrder + (catId to keys + rest)
        store.saveAppOrder(appOrder)
    }

    // ---- アプリのショートカット（カテゴリーのタブに置く） ----

    var shortcuts by mutableStateOf(store.shortcuts()); private set

    /** ショートカットをカテゴリーに置く。固定（pin）もここでする。 */
    fun addShortcut(info: android.content.pm.ShortcutInfo, catId: String) {
        val sc = jp.yaegaki.theta.home.data.PinnedShortcut(Store.newId(), info.`package`, info.id, jp.yaegaki.theta.home.data.Shortcuts.label(info), catId)
        jp.yaegaki.theta.home.data.Shortcuts.saveIcon(ctx, info, sc.id)
        shortcuts = shortcuts.filter { !(it.packageName == sc.packageName && it.shortcutId == sc.shortcutId && it.catId == catId) } + sc
        store.saveShortcuts(shortcuts)
        repin(sc.packageName)
        iconVersion++
        // 端の下に置くものは、並びにも足す
        if (catId == DOCK) toggleDock("sc:" + sc.id)
    }

    /** ほかのアプリの「ホーム画面に追加」で受け取ったもの（固定は受け取った時点で済んでいる）。 */
    fun reloadShortcuts() {
        shortcuts = store.shortcuts()
        iconVersion++
    }

    fun removeShortcut(id: String) {
        val sc = shortcuts.firstOrNull { it.id == id } ?: return
        if ("sc:$id" in favoriteApps) saveDock(favoriteApps - "sc:$id")
        shortcuts = shortcuts.filter { it.id != id }
        store.saveShortcuts(shortcuts)
        jp.yaegaki.theta.home.data.Shortcuts.iconFile(ctx, id).delete()
        repin(sc.packageName)
    }

    fun renameShortcut(id: String, label: String) {
        if (label.isBlank()) return
        shortcuts = shortcuts.map { if (it.id == id) it.copy(label = label.trim()) else it }
        store.saveShortcuts(shortcuts)
    }

    fun moveShortcutTo(from: String, to: String) {
        val i = shortcuts.indexOfFirst { it.id == from }
        val j = shortcuts.indexOfFirst { it.id == to }
        if (i < 0 || j < 0 || i == j) return
        shortcuts = shortcuts.toMutableList().also { it.add(j, it.removeAt(i)) }
        store.saveShortcuts(shortcuts)
    }

    fun launchShortcut(sc: jp.yaegaki.theta.home.data.PinnedShortcut) {
        if (!jp.yaegaki.theta.home.data.Shortcuts.start(ctx, sc)) (ctx as HomeApp).toast("開けませんでした（このアプリが既定のホームのときだけ開けます）")
    }

    /** そのアプリについて、置いているショートカットだけを固定にしておく。 */
    private fun repin(pkg: String) {
        jp.yaegaki.theta.home.data.Shortcuts.setPinned(ctx, pkg, shortcuts.filter { it.packageName == pkg }.map { it.shortcutId }.distinct())
    }

    // ---- ふつうのタブ（BOARD）：ウィジェット・フォルダ・ファイル・ページを並べる ----

    fun boardItems(tabId: String) = boards[tabId].orEmpty()

    /** 縦いっぱいのものを置いたタブか（そのときはパネルを縦いっぱいにする）。 */
    fun boardFills(tabId: String) = boardItems(tabId).any { it.fill }

    private fun saveBoards(map: Map<String, List<BoardItem>>) {
        boards = map
        store.saveBoards(map)
    }

    private fun editBoard(tabId: String, f: (List<BoardItem>) -> List<BoardItem>) = saveBoards(boards + (tabId to f(boardItems(tabId))))

    /** 置いたもの 1 つがどのタブにあるか。 */
    private fun boardOf(itemId: String): String? = boards.entries.firstOrNull { (_, v) -> v.any { it.id == itemId } }?.key

    fun boardItem(itemId: String): BoardItem? = boards.values.firstNotNullOfOrNull { v -> v.firstOrNull { it.id == itemId } }

    fun addBoardItem(tabId: String, item: BoardItem) = editBoard(tabId) { it + item }

    fun updateBoardItem(item: BoardItem) {
        val tabId = boardOf(item.id) ?: return
        editBoard(tabId) { list -> list.map { if (it.id == item.id) item else it } }
    }

    /** この画面での高さ（dp）。 */
    fun setItemHeight(itemId: String, dp: Int) {
        val item = boardItem(itemId) ?: return
        updateBoardItem(item.copy(h = item.h + (profile to dp)))
    }

    fun moveBoardItem(itemId: String, delta: Int) {
        val tabId = boardOf(itemId) ?: return
        editBoard(tabId) { list ->
            val i = list.indexOfFirst { it.id == itemId }
            val j = i + delta
            if (i < 0 || j !in list.indices) list else list.toMutableList().also { it.add(j, it.removeAt(i)) }
        }
    }

    /** 外す。ウィジェットなら割り当ても消す。 */
    fun removeBoardItem(itemId: String) {
        val tabId = boardOf(itemId) ?: return
        val item = boardItem(itemId)
        editBoard(tabId) { list -> list.filter { it.id != itemId } }
        releaseItem(item)
    }

    private fun releaseItem(item: BoardItem?) {
        if (item?.type == BoardItem.WIDGET) item.ref.toIntOrNull()?.let { (ctx as HomeApp).widgets.release(it) }
        folders.remove(item?.id)
        nasStates.remove(item?.id)
        driveStates.remove(item?.id)
    }

    /** 置いたものを、フォルダ・ファイル・ページの画面に渡す形にする（id は置いたものの id）。 */
    fun specOf(item: BoardItem): TabSpec {
        val kind = when (item.type) {
            BoardItem.FILE -> TabKind.FILE
            BoardItem.WEB -> TabKind.WEB
            else -> TabKind.FOLDER
        }
        return TabSpec(item.id, kind, item.label.ifEmpty { kind.defaultLabel }, item.ref.ifEmpty { null })
    }

    /** タブか置いたものの、場所（フォルダ・ファイル・URL）を変える。 */
    private fun setRef(id: String, ref: String) {
        if (tabs.any { it.id == id }) {
            updateTabs(tabs.map { if (it.id == id) it.copy(folderUri = ref) else it })
            return
        }
        boardItem(id)?.let { updateBoardItem(it.copy(ref = ref)) }
    }

    /** ウィジェットの設定画面から戻ったとき（ホーム・エッジの onActivityResult から）。 */
    fun onWidgetConfigured(ok: Boolean) {
        val id = store.pendingWidget
        if (id == -1) return
        if (ok) addWidget(id)
        else {
            (ctx as HomeApp).widgets.release(id)
            store.pendingBoard = ""
            (ctx as HomeApp).toast("ウィジェットの設定をやめたので、置きませんでした")
        }
        store.pendingWidget = -1
    }

    /** 割り当てが済んだウィジェットを置く。足している途中のタブがあればその下に、無ければ新しいタブにする。 */
    fun addWidget(id: Int) {
        val host = (ctx as HomeApp).widgets
        val info = host.info(id) ?: run {
            (ctx as HomeApp).toast("ウィジェットを置けませんでした（割り当てに失敗）")
            return
        }
        val d = ctx.resources.displayMetrics.density
        val item = BoardItem(Store.newId(), BoardItem.WIDGET, id.toString(), h = mapOf(profile to (info.minHeight / d).toInt().coerceIn(120, 600)))
        val into = store.pendingBoard.also { store.pendingBoard = "" }
        val tabId = into.takeIf { t -> tabs.any { it.id == t } } ?: run {
            val t = TabSpec(Store.newId(), TabKind.BOARD, host.label(info).take(14))
            updateTabs(tabs + t)
            setTabWidth(t.id, (info.minWidth / d).toInt().coerceIn(240, 600) + 16)
            t.id
        }
        addBoardItem(tabId, item)
        open = tabId
        lastOpened = open
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
        const val APPS_ID = "@apps"

        const val GOOGLE = "com.google.android.googlequicksearchbox"
        const val DSBOOKS = "jp.shinwakai.dsbooks.preview"
        const val MEMO = "jp.yaegaki.theta.memo"
        const val LINE = "jp.naver.line.android"

        /** ピン留めできる機能。pkg のアプリが入っているときだけ出す（fallback は無くても出す）。 */
        val DOCK_ACTIONS = listOf(
            DockAction("quick", "入力", null, fallback = true),
            DockAction("voice-app", "声でアプリ", null, fallback = true),
            DockAction("voice", "音声検索", GOOGLE, fallback = true),
            DockAction("memo-audio", "音声メモ", MEMO),
        )

        /** 再接続ボタンで動かすショートカット（Galaxy のルーチンなど）の置き場所。 */
        const val RECONNECT = "reconnect"

        /** 端の下（よく使う）に置いたショートカットの置き場所。 */
        const val DOCK = "dock"
        const val DOCK_MAX = 8

        /** NAS・ドライブの中身のうち、背景のファイラーのもの。 */
        const val DESK = ""

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

/** ピン留めできる機能（アプリの中の 1 つの操作）。アイコンは pkg のアプリのアイコンにマイクの印。 */
data class DockAction(val id: String, val label: String, val pkg: String?, val fallback: Boolean = false)
