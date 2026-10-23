package jp.yaegaki.theta.home.ui

import android.appwidget.AppWidgetProviderInfo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import jp.yaegaki.theta.home.HomeApp
import jp.yaegaki.theta.home.HomeModel
import jp.yaegaki.theta.home.MainActivity

/** ほかのアプリのウィジェット（カレンダー・メール・天気など）をドロワーにしまっておく。 */
@Composable
fun WidgetDrawer(model: HomeModel) {
    val p = LocalPalette.current
    val actions = LocalActions.current
    val activity = actions.activity
    val app = activity.application as HomeApp
    val host = app.widgets
    var picking by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }

    fun configureOrSave(id: Int) {
        if (host.needsConfigure(id)) {
            app.store.pendingWidget = id
            host.startConfigure(activity, id, MainActivity.REQ_WIDGET_CONFIGURE)
        } else {
            model.saveWidget(id)
        }
    }

    fun add(info: AppWidgetProviderInfo) {
        val id = host.allocate()
        if (host.bindIfAllowed(id, info)) {
            configureOrSave(id)
        } else {
            actions.forResult(host.bindIntent(id, info)) { ok -> if (ok) configureOrSave(id) else host.release(id) }
        }
    }

    DrawerList {
        if (model.widgetIds.isEmpty()) {
            item { Note("カレンダーやメールなど、ほかのアプリが用意しているウィジェットをここに置けます。") }
        }
        items(model.widgetIds, key = { it }) { id ->
            val info = host.info(id)
            Column(Modifier.padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (info == null) {
                    Note("このウィジェットは使えなくなりました")
                } else {
                    val density = activity.resources.displayMetrics.density
                    val minH = (info.minHeight / density).toInt().coerceIn(110, 420)
                    Card {
                        AndroidView(
                            factory = { host.view(activity, id)!! },
                            modifier = Modifier.fillMaxWidth().heightIn(min = minH.dp),
                        )
                    }
                }
                if (editing) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(info?.let { host.label(it) } ?: "", color = p.muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        PillButton("上へ", { model.moveWidget(id, -1) })
                        PillButton("下へ", { model.moveWidget(id, 1) })
                        PillButton("外す", { model.removeWidget(id) })
                    }
                }
            }
        }
        item {
            FlowRow2 {
                PillButton("＋ ウィジェットを追加", { picking = true }, primary = model.widgetIds.isEmpty())
                if (model.widgetIds.isNotEmpty()) PillButton(if (editing) "並べ替え終わり" else "並べ替え・外す", { editing = !editing })
            }
        }
    }

    if (picking) {
        val providers = remember { host.providers() }
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text("ウィジェットを選ぶ") },
            text = {
                LazyColumn(Modifier.heightIn(max = 460.dp)) {
                    items(providers, key = { it.provider.flattenToString() }) { info ->
                        RowButton(onClick = { picking = false; add(info) }) {
                            val pm = activity.packageManager
                            val appLabel = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(info.provider.packageName, 0)).toString() }.getOrDefault("")
                            TwoLines(host.label(info), appLabel, Modifier.weight(1f))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { picking = false }) { Text("閉じる") } },
        )
    }
}
