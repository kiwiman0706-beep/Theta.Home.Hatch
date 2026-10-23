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

private fun profileName(p: String) = if (p == "wide") "開いた画面" else "閉じた画面"

/** ウィジェット 1 つのタブ。角をなぞって大きさを変える。大きさはこの画面（閉じた／開いた）だけに効く。 */
@Composable
fun WidgetTabDrawer(model: HomeModel, t: WidgetTab) {
    val p = LocalPalette.current
    val actions = LocalActions.current
    val activity = actions.activity
    val host = (activity.application as HomeApp).widgets
    val density = LocalDensity.current.density
    val config = LocalConfiguration.current
    val maxW = config.screenWidthDp - 46 - 16
    val maxH = config.screenHeightDp - 160
    var w by remember(t.id) { mutableIntStateOf(t.w) }
    var h by remember(t.id) { mutableIntStateOf(t.h) }
    var renaming by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    val info = host.info(t.id)
    // ドロワーは端に付いているので、角を内側（画面の中央側）へなぞると広がる
    val grow = if (model.railLeft) 1 else -1

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (info == null) {
            Note("このウィジェットは使えなくなりました（アプリが消えたなど）。")
        } else {
            Box(Modifier.size(w.dp, h.dp)) {
                AndroidView(
                    factory = { host.view(activity, t.id)!! },
                    update = { v -> resize(v, w, h) },
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier
                        .align(if (model.railLeft) Alignment.BottomEnd else Alignment.BottomStart)
                        .size(40.dp)
                        .clip(RoundedCornerShape(0.dp))
                        .background(p.accent.copy(alpha = 0.85f))
                        .semantics { contentDescription = "なぞって大きさを変える" }
                        .pointerInput(t.id, grow) {
                            detectDragGestures(onDragEnd = { model.updateWidgetTab(t.copy(w = w, h = h)) }) { change, drag ->
                                change.consume()
                                w = (w + grow * drag.x / density).toInt().coerceIn(120, maxW.coerceAtLeast(120))
                                h = (h + drag.y / density).toInt().coerceIn(80, maxH.coerceAtLeast(80))
                                model.resizeLive(t.id, w)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) { Text("⤡", color = p.onAccent, fontSize = 18.sp) }
            }
            Text("${w} × ${h} dp（${profileName(model.profile)}の大きさ）", color = p.faint, fontSize = 11.sp)
        }
        FlowRow2 {
            PillButton("名前", { renaming = true })
            PillButton("左へ", { model.moveWidgetTab(t.id, -1) })
            PillButton("右へ", { model.moveWidgetTab(t.id, 1) })
            PillButton("このタブを外す", { model.removeWidgetTab(t.id) })
            PillButton("${profileName(model.otherProfile())}から取り込む", { importing = true })
        }
    }

    if (renaming) {
        var name by remember { mutableStateOf(t.label) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("タブの名前") },
            text = { Field(name, { name = it.take(10) }, "短い名前") },
            confirmButton = { TextButton(onClick = { model.updateWidgetTab(t.copy(label = name.ifBlank { t.label }, w = w, h = h)); renaming = false }) { Text("保存") } },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("やめる") } },
        )
    }
    if (importing) ImportWidgetsDialog(model) { importing = false }
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
fun AddWidgetDialog(model: HomeModel, onDismiss: () -> Unit) {
    val actions = LocalActions.current
    val activity = actions.activity
    val app = activity.application as HomeApp
    val host = app.widgets
    val providers = remember { host.providers() }

    fun configureOrAdd(id: Int) {
        if (host.needsConfigure(id)) {
            app.store.pendingWidget = id
            host.startConfigure(activity, id, MainActivity.REQ_WIDGET_CONFIGURE)
        } else {
            model.addWidget(id)
        }
    }

    fun add(info: AppWidgetProviderInfo) {
        val id = host.allocate()
        if (host.bindIfAllowed(id, info)) configureOrAdd(id)
        else actions.forResult(host.bindIntent(id, info)) { ok -> if (ok) configureOrAdd(id) else host.release(id) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ウィジェットのタブを追加（${profileName(model.profile)}）") },
        text = {
            LazyColumn(Modifier.heightIn(max = 460.dp)) {
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
