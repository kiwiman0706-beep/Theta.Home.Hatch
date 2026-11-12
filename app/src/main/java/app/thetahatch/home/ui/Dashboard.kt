package app.thetahatch.home.ui

import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.collectAsState

import android.Manifest
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.thetahatch.home.HomeModel
import app.thetahatch.home.data.HomeSection
import androidx.compose.runtime.key
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.BoxWithConstraints
import app.thetahatch.home.data.Files
import app.thetahatch.home.data.Weather
import app.thetahatch.home.data.WeatherPlace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import app.thetahatch.home.data.ViewItem
import app.thetahatch.home.notify.HatchNotificationListener
import app.thetahatch.home.notify.MediaWatch

/**
 * ホーム（タブではないところ）：時計・検索・予定・通知・天気・つながり・ファイル・ファイラー・端末を、決めた順に並べる。
 * 区画ごとに、閉じた画面・開いた画面で出すかを決められる。開いた画面・横長では 2 列（区画ごとに左右を決める）。
 * 並べ方は設定の「HOME / ホームの並び」で。
 */
@Composable
fun HomeBoard(model: HomeModel, openNasSettings: () -> Unit, openDriveSettings: () -> Unit) {
    LaunchedEffect(model.weatherPlace) { model.refreshWeather() }
    val sections = model.sectionsHere()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // 両側にタブを出すと幅が減るので 560dp から（Fold5 のメインで両側のタブでも 2 列になるように）
        val twoCols = model.profile == "wide" && model.homeTwoColumns && maxWidth >= 560.dp
        // 右の列に何も置いていなければ、ファイラー（無ければ下半分）を右へ。全部が 1 列に縦に並ばないように
        val placed = if (twoCols && sections.none { it.col == 1 } && sections.size > 1) {
            if (sections.any { it.id == HomeSection.FILER }) sections.map { if (it.id == HomeSection.FILER) it.copy(col = 1) else it }
            else sections.mapIndexed { i, it -> if (i >= (sections.size + 1) / 2) it.copy(col = 1) else it }
        } else sections
        val left = placed.filter { !twoCols || it.col == 0 }
        val right = if (twoCols) placed.filter { it.col == 1 } else emptyList()
        // 探している間も並びは変えない（検索の箱を作り直すとキーボードが閉じるので）。検索のある列で、箱の下に結果を出す
        // 検索の区画を出していない画面で（キーボードから）打ったときは、箱と結果だけを出す
        if (model.query.isNotBlank() && sections.none { it.id == HomeSection.SEARCH }) Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SearchRow(model)
            SearchResults(model)
        } else if (left.isNotEmpty() && right.isNotEmpty()) Row(Modifier.fillMaxSize()) {
            SectionColumn(model, left, Modifier.weight(1f).fillMaxHeight(), openNasSettings, openDriveSettings)
            SectionColumn(model, right, Modifier.weight(1f).fillMaxHeight(), openNasSettings, openDriveSettings)
        } else SectionColumn(model, left + right, Modifier.fillMaxSize(), openNasSettings, openDriveSettings)
    }
}

/** 1 列ぶん。ファイラーだけの列はファイラーが全体を使う。ほかは縦にスクロールし、ファイラーは列の高さぶん。 */
@Composable
private fun SectionColumn(model: HomeModel, list: List<HomeSection>, modifier: Modifier, openNasSettings: () -> Unit, openDriveSettings: () -> Unit) {
    BoxWithConstraints(modifier) {
        val filerHeight = (maxHeight - 24.dp).coerceAtLeast(320.dp)
        // 探しているときは、検索の箱までの区画と結果だけ（検索の無い列はそのまま）
        val upto = list.indexOfFirst { it.id == HomeSection.SEARCH }
        val searching = model.query.isNotBlank() && upto >= 0
        if (!searching && list.size == 1 && list[0].id == HomeSection.FILER) {
            FilerSection(model, Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp), openNasSettings, openDriveSettings)
        } else {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                list.forEachIndexed { i, s ->
                    key(s.id) { if (!searching || i <= upto) SectionView(model, s, filerHeight, openNasSettings, openDriveSettings) }
                    if (searching && i == upto) SearchResults(model)
                }
                if (list.isEmpty()) Note("ホームに出す区画がありません。設定の「HOME / ホームの並び」で選べます。")
                Gap(8.dp)
            }
        }
    }
}

@Composable
private fun SectionView(model: HomeModel, s: HomeSection, filerHeight: Dp, openNasSettings: () -> Unit, openDriveSettings: () -> Unit) {
    when (s.id) {
        HomeSection.CLOCK -> ClockBlock(big = model.profile == "wide")
        HomeSection.SEARCH -> SearchRow(model)
        HomeSection.TODAY -> Panel(model, "TODAY", "予定", folded = {
            val next = model.nextEvent
            if (next != null) Text(dayLabel(next.localBegin) + "  " + next.title, color = LocalPalette.current.muted, fontSize = 13.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }) { TodayEvents(model, max = 12) }
        HomeSection.NOTICE -> NoticePanel(model)
        HomeSection.PINS -> PinsPanel(model)
        HomeSection.MUSIC -> MusicPanel(model)
        HomeSection.BOOKS -> BooksPanel(model)
        HomeSection.WIDGETS -> HomeWidgets(model)
        HomeSection.WEATHER -> Panel(model, "WEATHER", "天気", folded = {
            val f = model.forecast
            val warn = model.alerts?.list.orEmpty().joinToString("・") { it.name }
            if (f != null) Text("${f.temp.roundToInt()}°  ${Weather.label(f.code)}" + (if (warn.isNotEmpty()) "  ⚠ $warn" else ""),
                color = if (warn.isNotEmpty()) LocalPalette.current.accent else LocalPalette.current.muted, fontSize = 13.sp, maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }) { WeatherPanel(model) }
        HomeSection.STATUS -> StatusStrip(model, openNasSettings)
        HomeSection.QUICK -> QuickSection(model)
        HomeSection.FAVPEOPLE -> Panel(model, "PEOPLE", "よく連絡する人", folded = {
            Text(model.favPeople.joinToString("・") { f -> f.contactKey?.let { model.personByKey(it)?.name } ?: f.label }.ifEmpty { "＋ で足す" },
                color = LocalPalette.current.muted, fontSize = 13.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }) { FavPeopleRow(model) }
        HomeSection.FILES -> Panel(model, "FILES", "ファイル") { FilesPanel(model) }
        HomeSection.FILER -> FilerSection(model, Modifier.fillMaxWidth().height(filerHeight), openNasSettings, openDriveSettings)
        HomeSection.DEVICE -> Panel(model, "DEVICE", "端末") { DevicePanel() }
    }
}

/** ホームの区画としてのファイラー：枠の中に場所・フォルダ・下の帯。 */
@Composable
private fun FilerSection(model: HomeModel, modifier: Modifier, openNasSettings: () -> Unit, openDriveSettings: () -> Unit) {
    val p = LocalPalette.current
    // 枠は作りに合わせる（ほかの区画と同じ見た目の見出し）
    val shape = if (p.pop) androidx.compose.foundation.shape.RoundedCornerShape(p.radius) else androidx.compose.ui.graphics.RectangleShape
    val framed = when (p.style) {
        "soft", "braun" -> modifier.popShadow(p, shape).clip(shape).background(p.panel)
        "window", "pop" -> modifier.popShadow(p, shape).clip(shape).background(p.panel).border(lineWidth, p.line, shape)
        "lcd" -> modifier.border(2.dp, p.line).background(p.panel)
        else -> modifier.border(1.dp, p.line)
    }
    Column(framed) {
        when (p.style) {
            "window" -> WindowBar("Files")
            "term" -> Text("FILER  ファイラー", color = p.bg, fontSize = 13.sp, fontFamily = Hud.mono, modifier = Modifier.fillMaxWidth().background(p.fg).padding(horizontal = 10.dp, vertical = 4.dp))
            "hud" -> Row(Modifier.padding(start = 10.dp, end = 10.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("[ FILER ]", color = p.accent, fontSize = 11.sp, fontFamily = Hud.mono, letterSpacing = 2.sp)
                Text("ファイラー", color = p.muted, fontSize = 11.sp)
                Stripes(Modifier.weight(1f).height(5.dp), color = p.line)
            }
            else -> Text(if (p.style == "braun") "FILER" else "ファイラー", color = p.faint, fontSize = 12.sp, modifier = Modifier.padding(start = 14.dp, top = 12.dp))
        }
        FilerBody(model, Modifier.weight(1f).fillMaxWidth().padding(10.dp), openNasSettings, openDriveSettings)
    }
}

/** 再生中の音楽の区画。再生していなくて、Theta Music も入っていなければ出さない。 */
@Composable
private fun MusicPanel(model: HomeModel) {
    val now by MediaWatch.now.collectAsState()
    val hasMusicApp = model.apps.any { it.packageName in MediaWatch.THETA_MUSIC_IDS }
    if (now == null && !hasMusicApp) return
    Panel(model, "MUSIC", "音楽", folded = {
        now?.let { n -> Text((if (n.playing) "▶ " else "❚❚ ") + listOf(n.title, n.artist).filter { it.isNotBlank() }.joinToString(" — "),
            color = LocalPalette.current.muted, fontSize = 13.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
    }) { MusicCard(model) }
}

/**
 * すぐ記録：MemoHatch の入力の段と同じ 5 つ（文字・チェックリスト・手書き・声・写真）を絵だけで。押すと MemoHatch でそのメモを始める。
 * MemoHatch が入っていなければ、このホームの機能（入力・録音と文字起こし・手書き）で代わりにする。写真は長押しで名刺・チラシ。
 */
@Composable
private fun QuickSection(model: HomeModel) {
    val p = LocalPalette.current
    val actions = LocalActions.current
    val ctx = actions.activity
    val recording by app.thetahatch.home.capture.RecordingService.startedAt.collectAsState()
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var wasRecording by remember { mutableStateOf(false) }
    var inking by remember { mutableStateOf(false) }
    var shootMenu by remember { mutableStateOf(false) }
    LaunchedEffect(recording) {
        if (recording != null) wasRecording = true
        while (recording != null) { now = System.currentTimeMillis(); delay(1000) }
        model.refreshCaptures()
        // ここで止めた録音は、文字起こしして入力を開く
        if (wasRecording) {
            wasRecording = false
            app.thetahatch.home.capture.RecordingService.lastFile.value?.let { model.transcribeAndOpen(it) }
        }
    }
    fun record() {
        if (recording != null) { app.thetahatch.home.capture.RecordingService.stop(ctx); return }
        val need = buildList {
            if (!model.has(Manifest.permission.RECORD_AUDIO)) add(Manifest.permission.RECORD_AUDIO)
            if (android.os.Build.VERSION.SDK_INT >= 33 && !model.has(Manifest.permission.POST_NOTIFICATIONS)) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (need.isEmpty()) app.thetahatch.home.capture.RecordingService.start(ctx)
        else actions.requestPermissions(need.toTypedArray()) {
            if (model.has(Manifest.permission.RECORD_AUDIO)) app.thetahatch.home.capture.RecordingService.start(ctx) else actions.toast("録音にはマイクの許可が必要です")
        }
    }
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(if (p.pop) p.radius else 0.dp)
    // 絵だけのボタン。読み上げ・長押しの説明は desc
    @Composable
    fun androidx.compose.foundation.layout.RowScope.Big(glyph: Glyph, on: Boolean, desc: String, extra: String? = null, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
        val bg = if (on) p.accent2 else p.accent
        Box(
            Modifier.weight(1f).height(52.dp).popShadow(p, shape).clip(shape).background(bg)
                .combinedClickable(onClickLabel = desc, onClick = onClick, onLongClick = onLongClick, onLongClickLabel = if (onLongClick != null) "名刺・チラシ" else null)
                .semantics { contentDescription = desc },
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(glyph, p.onAccent, 24.dp)
            extra?.let { Text(it, color = p.onAccent, fontSize = 10.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp)) }
        }
    }
    // MemoHatch の入力の段（打つ・チェック・手書き・声・写真）と同じ並び。MemoHatch が無ければこのホームの機能で
    fun memo(type: String, fallback: () -> Unit) { if (!model.memoNew(type, actions.start)) fallback() }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Big(Glyph.FILE, false, "文字のメモ（MemoHatch）") { memo("text") { model.quickOpen = "text" } }
        Big(Glyph.LIST, false, "チェックリスト（MemoHatch）") { memo("list") { model.quickOpen = "text" } }
        Big(Glyph.PENCIL, false, "手書き（MemoHatch）") { memo("ink") { inking = true } }
        val rec = recording
        Big(Glyph.MIC, rec != null || model.transcribing, if (rec != null) "録音を止めて文字起こし" else "声のメモ（MemoHatch）",
            extra = when { rec != null -> "${(now - rec) / 1000}s"; model.transcribing -> "…"; else -> null }) {
            if (rec != null) record() else memo("audio") { if (!model.transcribing) record() }
        }
        Box(Modifier.weight(1f)) {
            Row { Big(Glyph.CAMERA, false, "写真のメモ（MemoHatch）。長押しで名刺・チラシ", onLongClick = { shootMenu = true }) { memo("photo") { model.quickOpen = "photo" } } }
            // 長押し：名刺（Meishi）・チラシ（Chirashi）を撮る、それぞれを開く
            androidx.compose.material3.DropdownMenu(expanded = shootMenu, onDismissRequest = { shootMenu = false }) {
                listOf("meishi" to "名刺", "chirashi" to "チラシ").forEach { (id, what) ->
                    model.scanIntent(id)?.let { i -> androidx.compose.material3.DropdownMenuItem(text = { Text("📷 ${what}を撮って登録") }, onClick = { shootMenu = false; actions.start(i) }) }
                    model.hatchLaunch(id)?.let { i -> androidx.compose.material3.DropdownMenuItem(text = { Text(if (id == "meishi") "Meishi を開く" else "Chirashi を開く") }, onClick = { shootMenu = false; actions.start(i) }) }
                }
                if (model.hatchLaunch("meishi") == null && model.hatchLaunch("chirashi") == null)
                    androidx.compose.material3.DropdownMenuItem(text = { Text("Meishi・Chirashi が入っていません") }, onClick = { shootMenu = false })
            }
        }
    }
    if (inking) InkDialog(onDismiss = { inking = false }) { file -> inking = false; model.refreshCaptures(); actions.toast("保存しました：${file.name}") }
}

/**
 * 本：Theta Book Hatch の棚（読みかけ・お気に入り・最近など）を表紙で横に並べ、押すとその本を開く。
 * Book が入っていない・棚に対応していない・何も無いときは出さない。
 */
@Composable
private fun BooksPanel(model: HomeModel) {
    val ctx = LocalActions.current.activity
    val actions = LocalActions.current
    val book = app.thetahatch.home.hub.Catalog.apps.collectAsState().value.firstOrNull { it.id == "book" } ?: return
    val pkg = book.installedPackage(app.thetahatch.home.hub.UpdateHub.versionOf(ctx)) ?: return
    val items by androidx.compose.runtime.produceState<List<app.thetahatch.home.hub.HatchShelf.Item>?>(null, pkg, model.resumeTick) {
        value = withContext(Dispatchers.IO) { app.thetahatch.home.hub.HatchShelf.read(ctx, pkg) }
    }
    val list = items
    if (list.isNullOrEmpty()) return
    Panel(model, "BOOKS", "本", folded = {
        Text(list.take(3).joinToString("・") { it.title }, color = LocalPalette.current.muted, fontSize = 13.sp, maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            list.groupBy { it.sectionTitle.ifEmpty { it.section } }.forEach { (title, l) ->
                if (title.isNotEmpty()) Text(title, color = LocalPalette.current.faint, fontSize = 11.sp)
                ShelfStrip(pkg, l, 72.dp, tall = true)
            }
            FlowRow2 { PillButton("${book.nickname ?: book.name} を開く", { ctx.packageManager.getLaunchIntentForPackage(pkg)?.let(actions.start) }) }
        }
    }
}

/**
 * ホームに置いたウィジェット・アプリ・ショートカット。タブと同じ仕組み（置き場所 [HomeModel.HOME_BOARD]）。
 * 何も置いていなければ「＋ ホームに置く」の 1 行だけ。⚙ で編集（高さ・半分・並べ替え・外す・足す）。
 */
@Composable
private fun HomeWidgets(model: HomeModel) {
    val p = LocalPalette.current
    var editing by remember { mutableStateOf(false) }
    var placing by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    val id = HomeModel.HOME_BOARD
    val tab = remember { app.thetahatch.home.data.TabSpec(id, app.thetahatch.home.data.TabKind.BOARD, "HOME") }
    val empty = model.boardItems(id).isEmpty() && model.opts(id).apps.isEmpty() && model.shortcuts.none { it.catId == "tab:$id" }
    if (placing) TabAppsDialog(model, id) { placing = false }
    if (adding) AddItemDialog(model, id) { adding = false }
    if (empty && !editing) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("ホームに置く：", color = p.faint, fontSize = 12.sp)
            MiniButton("＋ ウィジェットなど", "ウィジェット・フォルダ・予定などをホームに置く") { adding = true }
            MiniButton("＋ アプリ", "アプリ・ショートカットをホームに置く") { placing = true }
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        TabAppsStrip(model, id)
        BoardDrawer(model, tab, editing, inline = true)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Spacer(Modifier.weight(1f))
            if (editing) {
                MiniButton("＋ アプリ", "アプリ・ショートカットをホームに置く") { placing = true }
                MiniButton("＋ ウィジェットなど", "ウィジェットなどを足す") { adding = true }
            }
            MiniButton(if (editing) "✓" else "⚙", if (editing) "編集を終える" else "ホームのウィジェット・アプリを編集") { editing = !editing }
        }
    }
}

/** ピン留めしたファイルの区画。留めたものが無ければ出さない。押すと開く。 */
@Composable
private fun PinsPanel(model: HomeModel) {
    val pins = model.pinnedFiles
    if (pins.isEmpty()) return
    val p = LocalPalette.current
    val actions = LocalActions.current
    Panel(model, "PINNED", "ピン留めのファイル", folded = {
        Text(pins.joinToString("・") { it.name }, color = p.muted, fontSize = 13.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }) {
        Column {
            pins.take(8).forEachIndexed { i, f ->
                RowButton(zebra = i, onClick = { openPinned(model, actions, f) }, onLongClick = { model.togglePinFile(f) }, label = "開く（長押しで外す）") {
                    Badge(f.name.substringAfterLast('.', if (f.source == "drive") "DOC" else "FILE").uppercase(java.util.Locale.ROOT).take(4))
                    TwoLines(f.name, listOf(when (f.source) { "nas" -> "NAS"; "drive" -> "DRIVE"; else -> "" }, f.where).filter { it.isNotEmpty() }.distinct().joinToString(" · ").ifEmpty { null }, Modifier.weight(1f))
                }
            }
            if (pins.size > 8) Text("ほか ${pins.size - 8} 件 ›", color = p.accent, fontSize = 12.sp, fontFamily = Hud.mono,
                modifier = Modifier.clickable { model.showPinned() }.padding(vertical = 6.dp))
        }
    }
}

/** 条件に合う通知の区画。条件が無い・合う通知が無いときは出さない（通知へのアクセスが無ければ許可を促す）。 */
@Composable
private fun NoticePanel(model: HomeModel) {
    val picked = filteredNotices(model)
    val enabled = HatchNotificationListener.isEnabled(LocalActions.current.activity)
    if (model.noticeRules.isEmpty() || (picked.isEmpty() && enabled)) return
    Panel(model, "NOTICE", "通知", folded = { FilteredNoticesFolded(picked) }) { FilteredNotices(model, picked, max = 6) }
}

/**
 * 計器の区画：見出し（英字＋日本語）と、四隅のかぎ括弧。POP では角丸の枠に、色の付いた見出しの帯。
 * 見出しを押すとたたむ（たたんだら folded の 1 行だけ）。たたんだのは覚えておく。
 */
@Composable
private fun Panel(model: HomeModel, code: String, title: String, folded: (@Composable () -> Unit)? = null, body: @Composable () -> Unit) {
    val p = LocalPalette.current
    val closed = model.isCollapsed(code)
    val toggle = Modifier.clickable(onClickLabel = if (closed) "広げる" else "たたむ") { model.toggleCollapsed(code) }
    val band = when (code) {
        "TODAY" -> p.accent
        "WEATHER" -> p.accent3
        "FILES" -> p.accent2
        else -> p.raised
    }
    SectionFrame(code, title, toggle, if (closed) "▸" else "▾", band, showBody = !closed || folded != null) {
        if (closed) folded?.invoke() else body()
    }
}

// ---- 天気 ----

@Composable
private fun WeatherPanel(model: HomeModel) {
    val p = LocalPalette.current
    val actions = LocalActions.current
    var choosing by remember { mutableStateOf(false) }
    val place = model.weatherPlace
    val f = model.forecast
    if (place == null) {
        Text("天気を出す場所を選んでください（Open-Meteo の予報。API キーは要りません）", color = p.muted, fontSize = 13.sp, lineHeight = 19.sp)
        FlowRow2 {
            PillButton("現在地を使う", {
                actions.requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                    if (model.has(Manifest.permission.ACCESS_COARSE_LOCATION)) model.chooseWeatherPlace(WeatherPlace("現在地", 35.68, 139.76, useLocation = true))
                    else actions.toast("位置の許可が無いので、地名で選んでください")
                }
            }, primary = true)
            PillButton("地名で探す", { choosing = true })
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            WeatherGlyph(f?.code ?: 3, 40.dp, p.accent, night = f?.isDay == false)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(f?.let { "${it.temp.roundToInt()}°" } ?: "--°", color = p.fg, fontSize = 32.sp, fontFamily = Hud.display, fontWeight = FontWeight.Light, lineHeight = 36.sp)
                    Text(f?.let { Weather.label(it.code) } ?: "", color = p.fg, fontSize = 14.sp, modifier = Modifier.padding(bottom = 6.dp))
                }
                if (f != null) Text(
                    "体感 ${f.feels.roundToInt()}° · 湿度 ${f.humidity}% · 風 ${"%.0f".format(f.wind)}m/s" +
                        (f.days.firstOrNull()?.let { " · ${it.max.roundToInt()}°/${it.min.roundToInt()}° · 雨 ${it.rain}%" } ?: ""),
                    color = p.muted, fontSize = 11.sp, fontFamily = Hud.mono, lineHeight = 15.sp,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("◎ " + place.name, color = p.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).clickable(onClickLabel = "場所を変える") { choosing = true })
            Text(
                when {
                    model.weatherLoading -> "UPDATING…"
                    f != null -> "UPD " + SimpleDateFormat("HH:mm", Locale.US).format(Date(f.fetchedAt))
                    else -> ""
                },
                color = p.faint, fontSize = 10.sp, fontFamily = Hud.mono,
                modifier = Modifier.clickable(onClickLabel = "今すぐ取り直す") { model.refreshWeather(force = true) },
            )
        }
        model.weatherError?.let { Text(it, color = p.accent, fontSize = 12.sp) }
        AlertChips(model)
        // ふだんは 3 日分を 1 行だけ。24 時間と 7 日は「詳しく」で（場所を取りすぎるので）
        if (f != null && !model.weatherDetail) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                f.days.drop(1).take(3).forEach { d ->
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(SimpleDateFormat("E", Locale.JAPAN).format(Date(d.date)), color = p.muted, fontSize = 12.sp)
                        WeatherGlyph(d.code, 16.dp, p.fg)
                        Text("${d.max.roundToInt()}/${d.min.roundToInt()}", color = p.fg, fontSize = 12.sp, fontFamily = Hud.mono, maxLines = 1)
                        Text("${d.rain}%", color = if (d.rain >= 50) p.accent else p.muted, fontSize = 11.sp, fontFamily = Hud.mono, maxLines = 1)
                    }
                }
                Text("詳しく ▾", color = p.faint, fontSize = 12.sp, modifier = Modifier.clickable { model.changeWeatherDetail(true) }.padding(4.dp))
            }
        }
        if (f != null && model.weatherDetail) {
            Text("たたむ ▴", color = p.faint, fontSize = 12.sp, modifier = Modifier.clickable { model.changeWeatherDetail(false) }.padding(vertical = 2.dp))
            // 24 時間：時刻・絵・気温・降水確率
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                f.hours.forEach { h ->
                    Column(Modifier.width(40.dp).background(p.raised).padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(SimpleDateFormat("HH", Locale.US).format(Date(h.time)), color = p.faint, fontSize = 10.sp, fontFamily = Hud.mono)
                        WeatherGlyph(h.code, 20.dp, p.fg)
                        Text("${h.temp.roundToInt()}°", color = p.fg, fontSize = 12.sp, fontFamily = Hud.mono)
                        Text("${h.rain}%", color = if (h.rain >= 50) p.accent else p.muted, fontSize = 10.sp, fontFamily = Hud.mono)
                    }
                }
            }
            // 7 日
            Column {
                f.days.forEachIndexed { i, d ->
                    Row(
                        Modifier.fillMaxWidth().background(if (i % 2 == 1) p.zebra else Color.Transparent).padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            if (i == 0) "TODAY" else SimpleDateFormat("MM/dd EEE", Locale.US).format(Date(d.date)).uppercase(Locale.US),
                            color = if (i == 0) p.accent else p.fg, fontSize = 12.sp, fontFamily = Hud.mono, modifier = Modifier.width(90.dp),
                        )
                        WeatherGlyph(d.code, 18.dp, p.fg)
                        Text("${d.max.roundToInt()}° / ${d.min.roundToInt()}°", color = p.fg, fontSize = 12.sp, fontFamily = Hud.mono, modifier = Modifier.width(84.dp))
                        Text("${d.rain}%", color = if (d.rain >= 50) p.accent else p.muted, fontSize = 11.sp, fontFamily = Hud.mono, modifier = Modifier.width(34.dp))
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
    if (choosing) PlaceDialog(model) { choosing = false }
}

/** 天気を出す場所：現在地か、地名で探す。 */
@Composable
private fun PlaceDialog(model: HomeModel, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    val actions = LocalActions.current
    val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<WeatherPlace>>(emptyList()) }
    var msg by remember { mutableStateOf<String?>(null) }
    fun search() {
        if (q.isBlank()) return
        msg = "探しています…"
        scope.launch {
            val r = runCatching { withContext(Dispatchers.IO) { Weather.search(q) } }
            results = r.getOrDefault(emptyList())
            msg = r.exceptionOrNull()?.let { "探せませんでした（${it.message}）" } ?: if (results.isEmpty()) "見つかりませんでした" else null
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("天気の場所") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton("現在地を使う", {
                    actions.requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                        if (model.has(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                            model.chooseWeatherPlace(WeatherPlace("現在地", model.weatherPlace?.lat ?: 35.68, model.weatherPlace?.lon ?: 139.76, useLocation = true))
                            onDismiss()
                        } else actions.toast("位置の許可がありません")
                    }
                }, primary = true)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(q, { q = it }, "地名（例 札幌、横浜市）", Modifier.weight(1f))
                    PillButton("探す", ::search)
                }
                msg?.let { Text(it, color = p.muted, fontSize = 12.sp) }
                LazyColumn(Modifier.heightIn(max = 280.dp)) {
                    items(results) { r ->
                        RowButton(onClick = { model.chooseWeatherPlace(r); onDismiss() }) {
                            TwoLines(r.name, "%.2f, %.2f".format(Locale.US, r.lat, r.lon), Modifier.weight(1f))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } },
    )
}

/** 天気の絵（線だけ）：晴れ・晴れ時々くもり・くもり・霧・雨・雪・雷。 */
@Composable
fun WeatherGlyph(code: Int, size: Dp, color: Color, night: Boolean = false) {
    val kind = Weather.kind(code)
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val st = Stroke(width = w * 0.06f, cap = StrokeCap.Round)
        fun sun(c: Offset, r: Float) {
            if (night) {
                drawCircle(color, r, c, style = st)
                drawCircle(color, r * 0.8f, Offset(c.x + r * 0.55f, c.y - r * 0.35f), style = st)
                return
            }
            drawCircle(color, r, c, style = st)
            for (i in 0 until 8) {
                val a = Math.toRadians(i * 45.0)
                val o1 = Offset(c.x + (r * 1.45f * Math.cos(a)).toFloat(), c.y + (r * 1.45f * Math.sin(a)).toFloat())
                val o2 = Offset(c.x + (r * 1.85f * Math.cos(a)).toFloat(), c.y + (r * 1.85f * Math.sin(a)).toFloat())
                drawLine(color, o1, o2, st.width, StrokeCap.Round)
            }
        }
        fun cloud(dy: Float = 0f) {
            drawCircle(color, w * 0.15f, Offset(w * 0.34f, w * 0.52f + dy), style = st)
            drawCircle(color, w * 0.2f, Offset(w * 0.54f, w * 0.44f + dy), style = st)
            drawCircle(color, w * 0.13f, Offset(w * 0.72f, w * 0.55f + dy), style = st)
            drawLine(color, Offset(w * 0.22f, w * 0.67f + dy), Offset(w * 0.82f, w * 0.67f + dy), st.width, StrokeCap.Round)
        }
        when (kind) {
            "clear" -> sun(Offset(w / 2, w / 2), w * 0.2f)
            "partly" -> { sun(Offset(w * 0.32f, w * 0.3f), w * 0.12f); cloud(w * 0.06f) }
            "cloudy" -> cloud()
            "fog" -> for (i in 0 until 3) drawLine(color, Offset(w * 0.15f, w * (0.35f + i * 0.15f)), Offset(w * 0.85f, w * (0.35f + i * 0.15f)), st.width, StrokeCap.Round)
            "rain" -> { cloud(-w * 0.1f); for (i in 0 until 3) drawLine(color, Offset(w * (0.35f + i * 0.15f), w * 0.7f), Offset(w * (0.3f + i * 0.15f), w * 0.86f), st.width, StrokeCap.Round) }
            "snow" -> { cloud(-w * 0.1f); for (i in 0 until 3) drawCircle(color, w * 0.03f, Offset(w * (0.33f + i * 0.17f), w * 0.8f)) }
            "thunder" -> {
                cloud(-w * 0.1f)
                drawLine(color, Offset(w * 0.52f, w * 0.62f), Offset(w * 0.44f, w * 0.78f), st.width, StrokeCap.Round)
                drawLine(color, Offset(w * 0.44f, w * 0.78f), Offset(w * 0.56f, w * 0.78f), st.width, StrokeCap.Round)
                drawLine(color, Offset(w * 0.56f, w * 0.78f), Offset(w * 0.48f, w * 0.94f), st.width, StrokeCap.Round)
            }
        }
    }
}

// ---- ファイル（ファイラーへの入口） ----

@Composable
private fun FilesPanel(model: HomeModel) {
    val p = LocalPalette.current
    val actions = LocalActions.current
    PlaceChips(model)
    val recent = model.recents.take(3)
    if (recent.isEmpty()) Text("フォルダはタブで開きます（NAS・DRIVE・最近はファイラーで）", color = p.muted, fontSize = 12.sp)
    recent.forEach { r ->
        Row(
            Modifier.fillMaxWidth().clickable { openFile(model, actions, ViewItem(Uri.parse(r.uri), r.name, r.mime)) }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Badge(r.name.substringAfterLast('.', "FILE").uppercase(Locale.ROOT).take(4))
            TwoLines(r.name, r.where, Modifier.weight(1f))
        }
    }
}

// ---- 端末：電池・空き容量 ----

private data class DeviceInfo(val battery: Int, val charging: Boolean, val freeGb: Double, val totalGb: Double)

@Composable
private fun DevicePanel() {
    val p = LocalPalette.current
    val ctx = LocalActions.current.activity
    val info by produceState<DeviceInfo?>(null) {
        while (true) {
            value = withContext(Dispatchers.IO) {
                val b = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                val level = b?.let { it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) * 100 / it.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1) } ?: -1
                val status = b?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                val fs = runCatching { StatFs(Environment.getDataDirectory().path) }.getOrNull()
                DeviceInfo(
                    level,
                    status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL,
                    (fs?.availableBytes ?: 0) / 1e9, (fs?.totalBytes ?: 0) / 1e9,
                )
            }
            delay(60_000)
        }
    }
    val i = info ?: return
    Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
        Readout("BATTERY", if (i.battery >= 0) "${i.battery}%" + if (i.charging) " ⚡" else "" else "--", accent = i.battery in 0..20)
        Readout("STORAGE", "%.0f / %.0f GB".format(i.freeGb, i.totalGb))
        Column(Modifier.weight(1f)) {
            Meter(i.battery / 100f, if (i.battery in 0..20) p.accent else p.fg)
            Gap(4.dp)
            Meter(if (i.totalGb > 0) (1 - i.freeGb / i.totalGb).toFloat() else 0f, p.muted)
        }
    }
}

@Composable
private fun Meter(value: Float, color: Color) {
    val p = LocalPalette.current
    Box(Modifier.fillMaxWidth().height(6.dp).border(1.dp, p.line)) {
        Box(Modifier.fillMaxWidth(value.coerceIn(0f, 1f)).height(6.dp).background(color))
    }
}

/**
 * 気象庁の警報・注意報。特別警報・警報は警告の色の地、注意報は枠だけ。何も出ていなければ「なし」と小さく。
 * 日本の外・取れないときは何も出さない。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AlertChips(model: HomeModel) {
    val p = LocalPalette.current
    val actions = LocalActions.current
    val a = model.alerts ?: return
    // 色はテーマに依らず、気象庁の色分けに近く：特別警報＝紫、警報＝赤、注意報＝黄（明るい地では濃いめ）
    val dark = p.isDark
    fun colorOf(level: Int) = when (level) {
        3 -> if (dark) Color(0xFFC060F0) else Color(0xFF7B1FA2)
        2 -> if (dark) Color(0xFFFF3B3B) else Color(0xFFC62828)
        else -> if (dark) Color(0xFFF2C200) else Color(0xFF8A6D00)
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("JMA", color = p.faint, fontSize = 10.sp, fontFamily = Hud.mono, letterSpacing = 1.sp)
        Text(a.area, color = p.muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (a.list.isEmpty()) Text("警報・注意報なし", color = p.faint, fontSize = 11.sp)
    }
    if (a.list.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        a.list.forEach { al ->
            val strong = al.level >= 2
            val warn = colorOf(al.level)
            Text(
                (if (al.level >= 3) "▲ " else "") + al.name,
                color = if (strong) Color.White else warn, fontSize = 12.sp, fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.background(if (strong) warn else Color.Transparent).border(1.dp, warn)
                    // 押すと気象庁の警報・注意報のページ
                    .clickable(onClickLabel = "気象庁のページを開く") { actions.start(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://www.jma.go.jp/bosai/warning/"))) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}
