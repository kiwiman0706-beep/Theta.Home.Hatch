package jp.yaegaki.theta.home.ui

import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.Text
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import jp.yaegaki.theta.home.HomeModel
import jp.yaegaki.theta.home.data.TabKind
import jp.yaegaki.theta.home.data.BoardItem
import jp.yaegaki.theta.home.data.TabSpec

/** 端末の画面幅から決める並べ方。機種名ではなく使える幅で決める。 */
data class Layout(
    val compact: Boolean,
    val drawerWidth: Dp,
    val maxDocked: Int,
    val wideHome: Boolean,
)

/** 画面の外（他のアプリ・許可画面・ファイル選択）を開く操作。 */
class Actions(
    val activity: ComponentActivity,
    val start: (Intent) -> Unit,
    val pickFolder: (tabId: String) -> Unit,
    val requestPermissions: (Array<String>, () -> Unit) -> Unit,
    val takePicture: (Uri, (Boolean) -> Unit) -> Unit,
    val captureVideo: (Uri, (Boolean) -> Unit) -> Unit,
    val export: (source: Uri, name: String, mime: String?) -> Unit,
    val forResult: (Intent, (Boolean) -> Unit) -> Unit,
    /** ファイルを作る場所を選ぶ（書き出し用）。 */
    val createDocument: (name: String, mime: String, (Uri) -> Unit) -> Unit,
    /** ファイルを選ぶ（読み込み用）。 */
    val openDocument: (mimes: Array<String>, (Uri) -> Unit) -> Unit,
    val toast: (String) -> Unit,
)

val LocalActions = staticCompositionLocalOf<Actions> { error("Actions not provided") }

/** 端のタブが左か（ホームは設定、エッジはエッジの側）。 */
val LocalRailLeft = staticCompositionLocalOf { false }

/** エッジ（ほかのアプリの上）で開いているときの「閉じる」。ホームでは null。 */
val LocalEdgeExit = staticCompositionLocalOf<(() -> Unit)?> { null }
val LocalLayout = staticCompositionLocalOf { Layout(true, 360.dp, 0, false) }

private const val SETTINGS = "__settings"

/**
 * ホーム。edgeExit を渡すとエッジ（ほかのアプリの上に重ねる画面）として、背景のファイラーを出さずに、
 * 端のタブとドロワーだけを透けた画面に出す。タブ・中身はホームと同じもの。
 */
@Composable
fun HomeRoot(model: HomeModel, activity: ComponentActivity, edgeExit: (() -> Unit)? = null) {
    val edge = edgeExit != null
    // エッジでは、何も開いていなければ戻るで閉じる（ほかの戻るより後回し）
    BackHandler(enabled = edge) { edgeExit?.invoke() }
    val actions = rememberActions(model, activity, edgeExit)
    // Google ドライブの許可の画面
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        model.driveConsentResult(it.resultCode == android.app.Activity.RESULT_OK, it.data)
    }
    LaunchedEffect(model.driveConsent) {
        val pi = model.driveConsent ?: return@LaunchedEffect
        model.driveConsent = null
        runCatching { consent.launch(androidx.activity.result.IntentSenderRequest.Builder(pi.intentSender).build()) }
    }
    val p = LocalPalette.current

    val here = model.place(model.location.removePrefix("tab:"))
    val canGoUp = model.location == "nas" && model.nasState.path.isNotEmpty() ||
        model.location == "drive" && model.driveState.stack.size > 1 ||
        here != null && (model.folders[here.id]?.stack?.size ?: 0) > 1
    // 狭い画面では、いちばん上のフォルダで戻るとファイラーを閉じてダッシュボードへ
    val closeFiler = model.filerOpen && !model.filerOnHome()
    BackHandler(enabled = model.open != null || model.settingsOpen || model.query.isNotEmpty() || canGoUp || closeFiler) {
        when {
            model.settingsOpen -> model.settingsOpen = false
            model.open != null -> model.close()
            model.query.isNotEmpty() -> model.query = ""
            model.location == "nas" && model.nasState.path.isNotEmpty() -> model.nasUp()
            model.location == "drive" && model.driveState.stack.size > 1 -> model.driveUp()
            here != null && (model.folders[here.id]?.stack?.size ?: 0) > 1 -> model.up(here.id)
            closeFiler -> model.filerOpen = false
        }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(if (edge) Color.Black.copy(alpha = 0.3f) else p.bg)
            // ALERT の走査線・SCI-FI の方眼
            .then(if (edge) Modifier else Modifier.backdrop(p))
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        val railLeft = if (edge) model.edgeSide != "right" else model.railLeft
        val railW = 46.dp
        val w = maxWidth
        val compact = w < 600.dp
        val layout = Layout(
            compact = compact,
            drawerWidth = when {
                compact -> (w - railW - 20.dp).coerceAtMost(400.dp)
                w < 900.dp -> 360.dp
                else -> 420.dp
            },
            // 並べて出すと、残りのホームが 280dp 以上残る枚数まで（エッジでは並べない）
            maxDocked = when {
                edge -> 0
                w >= 1180.dp -> 2
                w >= 690.dp -> 1
                else -> 0
            },
            wideHome = w >= 720.dp,
        )
        LaunchedEffect(w >= 600.dp) { model.changeProfile(if (w >= 600.dp) "wide" else "narrow") }
        val available = w - railW
        CompositionLocalProvider(
            LocalActions provides actions, LocalLayout provides layout, LocalIconLook provides IconLook(model.iconMode, model.iconVersion, model.iconColors),
            LocalRailLeft provides railLeft, LocalEdgeExit provides edgeExit,
        ) {
            val docked = model.docked.takeLast(layout.maxDocked)
            // 両側のときは、タブごとに決めた側へ（エッジは片側だけ）
            val wantBoth = !edge && model.railsBoth()
            // START の無い側にタブが 1 つも無ければ、その側の端は出さない
            val both = wantBoth && model.tabs.any { model.shownHere(it.id) && model.tabOnLeft(it.id) != railLeft }
            fun onLeft(id: String) = if (both) model.tabOnLeft(id) else railLeft
            Row(Modifier.fillMaxSize()) {
                if (railLeft || both) CompositionLocalProvider(LocalRailLeft provides true) { Rail(model, railW, main = railLeft, filter = if (both) ({ onLeft(it) }) else null) }
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    Row(Modifier.fillMaxSize()) {
                        CompositionLocalProvider(LocalRailLeft provides true) {
                            docked.filter { onLeft(it) }.forEach { DockedDrawer(model, it, drawerWidth(model, it, layout, available), available) }
                        }
                        Box(Modifier.weight(1f).fillMaxHeight()) {
                            // エッジでは背景は透けたまま。押すと閉じる
                            if (edge) Box(Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClickLabel = "閉じる") { edgeExit?.invoke() })
                            else Filer(model)
                        }
                        CompositionLocalProvider(LocalRailLeft provides false) {
                            docked.filter { !onLeft(it) }.forEach { DockedDrawer(model, it, drawerWidth(model, it, layout, available), available) }
                        }
                    }
                    // 開いたドロワーは、そのタブの側から出す（設定は START の側）
                    // 閉じるときも出てきた側へしまうよう、最後に開いていた側を覚えておく
                    var lastFrom by remember { mutableStateOf(railLeft) }
                    if (model.settingsOpen) lastFrom = railLeft else model.open?.let { lastFrom = onLeft(it) }
                    CompositionLocalProvider(LocalRailLeft provides lastFrom) { Overlay(model, layout, available) }
                }
                if (!railLeft || both) CompositionLocalProvider(LocalRailLeft provides false) { Rail(model, railW, main = !railLeft, filter = if (both) ({ !onLeft(it) }) else null) }
            }
            model.importReport?.let { r -> ImportReportDialog(model, r) { model.importReport = null } }
            // 中で開いたファイル（いちばん上に重ねる）
            ViewerOverlay(model)
            QuickInput(model)
            // 初めて入れたときの案内（エッジでは出さない）
            if (!edge) WelcomeDialog(model)
        }
    }
}

/** ドロワーの幅：この画面で決めた幅（なければ決まった幅）。 */
private fun drawerWidth(model: HomeModel, id: String?, layout: Layout, available: Dp): Dp {
    val max = available.coerceAtLeast(240.dp)
    return ((id?.let { model.liveDrawer[it] ?: model.tabWidth(it) })?.dp ?: layout.drawerWidth).coerceIn(240.dp, max)
}

@Composable
private fun DockedDrawer(model: HomeModel, id: String, width: Dp, available: Dp) {
    if (model.tab(id) == null) return
    val p = LocalPalette.current
    Box(Modifier.width(width).fillMaxHeight().background(p.panel)) {
        DrawerFrame(model, id, docked = true)
        WidthHandle(model, id, width, available, Modifier.align(if (LocalRailLeft.current) Alignment.CenterEnd else Alignment.CenterStart))
    }
}

/**
 * ドロワーの内側の端の取っ手。なぞると幅が変わり、この画面（閉じた／開いた）の幅として覚える。
 * 2 回押すと決まった幅に戻す。
 */
@Composable
private fun WidthHandle(model: HomeModel, id: String, width: Dp, available: Dp, modifier: Modifier) {
    val p = LocalPalette.current
    val density = LocalDensity.current
    // 取っ手はドロワーの内側（画面の中央寄り）。右から出るドロワーは左へなぞると広がる
    val left = LocalRailLeft.current
    val grow = if (left) 1f else -1f
    Box(
        modifier
            .width(16.dp)
            .height(72.dp)
            .pointerInput(id, left) {
                var start = 0f
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { start = width.value; total = 0f },
                    onDragEnd = {
                        model.liveDrawer[id]?.let { model.setTabWidth(id, it) }
                        model.liveDrawer.remove(id)
                    },
                    onDragCancel = { model.liveDrawer.remove(id) },
                ) { change, d ->
                    change.consume()
                    total += d
                    val dp = start + grow * with(density) { total.toDp().value }
                    model.liveDrawer[id] = dp.toInt().coerceIn(240, available.value.toInt().coerceAtLeast(240))
                }
            }
            .pointerInput(id) { detectTapGestures(onDoubleTap = { model.setTabWidth(id, null) }) }
            .semantics { contentDescription = "なぞってドロワーの幅を変える（2 回押すと元の幅）" },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.width(3.dp).height(40.dp).background(if (model.liveDrawer[id] != null) p.accent else p.line))
    }
}

@Composable
private fun Overlay(model: HomeModel, layout: Layout, available: Dp) {
    val p = LocalPalette.current
    val visibleId = if (model.settingsOpen) SETTINGS else model.open
    // 閉じるアニメーションの間も中身を描けるよう、最後に開いたものを覚えておく
    var shown by remember { mutableStateOf<String?>(null) }
    if (visibleId != null) shown = visibleId
    val fromRight = !LocalRailLeft.current
    val edgeExit = LocalEdgeExit.current

    AnimatedVisibility(visible = visibleId != null, enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClickLabel = "閉じる") {
                    // エッジでは外側を押すとエッジごと閉じる
                    if (edgeExit != null) edgeExit() else { model.close(); model.settingsOpen = false }
                },
        )
    }
    // ふつうのタブは中身の高さだけの小さなパネル（上寄せ）。ほかは縦いっぱい
    val small = shown != SETTINGS && model.tab(shown)?.kind == TabKind.BOARD && !model.boardFills(shown!!)
    Box(Modifier.fillMaxSize(), contentAlignment = when {
        small && fromRight -> Alignment.TopEnd
        small -> Alignment.TopStart
        fromRight -> Alignment.CenterEnd
        else -> Alignment.CenterStart
    }) {
        AnimatedVisibility(
            visible = visibleId != null,
            enter = slideInHorizontally(tween(280)) { if (fromRight) it else -it },
            exit = slideOutHorizontally(tween(240)) { if (fromRight) it else -it },
        ) {
            Box(
                Modifier
                    .width(if (shown == SETTINGS) layout.drawerWidth else drawerWidth(model, shown, layout, available))
                    .then(if (small) Modifier.padding(top = 8.dp).wrapContentHeight() else Modifier.fillMaxHeight())
                    .background(p.panel)
                    .pointerInput(fromRight) {
                        var total = 0f
                        detectHorizontalDragGestures(onDragStart = { total = 0f }, onDragEnd = {
                            val px = 80.dp.toPx()
                            if (fromRight && total > px || !fromRight && total < -px) {
                                model.close(); model.settingsOpen = false
                            }
                        }) { _, d -> total += d }
                    },
            ) {
                val id = shown
                if (id == SETTINGS) {
                    SettingsPanel(model)
                } else if (id != null) {
                    DrawerFrame(model, id, docked = false)
                    WidthHandle(model, id, drawerWidth(model, id, layout, available), available, Modifier.align(if (fromRight) Alignment.CenterStart else Alignment.CenterEnd))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Rail(model: HomeModel, width: Dp, main: Boolean = true, filter: ((String) -> Boolean)? = null) {
    val p = LocalPalette.current
    val density = LocalDensity.current
    val left = LocalRailLeft.current
    Column(
        Modifier
            .width(width)
            .fillMaxHeight()
            .background(p.bg)
            .pointerInput(left) {
                // 画面の端から内側へなぞると、最後に開いたドロワーを出す
                var total = 0f
                detectHorizontalDragGestures(onDragStart = { total = 0f }, onDragEnd = {
                    val px = with(density) { 48.dp.toPx() }
                    if (!left && total < -px || left && total > px) model.openLast()
                }) { _, d -> total += d }
            },
        horizontalAlignment = if (left) Alignment.Start else Alignment.End,
    ) {
    Column(
        Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 12.dp, bottom = 8.dp),
        horizontalAlignment = if (left) Alignment.Start else Alignment.End,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        var lastGroup = -1
        val reorder = rememberReorderState()
        // この画面（閉じた／開いた）では出さないタブは飛ばす
        model.tabs.filter { model.shownHere(it.id) && (filter == null || filter(it.id)) }.forEach { t ->
            val group = model.groupOf(t)
            if (lastGroup != -1 && group != lastGroup) Spacer(Modifier.height(12.dp))
            lastGroup = group
            RailTab(model, reorder, t.id, t.label, group, active = railActive(model, t), shortcut = shortcut(model, t), left = left)
        }
        var adding by remember { mutableStateOf(false) }
        var addingWidget by remember { mutableStateOf(false) }
        if (main) Box(
            Modifier
                .width(40.dp)
                .height(44.dp)
                .background(p.raised)
                .border(1.dp, p.line)
                .combinedClickable(role = Role.Button, onClick = { adding = true })
                .semantics { contentDescription = "タブを追加" },
            contentAlignment = Alignment.Center,
        ) { GlyphIcon(Glyph.PLUS, p.accent) }
        if (adding) AddTabDialog(model, onWidget = { adding = false; addingWidget = true }) { adding = false }
        if (addingWidget) AddWidgetDialog(model) { addingWidget = false }
    }
    // よく使うアプリ・ショートカット：タブをスクロールしても隠れない、START の上（START の側だけ）
    if (main) {
        RailDock(model)
        StartButton(model)
    }
    }
}

/** 端の下に固定のよく使うアプリ・ショートカット。押すと開く、長押しで並べ替え・外す・選ぶ。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RailDock(model: HomeModel) {
    val p = LocalPalette.current
    val actions = LocalActions.current
    val items = model.dockItems()
    if (items.isEmpty()) return
    var menu by remember { mutableStateOf<String?>(null) }
    var choosing by remember { mutableStateOf(false) }
    // 場所を取らないよう、小さめのマス（34dp）を詰めて並べる
    Column(Modifier.width(40.dp).padding(top = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.width(24.dp).height(1.dp).background(p.line))
        items.forEachIndexed { i, (key, item) ->
            Box {
                Box(
                    Modifier.size(34.dp)
                        .combinedClickable(
                            onClick = { dockLaunch(model, actions, item) },
                            onLongClick = { menu = key },
                            onClickLabel = "開く",
                            onLongClickLabel = "並べ替え・外す",
                        )
                        .semantics { contentDescription = dockLabel(item) },
                    contentAlignment = Alignment.Center,
                ) { DockIcon(model, item, 26.dp) }
                DropdownMenu(expanded = menu == key, onDismissRequest = { menu = null }) {
                    if (i > 0) DropdownMenuItem(text = { Text("▲ 上へ") }, onClick = { model.moveDock(key, -1) })
                    if (i < items.lastIndex) DropdownMenuItem(text = { Text("▼ 下へ") }, onClick = { model.moveDock(key, 1) })
                    DropdownMenuItem(text = { Text("ピン留めを外す") }, onClick = { menu = null; model.toggleDock(key) })
                    DropdownMenuItem(text = { Text("ピン留めするアプリを選ぶ") }, onClick = { menu = null; choosing = true })
                }
            }
        }
    }
    if (choosing) DockDialog(model) { choosing = false }
}

/** 端の下に置くアプリを選ぶ（8 つまで）。ショートカットは「SC」から。 */
@Composable
fun DockDialog(model: HomeModel, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    // "pkg:" で置いたもの（初めの LINE など）は、そのアプリとして選んだことに
    var chosen by remember {
        mutableStateOf(model.dockItems().mapNotNull { (_, it) -> (it as? jp.yaegaki.theta.home.data.AppEntry)?.key })
    }
    var q by remember { mutableStateOf("") }
    var shortcutsFor by remember { mutableStateOf<jp.yaegaki.theta.home.data.AppEntry?>(null) }
    val scCount = model.favoriteApps.count { it.startsWith("sc:") || it.startsWith("act:") }
    val room = HomeModel.DOCK_MAX - scCount
    val list = model.apps.filter { model.appMatches(it, q) }.sortedBy { it.key !in chosen }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ピン留め（${chosen.size + scCount}/${HomeModel.DOCK_MAX}）") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Note("端のタブの下（START の上）と、すべてのアプリの上に並べます。タブをスクロールしても隠れません。「SC」でそのアプリのショートカット（LINE のトークルームなど）も置けます。")
                // 機能：Google の音声検索・MemoHatch の音声メモ
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    HomeModel.DOCK_ACTIONS.forEach { a ->
                        val on = "act:" + a.id in model.favoriteApps
                        PillButton((if (on) "✓ " else "＋ ") + a.label, { model.toggleDock("act:" + a.id) }, primary = on)
                    }
                }
                Field(q, { q = it }, "名前で絞り込み")
                Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState())) {
                    list.forEachIndexed { zi, app ->
                        val on = app.key in chosen
                        RowButton(zebra = zi, onClick = { chosen = if (on) chosen - app.key else if (chosen.size < room) chosen + app.key else chosen }) {
                            androidx.compose.material3.Checkbox(checked = on, onCheckedChange = null)
                            AppIcon(app, 28.dp)
                            Text(app.label, color = p.fg, fontSize = 14.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text("SC", color = p.accent, fontSize = 11.sp, fontFamily = Hud.mono,
                                modifier = Modifier.clickable(onClickLabel = "ショートカットを選ぶ") { shortcutsFor = app }.padding(8.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { model.setDockApps(chosen); onDismiss() }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("やめる") } },
    )
    shortcutsFor?.let { app -> AppShortcutsDialog(model, app, HomeModel.DOCK) { shortcutsFor = null } }
}

/**
 * 端のタブのいちばん下に固定の「START」：すべてのアプリ。タブをスクロールしても隠れず、タブを全部外してもアプリを開ける。
 */
@Composable
private fun StartButton(model: HomeModel) {
    val p = LocalPalette.current
    val on = model.open == model.appsId()
    Column(
        Modifier
            .padding(top = 4.dp, bottom = 10.dp)
            .width(40.dp)
            .background(if (on) p.accent else p.raised)
            .border(1.dp, p.accent)
            .combinedClickable(role = Role.Button, onClickLabel = "すべてのアプリを開く", onLongClick = { model.pressApps(long = true) }, onLongClickLabel = "声でアプリを開く") { model.pressApps(long = false) }
            .semantics { contentDescription = "START すべてのアプリ（Alt+A）"; selected = on }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        GlyphIcon(Glyph.GRID, if (on) p.onAccent else p.accent, size = 16.dp)
        Text("ALL", color = if (on) p.onAccent else p.accent, fontSize = 9.sp, fontFamily = Hud.mono)
    }
}

private fun railActive(model: HomeModel, t: TabSpec) = model.open == t.id || t.id in model.docked

private fun shortcut(model: HomeModel, t: TabSpec) = model.keyOf(t)?.let { "Alt+$it" }
    ?: model.folderTabs().indexOf(t).takeIf { it in 0..8 }?.let { "Alt+${it + 1}" }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RailTab(model: HomeModel, reorder: ReorderState, id: String, label: String, group: Int, active: Boolean, shortcut: String?, left: Boolean) {
    val p = LocalPalette.current
    val groupColor = p.group(group)
    // POP では選んだタブを群の色（フォルダ＝コーラル・ウィジェット＝ミント・起動＝イエロー）で塗り、画面の内側の角を丸める
    val activeBg = if (p.pop) groupColor else p.accent
    val onActive = if (p.pop && group != 0) p.fg else p.onAccent
    val tabShape = if (p.pop) {
        if (left) RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp) else RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)
    } else RoundedCornerShape(0.dp)
    var menu by remember(id) { mutableStateOf(false) }
    var renaming by remember(id) { mutableStateOf(false) }
    var placing by remember(id) { mutableStateOf(false) }
    var iconing by remember(id) { mutableStateOf(false) }
    Box(
        Modifier
            .width(40.dp)
            // 長押し→ドラッグで並べ替え。動かさずに離すとメニュー
            .reorderable(reorder, id, "tab", p.accent, onMenu = { menu = true }) { from, to -> model.moveTabTo(from, to) }
            .background(if (active) activeBg else if (p.pop) p.panel else p.bg, tabShape)
            .border(lineWidth, if (active && !p.pop) p.accent else p.line, tabShape)
            .combinedClickable(role = Role.Tab, onClick = { model.toggle(id) }, onLongClick = {}, onLongClickLabel = "ドラッグで並べ替え・離すとメニュー")
            .semantics {
                contentDescription = label + (shortcut?.let { "（$it）" } ?: "")
                selected = active
            },
    ) {
        Box(
            Modifier
                .align(if (left) Alignment.CenterEnd else Alignment.CenterStart)
                .width(3.dp)
                .height(20.dp)
                .background(if (active) onActive else groupColor),
        )
        // 番号（01・02…）は出さない：印の分だけタブが長くなるので
        Column(Modifier.padding(top = 8.dp, bottom = 10.dp).align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            // 印（中身から自動か、長押し →「印を変える」で選んだもの）。パッと見て分かるように名前の上に
            model.tab(id)?.let(model::tabIcon)?.let { icon ->
                TabIconView(model, icon, if (active) onActive else groupColor)
                Gap(6.dp)
            }
            VerticalLabel(label, if (active) onActive else p.fg)
        }
        TabMenu(model, id, menu, { menu = false }, placeApps = { placing = true }, changeIcon = { iconing = true }) { renaming = true }
    }
    if (renaming) RenameTabDialog(model, id) { renaming = false }
    if (iconing) TabIconDialog(model, id) { iconing = false }
    if (placing) TabAppsDialog(model, id) { placing = false }
}

/** タブの長押し：上へ・下へ・名前・置くアプリ・出す画面・幅・外す。ウィジェットのタブも同じ。 */
@Composable
private fun TabMenu(model: HomeModel, id: String, expanded: Boolean, close: () -> Unit, placeApps: () -> Unit, changeIcon: () -> Unit, rename: () -> Unit) {
    val actions = LocalActions.current
    val t = model.tabs.firstOrNull { it.id == id }
    DropdownMenu(expanded = expanded, onDismissRequest = close) {
        DropdownMenuItem(text = { Text("▲ 上へ") }, onClick = { model.moveTab(id, -1) })
        DropdownMenuItem(text = { Text("▼ 下へ") }, onClick = { model.moveTab(id, 1) })
        if (t != null) {
            DropdownMenuItem(text = { Text("⤒ いちばん上へ") }, onClick = { close(); model.moveTab(id, -model.tabs.indexOf(t)) })
        }
        DropdownMenuItem(text = { Text("名前を変える") }, onClick = { close(); rename() })
        DropdownMenuItem(text = { Text("印（アイコン）を変える") }, onClick = { close(); changeIcon() })
        // 中身がフォルダ 1 つ・ファイル 1 つのタブは、ここからも選び直せる
        val only = model.boardItems(id).singleOrNull()
        if (only?.type == BoardItem.FOLDER) DropdownMenuItem(text = { Text("端末のフォルダを選び直す") }, onClick = { close(); actions.pickFolder(only.id) })
        if (only?.type == BoardItem.FILE) DropdownMenuItem(text = { Text("ファイルを選び直す") }, onClick = {
            close(); actions.openDocument(arrayOf("image/*", "application/pdf")) { model.setTabFile(only.id, it) }
        })
        if (t?.kind != TabKind.CATEGORY && t?.kind != TabKind.APPS) DropdownMenuItem(text = { Text("アプリ・ショートカットを置く") }, onClick = { close(); placeApps() })
        if (t?.kind == TabKind.CATEGORY) {
            val split = model.opts(id).split
            DropdownMenuItem(text = { Text(if (split) "ショートカットをアプリと同じ段に" else "ショートカットを別の段に") }, onClick = { close(); model.setTabSplit(id, !split) })
        }
        // 出す画面：両方・閉じた画面だけ・開いた画面だけ
        val show = model.opts(id).show
        listOf("both" to "両方の画面に出す", "narrow" to "閉じた画面だけに出す", "wide" to "開いた画面だけに出す").forEach { (k, label) ->
            DropdownMenuItem(text = { Text((if (show == k) "● " else "○ ") + label) }, onClick = {
                close()
                model.setTabShow(id, k)
                if (!model.shownHere(id)) actions.toast("この画面では隠しました（「＋」から戻せます）")
            })
        }
        if (model.railsBoth() && LocalEdgeExit.current == null) DropdownMenuItem(text = { Text(if (model.tabOnLeft(id)) "右側へ移す" else "左側へ移す") }, onClick = { close(); model.flipTabSide(id) })
        if (model.tabWidth(id) != null) DropdownMenuItem(text = { Text("幅を元に戻す（この画面）") }, onClick = { close(); model.setTabWidth(id, null) })
        DropdownMenuItem(text = { Text("このタブを外す") }, onClick = {
            close()
            model.removeTab(id)
            actions.toast("外しました（下の「＋」で戻せます）")
        })
    }
}

@Composable
private fun RenameTabDialog(model: HomeModel, id: String, onDismiss: () -> Unit) {
    val t = model.tabs.firstOrNull { it.id == id }
    var name by remember { mutableStateOf(t?.label.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("タブの名前") },
        text = { Field(name, { name = it.take(14) }, "名前") },
        confirmButton = {
            TextButton(onClick = {
                model.renameTab(id, name)
                onDismiss()
            }, enabled = name.isNotBlank()) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("やめる") } },
    )
}

/**
 * 端のタブの「＋」：ふつうのタブ（空・ウィジェット・フォルダ・ページ・ファイル・予定や通知など、中身を 1 つ入れて作る）と、
 * アプリのカテゴリーのタブ。ふつうのタブはあとから中身を足せる。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddTabDialog(model: HomeModel, onWidget: () -> Unit, onDismiss: () -> Unit) {
    val actions = LocalActions.current
    val usedCats = model.tabs.filter { it.kind == TabKind.CATEGORY }.mapNotNull { it.folderUri }.toSet()
    // 中身を 1 つ入れて作り、すぐ開く
    fun create(type: String, ref: String = "") {
        val (t, _) = model.addBoardTab(type, ref)
        onDismiss()
        model.toggle(t.id)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("タブを追加") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle("TAB / ふつうのタブ")
                Note("中身を 1 つ入れて作ります。あとで歯車から、ウィジェット・フォルダ・ページ・予定などを好きな数だけ足せます。")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    PillButton("＋ ウィジェット", onWidget, primary = true)
                    PillButton("＋ フォルダ", {
                        val (t, item) = model.addBoardTab(BoardItem.FOLDER)
                        onDismiss()
                        actions.pickFolder(item.id)
                        model.toggle(t.id)
                    })
                    // URL は開いたタブの中で入れる
                    PillButton("＋ Web ページ", { create(BoardItem.WEB) })
                    PillButton("＋ 画像・PDF", {
                        onDismiss()
                        actions.openDocument(arrayOf("image/*", "application/pdf")) { uri ->
                            val (t, item) = model.addBoardTab(BoardItem.FILE)
                            model.setTabFile(item.id, uri)
                            model.toggle(t.id)
                        }
                    })
                    BoardItem.PANELS.forEach { (type, kind) -> PillButton("＋ " + kind.defaultLabel, { create(type) }) }
                    PillButton("＋ 空のタブ", { val t = model.addTab(TabKind.BOARD); onDismiss(); model.toggle(t.id) })
                }
                SectionTitle("PINNED / ピン留め（端の下・すべてのアプリの上）")
                var docking by remember { mutableStateOf(false) }
                PillButton("アプリ・ショートカットを選ぶ（${model.favoriteApps.size}/${HomeModel.DOCK_MAX}）", { docking = true })
                if (docking) DockDialog(model) { docking = false; onDismiss() }
                SectionTitle("CATEGORY / アプリのカテゴリー")
                val cats = model.categories.filter { it.id !in usedCats }
                if (model.categories.isEmpty()) Note("アプリのドロワーの「仕分け」でカテゴリーを作ると、ここでタブにできます。")
                else if (cats.isEmpty()) Note("カテゴリーはすべてタブになっています。")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    cats.forEach { c -> PillButton(c.name, { model.addCategoryTab(c); onDismiss() }) }
                }
                // ほかの画面だけに出しているタブを、この画面にも出す
                val hidden = model.tabs.filter { !model.shownHere(it.id) }.map { it.id to it.label }
                if (hidden.isNotEmpty()) {
                    SectionTitle("HIDDEN / この画面で隠しているタブ")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        hidden.forEach { (id, label) -> PillButton(label, { model.setTabShow(id, "both"); onDismiss() }) }
                    }
                }
                Note("タブは長押しで、並べ替え・名前・アプリを置く・出す画面（閉じた／開いた）・外す。タブは両方の画面で共通で、幅と高さだけ画面ごとに覚えます。")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } },
    )
}

@Composable
private fun rememberActions(model: HomeModel, activity: ComponentActivity, edgeExit: (() -> Unit)?): Actions {
    var folderTab by remember { mutableStateOf<String?>(null) }
    var permDone by remember { mutableStateOf<(() -> Unit)?>(null) }
    var cameraDone by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    var resultDone by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    var exportSource by remember { mutableStateOf<Uri?>(null) }
    var docDone by remember { mutableStateOf<((Uri) -> Unit)?>(null) }
    val createDoc = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> if (uri != null) docDone?.invoke(uri) }
    val openDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) docDone?.invoke(uri) }

    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val id = folderTab
        if (uri != null && id != null) model.assignFolder(id, uri)
    }
    val perms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permDone?.invoke()
    }
    val picture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> cameraDone?.invoke(ok) }
    val video = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { ok -> cameraDone?.invoke(ok) }
    val result = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        resultDone?.invoke(it.resultCode == android.app.Activity.RESULT_OK)
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { dest ->
        val src = exportSource ?: return@rememberLauncherForActivityResult
        if (dest == null) return@rememberLauncherForActivityResult
        val ok = runCatching {
            activity.contentResolver.openInputStream(src)!!.use { i ->
                activity.contentResolver.openOutputStream(dest)!!.use { o -> i.copyTo(o) }
            }
        }.isSuccess
        Toast.makeText(activity, if (ok) "書き出しました" else "書き出せませんでした", Toast.LENGTH_SHORT).show()
    }

    return remember(activity) {
        Actions(
            activity = activity,
            start = { intent ->
                try {
                    activity.startActivity(intent)
                    // エッジから開いたら、エッジは閉じる（開いたアプリを前に）
                    edgeExit?.invoke()
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(activity, "開けるアプリがありません", Toast.LENGTH_SHORT).show()
                } catch (_: SecurityException) {
                    Toast.makeText(activity, "開けませんでした", Toast.LENGTH_SHORT).show()
                }
            },
            pickFolder = { id -> folderTab = id; folder.launch(null) },
            requestPermissions = { list, done -> permDone = done; perms.launch(list) },
            takePicture = { uri, done -> cameraDone = done; runCatching { picture.launch(uri) }.onFailure { done(false) } },
            captureVideo = { uri, done -> cameraDone = done; runCatching { video.launch(uri) }.onFailure { done(false) } },
            export = { src, name, _ -> exportSource = src; export.launch(name) },
            forResult = { intent, done -> resultDone = done; runCatching { result.launch(intent) }.onFailure { done(false) } },
            createDocument = { name, _, done -> docDone = done; createDoc.launch(name) },
            openDocument = { mimes, done -> docDone = done; openDoc.launch(mimes) },
            toast = { Toast.makeText(activity, it, Toast.LENGTH_SHORT).show() },
        )
    }
}

/** 既定のホームアプリとして選ぶ画面を開く。 */
fun requestHomeRole(actions: Actions) {
    val rm = actions.activity.getSystemService(RoleManager::class.java)
    if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
        actions.forResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME)) {}
    } else {
        actions.start(Intent(android.provider.Settings.ACTION_HOME_SETTINGS))
    }
}
