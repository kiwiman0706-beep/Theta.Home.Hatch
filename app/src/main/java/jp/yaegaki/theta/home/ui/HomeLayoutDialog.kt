package jp.yaegaki.theta.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.yaegaki.theta.home.HomeModel
import jp.yaegaki.theta.home.data.HomeSection

/**
 * ホームの並び：区画ごとに ▲▼ で順番、「閉」「開」で出す画面（閉じた画面・開いた画面やタブレット）、
 * 2 列のときは「左」「右」で列。変えるとすぐホームに出る。
 */
@Composable
fun HomeLayoutDialog(model: HomeModel, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ホームの並び") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Note("「閉」は閉じた画面（カバー画面など）、「開」は開いた画面・タブレットで出すもの。2 列のときは「左」「右」で列を選びます。")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("開いた画面・横長では 2 列", color = p.fg, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Switch(checked = model.homeTwoColumns, onCheckedChange = { model.changeHomeTwoColumns(it) })
                }
                val list = model.homeSections
                list.forEachIndexed { i, s ->
                    Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(HomeSection.LABELS[s.id] ?: s.id, color = p.fg, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Toggle("閉", s.narrow, "${HomeSection.LABELS[s.id]}を閉じた画面に出す") { model.updateSection(s.copy(narrow = !s.narrow)) }
                        Toggle("開", s.wide, "${HomeSection.LABELS[s.id]}を開いた画面に出す") { model.updateSection(s.copy(wide = !s.wide)) }
                        if (model.homeTwoColumns) Toggle(if (s.col == 0) "左" else "右", s.col == 1, "${HomeSection.LABELS[s.id]}の列") { model.updateSection(s.copy(col = 1 - s.col)) }
                        Arrow("▲", i > 0, "${HomeSection.LABELS[s.id]}を上へ") { model.moveSection(s.id, -1) }
                        Arrow("▼", i < list.size - 1, "${HomeSection.LABELS[s.id]}を下へ") { model.moveSection(s.id, 1) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } },
        dismissButton = { TextButton(onClick = { model.resetSections() }) { Text("初めに戻す") } },
    )
}

@Composable
private fun Toggle(label: String, on: Boolean, description: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Text(label, color = if (on) p.onAccent else p.muted, fontSize = 12.sp, fontFamily = Hud.mono,
        modifier = Modifier.background(if (on) p.accent else p.raised).border(1.dp, p.line)
            .semantics { contentDescription = description + if (on) "（オン）" else "（オフ）" }
            .clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp))
}

@Composable
private fun Arrow(label: String, enabled: Boolean, description: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Text(label, color = if (enabled) p.fg else p.faint, fontSize = 12.sp,
        modifier = Modifier.semantics { contentDescription = description }.clickable(enabled = enabled, onClick = onClick).padding(horizontal = 6.dp, vertical = 6.dp))
}
