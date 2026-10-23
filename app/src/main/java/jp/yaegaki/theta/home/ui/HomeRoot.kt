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
val LocalLayout = staticCompositionLocalOf { Layout(true, 360.dp, 0, false) }

private const val SETTINGS = "__settings"

@Composable
fun HomeRoot(model: HomeModel, activity: ComponentActivity) {
    val actions = rememberActions(model, activity)
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

    val here = model.tab(model.location.removePrefix("tab:"))
    val canGoUp = model.location == "nas" && model.nasState.path.isNotEmpty() ||
        model.location == "drive" && model.driveState.stack.size > 1 ||
        here != null && (model.folders[here.id]?.stack?.size ?: 0) > 1
    // 狭い画面では、いちばん上のフォルダで戻るとファイラーを閉じてダッシュボードへ
    val closeFiler = model.filerOpen && model.profile == "narrow"
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
            .background(p.bg)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
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
            // 並べて出すと、残りのホームが 280dp 以上残る枚数まで
            maxDocked = when {
                w >= 1180.dp -> 2
                w >= 690.dp -> 1
                else -> 0
            },
            wideHome = w >= 720.dp,
        )
        LaunchedEffect(w >= 600.dp) { model.changeProfile(if (w >= 600.dp) "wide" else "narrow") }
        val available = w - railW
        CompositionLocalProvider(LocalActions provides actions, LocalLayout provides layout, LocalIconLook provides IconLook(model.iconMode, model.iconVersion)) {
            val docked = model.docked.takeLast(layout.maxDocked)
            Row(Modifier.fillMaxSize()) {
                if (model.railLeft) Rail(model, railW)
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    Row(Modifier.fillMaxSize()) {
                        if (model.railLeft) docked.forEach { DockedDrawer(model, it, drawerWidth(model, it, layout, available)) }
                        Box(Modifier.weight(1f).fillMaxHeight()) { Filer(model) }
                        if (!model.railLeft) docked.forEach { DockedDrawer(model, it, drawerWidth(model, it, layout, available)) }
                    }
                    Overlay(model, layout, available)
                }
                if (!model.railLeft) Rail(model, railW)
            }
            model.importReport?.let { r -> ImportReportDialog(model, r) { model.importReport = null } }
        }
    }
}

/** ウィジェットのタブはウィジェットの幅に合わせる。それ以外は決まった幅。 */
private fun drawerWidth(model: HomeModel, id: String?, layout: Layout, available: Dp): Dp {
    val t = model.widgetTab(id) ?: return layout.drawerWidth
    return (model.widgetWidth(t) + 16).dp.coerceIn(200.dp, available.coerceAtLeast(200.dp))
}

@Composable
private fun DockedDrawer(model: HomeModel, id: String, width: Dp) {
    if (model.tab(id) == null && model.widgetTab(id) == null) return
    val p = LocalPalette.current
    Box(Modifier.width(width).fillMaxHeight().background(p.panel)) {
        DrawerFrame(model, id, docked = true)
    }
}

@Composable
private fun Overlay(model: HomeModel, layout: Layout, available: Dp) {
    val p = LocalPalette.current
    val visibleId = if (model.settingsOpen) SETTINGS else model.open
    // 閉じるアニメーションの間も中身を描けるよう、最後に開いたものを覚えておく
    var shown by remember { mutableStateOf<String?>(null) }
    if (visibleId != null) shown = visibleId
    val fromRight = !model.railLeft

    AnimatedVisibility(visible = visibleId != null, enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClickLabel = "閉じる") {
                    model.close(); model.settingsOpen = false
                },
        )
    }
    // ウィジェットのタブは中身の高さだけの小さなパネル（上寄せ）。ほかは縦いっぱい
    val small = shown != SETTINGS && model.widgetTab(shown) != null
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
                } else {
                    if (id != null) DrawerFrame(model, id, docked = false)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Rail(model: HomeModel, width: Dp) {
    val p = LocalPalette.current
    val density = LocalDensity.current
    val left = model.railLeft
    Column(
        Modifier
            .width(width)
            .fillMaxHeight()
            .pointerInput(left) {
                // 画面の端から内側へなぞると、最後に開いたドロワーを出す
                var total = 0f
                detectHorizontalDragGestures(onDragStart = { total = 0f }, onDragEnd = {
                    val px = with(density) { 48.dp.toPx() }
                    if (!left && total < -px || left && total > px) model.openLast()
                }) { _, d -> total += d }
            }
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp),
        horizontalAlignment = if (left) Alignment.Start else Alignment.End,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        var lastGroup = -1
        val reorder = rememberReorderState()
        model.tabs.forEach { t ->
            if (lastGroup != -1 && t.kind.group != lastGroup) Spacer(Modifier.height(12.dp))
            lastGroup = t.kind.group
            RailTab(model, reorder, t.id, t.label, t.kind.group, active = railActive(model, t), shortcut = shortcut(model, t), left = left)
        }
        // ウィジェットは 1 タブに 1 つ。並びと大きさは画面（閉じた／開いた）ごと
        Spacer(Modifier.height(12.dp))
        model.widgetTabs.forEach { w ->
            val id = "w:${w.id}"
            RailTab(model, reorder, id, w.label.take(10), 1, active = model.open == id || id in model.docked, shortcut = null, left = left)
        }
        var adding by remember { mutableStateOf(false) }
        var addingWidget by remember { mutableStateOf(false) }
        Box(
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
}

private fun railActive(model: HomeModel, t: TabSpec) = when (t.kind) {
    TabKind.FOLDER -> (model.filerOpen || model.profile == "wide") &&
        (model.location == "tab:${t.id}" || model.location == "nas" && model.nasTab == t.id || model.location == "drive" && model.driveTab == t.id)
    else -> model.open == t.id || t.id in model.docked
}

private fun shortcut(model: HomeModel, t: TabSpec) = t.kind.key?.let { "Alt+$it" }
    ?: model.tabs.filter { it.kind == TabKind.FOLDER }.indexOf(t).takeIf { it in 0..8 }?.let { "Alt+${it + 1}" }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RailTab(model: HomeModel, reorder: ReorderState, id: String, label: String, group: Int, active: Boolean, shortcut: String?, left: Boolean) {
    val p = LocalPalette.current
    val groupColor = p.group(group)
    val code = model.tabs.indexOfFirst { it.id == id }.let { if (it >= 0) "%02d".format(it + 1) else "W" + (model.widgetTabs.indexOfFirst { w -> "w:${w.id}" == id } + 1) }
    var menu by remember(id) { mutableStateOf(false) }
    var renaming by remember(id) { mutableStateOf(false) }
    Box(
        Modifier
            .width(40.dp)
            // 長押し→ドラッグで並べ替え（タブはタブの間、ウィジェットのタブはウィジェットの間）。動かさずに離すとメニュー
            .reorderable(reorder, id, if (id.startsWith("w:")) "widget" else "tab", p.accent, onMenu = { menu = true }) { from, to ->
                if (from.startsWith("w:")) model.moveWidgetTabTo(from.removePrefix("w:").toInt(), to.removePrefix("w:").toInt())
                else model.moveTabTo(from, to)
            }
            .background(if (active) p.accent else p.bg)
            .border(1.dp, if (active) p.accent else p.line)
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
                .background(if (active) p.onAccent else groupColor),
        )
        Column(Modifier.padding(top = 4.dp, bottom = 10.dp).align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(code, color = if (active) p.onAccent else p.faint, fontSize = 9.sp, fontFamily = Hud.mono)
            Gap(4.dp)
            VerticalLabel(label, if (active) p.onAccent else p.fg)
        }
        TabMenu(model, id, menu, { menu = false }) { renaming = true }
    }
    if (renaming) RenameTabDialog(model, id) { renaming = false }
}

/** タブの長押し：上へ・下へ・名前を変える・外す。ウィジェットのタブも同じ。 */
@Composable
private fun TabMenu(model: HomeModel, id: String, expanded: Boolean, close: () -> Unit, rename: () -> Unit) {
    val actions = LocalActions.current
    val t = model.tabs.firstOrNull { it.id == id }
    val w = model.widgetTab(id)
    DropdownMenu(expanded = expanded, onDismissRequest = close) {
        DropdownMenuItem(text = { Text("▲ 上へ") }, onClick = { if (t != null) model.moveTab(id, -1) else if (w != null) model.moveWidgetTab(w.id, -1) })
        DropdownMenuItem(text = { Text("▼ 下へ") }, onClick = { if (t != null) model.moveTab(id, 1) else if (w != null) model.moveWidgetTab(w.id, 1) })
        if (t != null) {
            DropdownMenuItem(text = { Text("⤒ いちばん上へ") }, onClick = { close(); model.moveTab(id, -model.tabs.indexOf(t)) })
        }
        DropdownMenuItem(text = { Text("名前を変える") }, onClick = { close(); rename() })
        if (t?.kind == TabKind.FOLDER) DropdownMenuItem(text = { Text("端末のフォルダを選び直す") }, onClick = { close(); actions.pickFolder(id) })
        DropdownMenuItem(text = { Text("このタブを外す") }, onClick = {
            close()
            if (t != null) model.removeTab(id) else if (w != null) model.removeWidgetTab(w.id)
            actions.toast("外しました（下の「＋」で戻せます）")
        })
    }
}

@Composable
private fun RenameTabDialog(model: HomeModel, id: String, onDismiss: () -> Unit) {
    val t = model.tabs.firstOrNull { it.id == id }
    val w = model.widgetTab(id)
    var name by remember { mutableStateOf(t?.label ?: w?.label.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("タブの名前") },
        text = { Field(name, { name = it.take(14) }, "名前") },
        confirmButton = {
            TextButton(onClick = {
                if (t != null) model.renameTab(id, name) else if (w != null) model.renameWidgetTab(w.id, name)
                onDismiss()
            }, enabled = name.isNotBlank()) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("やめる") } },
    )
}

/**
 * 端のタブの「＋」：ウィジェット・アプリのカテゴリー・フォルダ・ドロワーを足す。
 * 置いてあるものは出さない（フォルダは何個でも足せる）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddTabDialog(model: HomeModel, onWidget: () -> Unit, onDismiss: () -> Unit) {
    val actions = LocalActions.current
    val usedCats = model.tabs.filter { it.kind == TabKind.CATEGORY }.mapNotNull { it.folderUri }.toSet()
    val drawers = TabKind.entries.filter { it != TabKind.FOLDER && it != TabKind.CATEGORY && model.tabs.none { t -> t.kind == it } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("タブを追加") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle("WIDGET / ウィジェット")
                PillButton("＋ ウィジェットを選ぶ", onWidget, primary = true)
                SectionTitle("CATEGORY / アプリのカテゴリー")
                val cats = model.categories.filter { it.id !in usedCats }
                if (model.categories.isEmpty()) Note("アプリのドロワーの「仕分け」でカテゴリーを作ると、ここでタブにできます。")
                else if (cats.isEmpty()) Note("カテゴリーはすべてタブになっています。")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    cats.forEach { c -> PillButton(c.name, { model.addCategoryTab(c); onDismiss() }) }
                }
                SectionTitle("FOLDER / フォルダ")
                PillButton("＋ フォルダのタブ", {
                    val t = model.addTab(TabKind.FOLDER)
                    onDismiss()
                    actions.pickFolder(t.id)
                })
                if (drawers.isNotEmpty()) {
                    SectionTitle("DRAWER / ドロワー")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        drawers.forEach { k -> PillButton(k.defaultLabel, { model.addTab(k); onDismiss() }) }
                    }
                }
                Note("タブは長押しで、並べ替え・名前を変える・外す。")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } },
    )
}

@Composable
private fun rememberActions(model: HomeModel, activity: ComponentActivity): Actions {
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
