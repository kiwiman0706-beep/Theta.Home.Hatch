package jp.yaegaki.theta.home.ui

import kotlin.math.roundToInt
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.Row


internal fun resize(v: AppWidgetHostView, w: Int, h: Int) {
    if (Build.VERSION.SDK_INT >= 31) {
        v.updateAppWidgetSize(Bundle(), listOf(SizeF(w.toFloat(), h.toFloat())))
    } else {
        @Suppress("DEPRECATION")
        v.updateAppWidgetSize(null, w, h, w, h)
    }
}

/** ウィジェットを選んで、タブに足す（intoBoard が "" なら新しいタブ）。割り当ての許可と、必要ならウィジェットの設定画面を通る。 */
@Composable
fun AddWidgetDialog(model: HomeModel, intoBoard: String = "", onDismiss: () -> Unit) {
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
        app.store.pendingBoard = intoBoard
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

    // アプリごとにまとめる。アプリを押すと、そのアプリのウィジェット（絵と大きさ）が開く
    val pm = activity.packageManager
    val groups = remember(providers) {
        providers.groupBy { it.provider.packageName }.map { (pkg, list) ->
            val label = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
            Triple(pkg, label, list.sortedBy { host.label(it) })
        }.sortedBy { it.second.lowercase() }
    }
    var q by remember { mutableStateOf("") }
    var openPkg by remember { mutableStateOf<String?>(null) }
    val p = LocalPalette.current
    val shown = groups.filter { (_, label, list) -> q.isBlank() || label.contains(q, true) || list.any { host.label(it).contains(q, true) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (intoBoard.isEmpty()) "ウィジェットのタブを追加" else "このタブにウィジェットを足す") },
        text = {
            if (providers.isEmpty()) Text("置けるウィジェットが見つかりませんでした。", color = p.muted, fontSize = 14.sp)
            else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(q, { q = it }, "アプリ名・ウィジェット名で探す")
                LazyColumn(Modifier.heightIn(max = 460.dp)) {
                    shown.forEachIndexed { zi, (pkg, label, list) ->
                        val open = openPkg == pkg || q.isNotBlank() && !label.contains(q, true)
                        item(key = pkg) {
                            RowButton(zebra = zi, onClick = { openPkg = if (openPkg == pkg) null else pkg }) {
                                val icon = remember(pkg) { runCatching { pm.getApplicationIcon(pkg) }.getOrNull() }
                                icon?.let { androidx.compose.foundation.Image(androidx.compose.ui.graphics.painter.BitmapPainter(it.toBitmapSafe(96).asImageBitmap()), null, Modifier.size(28.dp)) }
                                Text(label, color = p.fg, fontSize = 15.sp, modifier = Modifier.weight(1f))
                                Text("%d %s".format(list.size, if (open) "▲" else "▼"), color = p.faint, fontSize = 12.sp, fontFamily = Hud.mono)
                            }
                        }
                        if (open || q.isNotBlank()) list.filter { q.isBlank() || label.contains(q, true) || host.label(it).contains(q, true) }.forEach { info ->
                            item(key = info.provider.flattenToString()) {
                                Row(
                                    Modifier.fillMaxWidth().clickable { onDismiss(); add(info) }.padding(start = 24.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    val preview = remember(info.provider) { runCatching { info.loadPreviewImage(activity, 0) ?: info.loadIcon(activity, 0) }.getOrNull() }
                                    Box(Modifier.size(64.dp, 48.dp).background(p.raised), contentAlignment = Alignment.Center) {
                                        preview?.let { androidx.compose.foundation.Image(androidx.compose.ui.graphics.painter.BitmapPainter(it.toBitmapSafe(256).asImageBitmap()), null, Modifier.fillMaxSize().padding(2.dp), contentScale = androidx.compose.ui.layout.ContentScale.Fit) }
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text(host.label(info), color = p.fg, fontSize = 14.sp)
                                        val d = activity.resources.displayMetrics.density
                                        Text("${(info.minWidth / d).toInt()} × ${(info.minHeight / d).toInt()} dp〜", color = p.faint, fontSize = 11.sp, fontFamily = Hud.mono)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } },
    )
}

/** Drawable を絵にする（大きすぎるものは縮める）。 */
private fun android.graphics.drawable.Drawable.toBitmapSafe(max: Int): android.graphics.Bitmap {
    val w = intrinsicWidth.takeIf { it > 0 } ?: max
    val h = intrinsicHeight.takeIf { it > 0 } ?: max
    val scale = minOf(1f, max.toFloat() / maxOf(w, h))
    val bw = maxOf(1, (w * scale).toInt())
    val bh = maxOf(1, (h * scale).toInt())
    val b = android.graphics.Bitmap.createBitmap(bw, bh, android.graphics.Bitmap.Config.ARGB_8888)
    setBounds(0, 0, bw, bh)
    draw(android.graphics.Canvas(b))
    return b
}

