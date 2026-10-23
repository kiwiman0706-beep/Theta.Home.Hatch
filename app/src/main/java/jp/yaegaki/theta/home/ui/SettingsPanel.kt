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
import androidx.compose.foundation.layout.width
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
    var nasDialog by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var sorting by remember { mutableStateOf(false) }
    var renamingCategory by remember { mutableStateOf<jp.yaegaki.theta.home.data.Category?>(null) }
    var renamingPreset by remember { mutableStateOf<String?>(null) }
    var importConfirm by remember { mutableStateOf(false) }

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
                SectionTitle("NAS とウィジェット", Modifier.padding(horizontal = 10.dp))
                FlowRow2 {
                    PillButton(if (model.nas.configured()) "NAS の接続先：${model.nas.base()}" else "NAS を登録", { nasDialog = true })
                    PillButton("ウィジェット：${if (model.profile == "wide") "閉じた画面" else "開いた画面"}から取り込む", { importing = true })
                }
                Gap(18.dp)
                SectionTitle("アプリのカテゴリー（押すと名前・並び・削除）", Modifier.padding(horizontal = 10.dp))
                model.categories.forEachIndexed { i, c ->
                    var menu by remember(c.id) { mutableStateOf(false) }
                    Box {
                        RowButton(zebra = i, onClick = { menu = true }) {
                            Text("%02d".format(i + 1), color = p.faint, fontSize = 12.sp)
                            Text(c.name, color = p.fg, fontSize = 15.sp, modifier = Modifier.weight(1f))
                            Text("%d 個".format(model.appCategories.count { it.value == c.id }), color = p.muted, fontSize = 12.sp)
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("名前を変える") }, onClick = { menu = false; renamingCategory = c })
                            if (i > 0) DropdownMenuItem(text = { Text("上へ") }, onClick = { menu = false; model.moveCategory(c, -1) })
                            if (i < model.categories.lastIndex) DropdownMenuItem(text = { Text("下へ") }, onClick = { menu = false; model.moveCategory(c, 1) })
                            DropdownMenuItem(text = { Text("消す（中のアプリは未分類に）") }, onClick = { menu = false; model.deleteCategory(c) })
                        }
                    }
                }
                FlowRow2 { PillButton("仕分けガイドを開く", { sorting = true }, primary = model.categories.isEmpty()) }
                Gap(18.dp)
                SectionTitle("仕分けの候補（規定のカテゴリー）", Modifier.padding(horizontal = 10.dp))
                Note("仕分けガイドの最初に並ぶ候補。● は初めから選んでおくもの。押すと名前の変更・削除など。")
                model.categoryPresets.forEachIndexed { i, name ->
                    var menu by remember(name) { mutableStateOf(false) }
                    Box {
                        RowButton(zebra = i, onClick = { menu = true }) {
                            Text(if (name in model.categoryDefaults) "●" else "○", color = if (name in model.categoryDefaults) p.accent else p.faint, fontSize = 12.sp)
                            Text(name, color = p.fg, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("名前を変える") }, onClick = { menu = false; renamingPreset = name })
                            DropdownMenuItem(text = { Text(if (name in model.categoryDefaults) "初めから選ばない" else "初めから選んでおく") }, onClick = { menu = false; model.toggleDefault(name) })
                            if (i > 0) DropdownMenuItem(text = { Text("上へ") }, onClick = { menu = false; model.movePreset(name, -1) })
                            if (i < model.categoryPresets.lastIndex) DropdownMenuItem(text = { Text("下へ") }, onClick = { menu = false; model.movePreset(name, 1) })
                            DropdownMenuItem(text = { Text("候補から消す") }, onClick = { menu = false; model.deletePreset(name) })
                        }
                    }
                }
                FlowRow2 {
                    PillButton("＋ 候補を追加", { renamingPreset = "" })
                    PillButton("初期の候補に戻す", { model.resetPresets() })
                }
                Gap(18.dp)
                SectionTitle("BACKUP / 設定の書き出し・読み込み", Modifier.padding(horizontal = 10.dp))
                Note("タブ・テーマ・カテゴリーと仕分け・候補・栞・ウィジェットの並び・NAS の接続先（パスワードを除く）・アプリの一覧を 1 つのファイルに。新しいスマホで読み込むと、入っていないアプリを一覧にして Play ストアへ案内します。")
                FlowRow2 {
                    PillButton("設定を書き出す", { actions.createDocument(jp.yaegaki.theta.home.data.Backup.fileName(), "application/json") { model.exportSettings(it) } }, primary = true)
                    PillButton("設定を読み込む", { importConfirm = true })
                }
                Gap(18.dp)
                SectionTitle("見た目", Modifier.padding(horizontal = 10.dp))
                Toggle("タブを左端に置く", model.railLeft) { model.changeRailLeft(it) }
                Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("THEME", color = p.faint, fontSize = 11.sp, letterSpacing = 1.5.sp, modifier = Modifier.width(56.dp))
                    listOf("dark" to "ダーク", "light" to "ライト", "system" to "端末に合わせる").forEach { (mode, label) ->
                        PillButton(label, { model.changeTheme(mode) }, primary = model.themeMode == mode)
                    }
                }
                Toggle("ホームのアプリアイコンをモノクロに", model.monoIcons) { model.changeMonoIcons(it) }
                Gap(18.dp)
                SectionTitle("端のタブ（押すと名前の変更・並べ替え）", Modifier.padding(horizontal = 10.dp))
            }
            itemsIndexed(model.tabs, key = { _, t -> t.id }) { i, t ->
                var menu by remember { mutableStateOf(false) }
                Box {
                    RowButton(zebra = i, onClick = { menu = true }) {
                        Dot(p.group(t.kind.group))
                        TwoLines(
                            t.label,
                            when {
                                t.kind == TabKind.FOLDER && t.folderUri == null -> "フォルダ · 未割り当て"
                                t.kind == TabKind.FOLDER && t.nasPath != null -> "フォルダ · NAS " + t.nasPath!!.ifEmpty { "/" }
                                t.kind == TabKind.FOLDER -> "フォルダ · " + android.net.Uri.decode(t.folderUri!!.substringAfterLast('/')).substringAfter(':')
                                else -> t.kind.defaultLabel + (t.kind.key?.let { " · Alt+$it" } ?: "")
                            },
                            Modifier.weight(1f),
                        )
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("名前を変える") }, onClick = { menu = false; renaming = t })
                        if (t.kind == TabKind.FOLDER) DropdownMenuItem(text = { Text("端末のフォルダを選ぶ") }, onClick = { menu = false; actions.pickFolder(t.id) })
                        if (t.kind == TabKind.FOLDER && model.nas.configured()) DropdownMenuItem(
                            text = { Text("NAS の今のフォルダ（${model.nasState.path.ifEmpty { "/" }}）にする") },
                            onClick = { menu = false; model.assignNasFolder(t.id, model.nasState.path) },
                        )
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
                            DropdownMenuItem(text = { Text(k.defaultLabel) }, onClick = { adding = false; model.addTab(k) })
                        }
                    }
                }
                Gap(18.dp)
                Note("物理キーボード：Alt＋1〜9 でフォルダのタブ、Alt＋C 予定・M メール・L LINE・P 人・A アプリ・B 栞・X Clip・R 記録・H Hatch。ホームで文字を打つとすぐ検索、Esc で閉じる。")
                Note("ウィジェットのタブは、端のタブの下の「＋」で足します。並びと大きさは閉じた画面と開いた画面で別々です。")
                Note("Theta Home Hatch ${BuildConfigInfo.versionName(actions.activity)}")
            }
        }
    }

    if (nasDialog) NasDialog(model) { nasDialog = false }
    if (importing) ImportWidgetsDialog(model) { importing = false }
    if (sorting) SortGuide(model) { sorting = false }
    renamingPreset?.let { old ->
        var name by remember(old) { mutableStateOf(old) }
        AlertDialog(
            onDismissRequest = { renamingPreset = null },
            title = { Text(if (old.isEmpty()) "候補を追加" else "候補の名前") },
            text = { Field(name, { name = it.take(12) }, "名前") },
            confirmButton = {
                TextButton(onClick = {
                    if (old.isEmpty()) model.addPreset(name) else model.renamePreset(old, name)
                    renamingPreset = null
                }, enabled = name.isNotBlank()) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { renamingPreset = null }) { Text("やめる") } },
        )
    }
    if (importConfirm) {
        AlertDialog(
            onDismissRequest = { importConfirm = false },
            title = { Text("設定を読み込む") },
            text = { Text("今の設定（タブ・カテゴリー・栞など）は、読み込んだファイルの内容で置き換わります。ウィジェットはこの端末のものを残します。") },
            confirmButton = {
                TextButton(onClick = {
                    importConfirm = false
                    actions.openDocument(arrayOf("application/json", "text/plain", "application/octet-stream")) { model.importSettings(it) }
                }) { Text("ファイルを選ぶ") }
            },
            dismissButton = { TextButton(onClick = { importConfirm = false }) { Text("やめる") } },
        )
    }

    renamingCategory?.let { c ->
        var name by remember(c.id) { mutableStateOf(c.name) }
        AlertDialog(
            onDismissRequest = { renamingCategory = null },
            title = { Text("カテゴリーの名前") },
            text = { Field(name, { name = it.take(12) }, "名前") },
            confirmButton = { TextButton(onClick = { model.renameCategory(c, name); renamingCategory = null }) { Text("保存") } },
            dismissButton = { TextButton(onClick = { renamingCategory = null }) { Text("やめる") } },
        )
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
