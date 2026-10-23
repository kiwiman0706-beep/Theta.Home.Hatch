package jp.yaegaki.theta.home.ui

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import jp.yaegaki.theta.home.HomeApp
import jp.yaegaki.theta.home.HomeModel
import jp.yaegaki.theta.home.MainActivity
import jp.yaegaki.theta.home.data.WidgetTab
import jp.yaegaki.theta.home.data.WidgetCell
import androidx.compose.foundation.layout.Row

private fun profileName(p: String) = if (p == "wide") "開いた画面" else "閉じた画面"

/**
 * ウィジェットのタブ。ウィジェットを縦に何個でも並べられる。
 * ふだんはウィジェットだけ。編集（見出しの歯車）にすると、角をなぞって大きさ・上へ下へ・外す・足す・取り込む。
 * 大きさはこの画面（閉じた／開いた）だけに効く。
 */
@Composable
fun WidgetTabDrawer(model: HomeModel, t: WidgetTab, editing: Boolean) {
    var adding by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        t.cells.forEachIndexed { i, c -> WidgetCellView(model, t, c, i, editing) }
        if (editing) FlowRow2 {
            PillButton("＋ ウィジェットを足す", { adding = true }, primary = true)
            PillButton("${profileName(model.otherProfile())}から取り込む", { importing = true })
        }
    }
    if (adding) AddWidgetDialog(model, intoTab = t.id) { adding = false }
    if (importing) ImportWidgetsDialog(model) { importing = false }
}

@Composable
private fun WidgetCellView(model: HomeModel, t: WidgetTab, c: WidgetCell, index: Int, editing: Boolean) {
    val p = LocalPalette.current
    val activity = LocalActions.current.activity
    val host = (activity.application as HomeApp).widgets
    val density = LocalDensity.current.density
    val config = LocalConfiguration.current
    val maxW = config.screenWidthDp - 46 - 16
    val maxH = config.screenHeightDp - 160
    var w by remember(c.id, c.w) { mutableIntStateOf(c.w) }
    var h by remember(c.id, c.h) { mutableIntStateOf(c.h) }
    val info = host.info(c.id)
    // ドロワーは端に付いているので、角を内側（画面の中央側）へなぞると広がる
    val grow = if (model.railLeft) 1 else -1
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (info == null) {
            Note("このウィジェットは使えなくなりました（アプリが消えたなど）。")
        } else {
            Box(Modifier.size(w.dp, h.dp)) {
                AndroidView(
                    factory = { host.view(activity, c.id)!! },
                    update = { v -> resize(v, w, h) },
                    modifier = Modifier.fillMaxSize(),
                )
                if (editing) Box(
                    Modifier
                        .align(if (model.railLeft) Alignment.BottomEnd else Alignment.BottomStart)
                        .size(40.dp)
                        .clip(RoundedCornerShape(0.dp))
                        .background(p.accent.copy(alpha = 0.85f))
                        .semantics { contentDescription = "なぞって大きさを変える" }
                        .pointerInput(c.id, grow) {
                            detectDragGestures(onDragEnd = { model.updateWidgetCell(t.id, c.copy(w = w, h = h)) }) { change, drag ->
                                change.consume()
                                w = (w + grow * drag.x / density).toInt().coerceIn(120, maxW.coerceAtLeast(120))
                                h = (h + drag.y / density).toInt().coerceIn(80, maxH.coerceAtLeast(80))
                                model.resizeLive(c.id, w)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) { Text("⤡", color = p.onAccent, fontSize = 18.sp) }
            }
        }
        if (editing) Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${info?.let { host.label(it) } ?: "?"}  ${w}×${h}", color = p.faint, fontSize = 11.sp, fontFamily = Hud.mono, modifier = Modifier.weight(1f))
            if (index > 0) TextButton(onClick = { model.moveWidgetCell(t.id, c.id, -1) }) { Text("▲", color = p.fg) }
            if (index < t.cells.lastIndex) TextButton(onClick = { model.moveWidgetCell(t.id, c.id, 1) }) { Text("▼", color = p.fg) }
            TextButton(onClick = { model.removeWidgetCell(t.id, c.id) }) { Text("外す", color = p.accent) }
        }
    }
}

private fun resize(v: AppWidgetHostView, w: Int, h: Int) {
    if (Build.VERSION.SDK_INT >= 31) {
        v.updateAppWidgetSize(Bundle(), listOf(SizeF(w.toFloat(), h.toFloat())))
    } else {
        @Suppress("DEPRECATION")
        v.updateAppWidgetSize(null, w, h, w, h)
    }
}

/** ウィジェットを選んで、この画面のタブに足す。割り当ての許可と、必要ならウィジェットの設定画面を通る。 */
@Composable
fun AddWidgetDialog(model: HomeModel, intoTab: Int = -1, onDismiss: () -> Unit) {
    val actions = LocalActions.current
    val activity = actions.activity
    val app = activity.application as HomeApp
    val host = app.widgets
    val providers = remember { host.providers() }

    // 端末によって、設定画面を開けない・割り当てを断られるウィジェットがある。そのときも黙らずに知らせる
    fun configureOrAdd(id: Int) {
        if (host.needsConfigure(id)) {
            app.store.pendingWidget = id
            val ok = runCatching { host.startConfigure(activity, id, MainActivity.REQ_WIDGET_CONFIGURE) }.isSuccess
            if (!ok) {
                // 設定画面を開けないときは、設定なしで置いてみる（Android 12 以降は設定が任意のものが多い）
                app.store.pendingWidget = -1
                model.addWidget(id)
                actions.toast("ウィジェットの設定画面を開けなかったので、そのまま置きました")
            }
        } else {
            model.addWidget(id)
        }
    }

    fun add(info: AppWidgetProviderInfo) {
        app.store.pendingWidgetTab = intoTab
        val id = runCatching { host.allocate() }.getOrElse {
            actions.toast("ウィジェットを用意できませんでした（${it.javaClass.simpleName}）")
            return
        }
        val bound = runCatching { host.bindIfAllowed(id, info) }.getOrDefault(false)
        if (bound) configureOrAdd(id)
        else actions.forResult(host.bindIntent(id, info)) { ok ->
            if (ok) configureOrAdd(id)
            else {
                host.release(id)
                actions.toast("ウィジェットの許可がありませんでした。もう一度選んで「許可」を押してください")
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (intoTab == -1) "ウィジェットのタブを追加（${profileName(model.profile)}）" else "このタブにウィジェットを足す") },
        text = {
            if (providers.isEmpty()) Text("置けるウィジェットが見つかりませんでした。", color = LocalPalette.current.muted, fontSize = 14.sp)
            else LazyColumn(Modifier.heightIn(max = 460.dp)) {
                itemsIndexed(providers, key = { _, it -> it.provider.flattenToString() }) { zi, info ->
                    RowButton(zebra = zi, onClick = { onDismiss(); add(info) }) {
                        val pm = activity.packageManager
                        val appLabel = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(info.provider.packageName, 0)).toString() }.getOrDefault("")
                        TwoLines(host.label(info), appLabel, Modifier.weight(1f))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } },
    )
}

/** もう一方の画面（閉じた／開いた）のウィジェットのタブを、この画面に持ってくる。 */
@Composable
fun ImportWidgetsDialog(model: HomeModel, onDismiss: () -> Unit) {
    val other = remember { model.otherWidgetTabs() }
    val from = profileName(model.otherProfile())
    val to = profileName(model.profile)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${from}から取り込む") },
        text = {
            Text(
                if (other.isEmpty()) "${from}にはウィジェットのタブがありません。"
                else "${from}のタブ ${other.size} 個（${other.joinToString("・") { it.label }}）を${to}に持ってきます。大きさも${from}のものになります。",
                color = LocalPalette.current.muted, fontSize = 14.sp,
            )
        },
        confirmButton = {
            if (other.isNotEmpty()) TextButton(onClick = { model.importWidgets(replace = true); onDismiss() }) { Text("置き換える") }
        },
        dismissButton = {
            if (other.isNotEmpty()) TextButton(onClick = { model.importWidgets(replace = false); onDismiss() }) { Text("今のタブに足す") }
            else TextButton(onClick = onDismiss) { Text("閉じる") }
        },
    )
}
