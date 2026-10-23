package jp.yaegaki.theta.home.ui

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
    val toast: (String) -> Unit,
)

val LocalActions = staticCompositionLocalOf<Actions> { error("Actions not provided") }
val LocalLayout = staticCompositionLocalOf { Layout(true, 360.dp, 0, false) }

private const val SETTINGS = "__settings"

@Composable
fun HomeRoot(model: HomeModel, activity: ComponentActivity) {
    val actions = rememberActions(model, activity)
    val p = LocalPalette.current

    BackHandler(enabled = model.open != null || model.settingsOpen || model.query.isNotEmpty()) {
        val t = model.tab(model.open)
        when {
            model.settingsOpen -> model.settingsOpen = false
            t != null && t.kind == TabKind.FOLDER && model.up(t.id) -> Unit
            model.open != null -> model.close()
            else -> model.query = ""
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
        CompositionLocalProvider(LocalActions provides actions, LocalLayout provides layout) {
            val docked = model.docked.takeLast(layout.maxDocked)
            Row(Modifier.fillMaxSize()) {
                if (model.railLeft) Rail(model, railW)
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    Row(Modifier.fillMaxSize()) {
                        if (model.railLeft) docked.forEach { DockedDrawer(model, it, layout.drawerWidth) }
                        Box(Modifier.weight(1f).fillMaxHeight()) { HomeContent(model) }
                        if (!model.railLeft) docked.forEach { DockedDrawer(model, it, layout.drawerWidth) }
                    }
                    Overlay(model, layout)
                }
                if (!model.railLeft) Rail(model, railW)
            }
        }
    }
}

@Composable
private fun DockedDrawer(model: HomeModel, id: String, width: Dp) {
    val t = model.tab(id) ?: return
    val p = LocalPalette.current
    Box(Modifier.width(width).fillMaxHeight().background(p.panel)) {
        DrawerFrame(model, t, docked = true)
    }
}

@Composable
private fun Overlay(model: HomeModel, layout: Layout) {
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
    Box(Modifier.fillMaxSize(), contentAlignment = if (fromRight) Alignment.CenterEnd else Alignment.CenterStart) {
        AnimatedVisibility(
            visible = visibleId != null,
            enter = slideInHorizontally(tween(280)) { if (fromRight) it else -it },
            exit = slideOutHorizontally(tween(240)) { if (fromRight) it else -it },
        ) {
            Box(
                Modifier
                    .width(layout.drawerWidth)
                    .fillMaxHeight()
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
                    model.tab(id)?.let { DrawerFrame(model, it, docked = false) }
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
        model.tabs.forEach { t ->
            if (lastGroup != -1 && t.kind.group != lastGroup) Spacer(Modifier.height(12.dp))
            lastGroup = t.kind.group
            RailTab(model, t, left)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RailTab(model: HomeModel, t: TabSpec, left: Boolean) {
    val p = LocalPalette.current
    val active = model.open == t.id || t.id in model.docked
    val groupColor = p.group(t.kind.group)
    val shape = if (left) RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp) else RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)
    val shortcut = t.kind.key?.let { "Alt+$it" }
        ?: model.tabs.filter { it.kind == TabKind.FOLDER }.indexOf(t).takeIf { it in 0..8 }?.let { "Alt+${it + 1}" }
    Box(
        Modifier
            .width(40.dp)
            .clip(shape)
            .background(if (active) p.accent else p.raised)
            .combinedClickable(role = Role.Tab, onClick = { model.toggle(t.id) })
            .semantics {
                contentDescription = t.label + (shortcut?.let { "（$it）" } ?: "")
                selected = active
            },
    ) {
        Box(
            Modifier
                .align(if (left) Alignment.CenterEnd else Alignment.CenterStart)
                .width(3.dp)
                .height(28.dp)
                .background(groupColor),
        )
        Box(Modifier.padding(vertical = 12.dp).align(Alignment.Center)) {
            VerticalLabel(t.label, if (active) p.onAccent else p.fg)
        }
    }
}

@Composable
private fun rememberActions(model: HomeModel, activity: ComponentActivity): Actions {
    var folderTab by remember { mutableStateOf<String?>(null) }
    var permDone by remember { mutableStateOf<(() -> Unit)?>(null) }
    var cameraDone by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    var resultDone by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    var exportSource by remember { mutableStateOf<Uri?>(null) }

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
