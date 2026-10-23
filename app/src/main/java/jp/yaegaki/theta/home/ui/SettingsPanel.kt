package jp.yaegaki.theta.home.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.yaegaki.theta.home.BuildConfigInfo
import jp.yaegaki.theta.home.HomeModel
import jp.yaegaki.theta.home.data.TabKind
import jp.yaegaki.theta.home.data.TabSpec
import jp.yaegaki.theta.home.notify.HatchNotificationListener

@Composable
fun SettingsPanel(model: HomeModel) {
    val p = LocalPalette.current
    val actions = LocalActions.current
    var renaming by remember { mutableStateOf<TabSpec?>(null) }
    var adding by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 8.dp, top = 18.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("設定", color = p.fg, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            GlyphButton(Glyph.CLOSE, "閉じる", { model.settingsOpen = false })
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
        DrawerList {
            item {
                FlowRow2 {
                    PillButton("既定のホームにする", { requestHomeRole(actions) }, primary = true)
                    PillButton(
                        if (HatchNotificationListener.isEnabled(actions.activity)) "通知へのアクセス：許可済み" else "通知へのアクセス（LINE・メール）",
                        { actions.start(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                    )
                }
                Gap(18.dp)
                SectionTitle("見た目", Modifier.padding(horizontal = 10.dp))
                Toggle("タブを左端に置く", model.railLeft) { model.changeRailLeft(it) }
                Toggle("グレーの配色", model.gray) { model.changeGray(it) }
                Toggle("ホームのアプリアイコンをモノクロに", model.monoIcons) { model.changeMonoIcons(it) }
                Gap(18.dp)
                SectionTitle("端のタブ（押すと名前の変更・並べ替え）", Modifier.padding(horizontal = 10.dp))
            }
            itemsIndexed(model.tabs, key = { _, t -> t.id }) { i, t ->
                var menu by remember { mutableStateOf(false) }
                Box {
                    RowButton(onClick = { menu = true }) {
                        Dot(p.group(t.kind.group))
                        TwoLines(
                            t.label,
                            when {
                                t.kind == TabKind.FOLDER && t.folderUri == null -> "フォルダ · 未割り当て"
                                t.kind == TabKind.FOLDER -> "フォルダ · " + android.net.Uri.decode(t.folderUri!!.substringAfterLast('/')).substringAfter(':')
                                else -> t.kind.defaultLabel + (t.kind.key?.let { " · Alt+$it" } ?: "")
                            },
                            Modifier.weight(1f),
                        )
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("名前を変える") }, onClick = { menu = false; renaming = t })
                        if (t.kind == TabKind.FOLDER) DropdownMenuItem(text = { Text("フォルダを選ぶ") }, onClick = { menu = false; actions.pickFolder(t.id) })
                        if (i > 0) DropdownMenuItem(text = { Text("上へ") }, onClick = { menu = false; model.updateTabs(model.tabs.move(i, i - 1)) })
                        if (i < model.tabs.lastIndex) DropdownMenuItem(text = { Text("下へ") }, onClick = { menu = false; model.updateTabs(model.tabs.move(i, i + 1)) })
                        DropdownMenuItem(text = { Text("このタブを外す") }, onClick = { menu = false; model.updateTabs(model.tabs - t) })
                    }
                }
            }
            item {
                Box {
                    FlowRow2 { PillButton("＋ タブを追加", { adding = true }) }
                    DropdownMenu(expanded = adding, onDismissRequest = { adding = false }) {
                        TabKind.entries.forEach { k ->
                            DropdownMenuItem(text = { Text(if (k == TabKind.FOLDER) "フォルダ" else k.defaultLabel) }, onClick = { adding = false; model.addTab(k) })
                        }
                    }
                }
                Gap(18.dp)
                Note("物理キーボード：Alt＋1〜9 でフォルダのタブ、Alt＋C 予定・M メール・L LINE・P 人・W Widget・A アプリ・B 栞・X Clip・R 記録・H Hatch。ホームで文字を打つとすぐ検索、Esc で閉じる。")
                Note("Theta Home Hatch ${BuildConfigInfo.versionName(actions.activity)}")
            }
        }
    }

    renaming?.let { t ->
        var name by remember(t.id) { mutableStateOf(t.label) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("タブの名前") },
            text = { Field(name, { name = it.take(8) }, "短い名前（縦に並べて表示）") },
            confirmButton = {
                TextButton(onClick = {
                    model.updateTabs(model.tabs.map { if (it.id == t.id) it.copy(label = name.ifBlank { t.label }) else it })
                    renaming = null
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("やめる") } },
        )
    }
}

@Composable
private fun Toggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    val p = LocalPalette.current
    RowButton(onClick = { onChange(!value) }) {
        Text(label, color = p.fg, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = onChange)
    }
}

private fun <T> List<T>.move(from: Int, to: Int): List<T> = toMutableList().also { it.add(to, it.removeAt(from)) }
