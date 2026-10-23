package jp.yaegaki.theta.home.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.yaegaki.theta.home.HomeModel
import jp.yaegaki.theta.home.data.Files
import jp.yaegaki.theta.home.data.TabSpec

/** タブに割り当てたフォルダの中を、ドロワーの中だけで辿る。 */
@Composable
fun FolderDrawer(model: HomeModel, t: TabSpec) {
    val p = LocalPalette.current
    val actions = LocalActions.current
    val state = model.folders[t.id]
    LaunchedEffect(t.id, t.folderUri) {
        if (state == null || state.stack.isEmpty() && state.error == null) model.openRoot(t)
    }

    if (t.folderUri == null) {
        NeedPermission("このタブに、よく使うフォルダを 1 つ割り当てます（端末内・SD カード・Google ドライブなど、ファイル選択画面で選べる場所）。", "フォルダを選ぶ") {
            actions.pickFolder(t.id)
        }
        return
    }
    if (state?.error != null) {
        NeedPermission(state.error, "フォルダを選び直す") { actions.pickFolder(t.id) }
        return
    }
    val s = state ?: return
    var menuFor by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 6.dp, end = 12.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (s.stack.size > 1) GlyphButton(Glyph.BACK, "上のフォルダへ", { model.up(t.id) })
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                s.stack.forEachIndexed { i, (_, name) ->
                    if (i > 0) Text(" › ", color = p.faint, fontSize = 12.sp)
                    Text(
                        name,
                        color = if (i == s.stack.lastIndex) p.fg else p.muted,
                        fontSize = 12.sp,
                        modifier = Modifier.clickable(onClickLabel = "ここへ戻る") { model.jump(t.id, i) }.padding(vertical = 12.dp),
                    )
                }
            }
        }
        Box(Modifier.weight(1f)) {
            DrawerList {
                if (s.loading && s.entries.isEmpty()) item { Note("読み込み中…") }
                if (!s.loading && s.entries.isEmpty()) item { Note("空のフォルダです") }
                items(s.entries, key = { it.uri.toString() }) { e ->
                    Box {
                        RowButton(
                            onClick = {
                                if (e.isDir) {
                                    model.enter(t.id, e)
                                } else {
                                    model.rememberOpened(t.label, e)
                                    actions.start(Files.openIntent(e.uri, e.mime))
                                }
                            },
                            onLongClick = if (e.isDir) null else ({ menuFor = e.uri.toString() }),
                            label = if (e.isDir) "フォルダを開く" else "ファイルを開く",
                        ) {
                            Badge(Files.badge(e), highlight = e.isDir)
                            TwoLines(
                                e.name,
                                if (e.isDir) null else listOf(if (e.modified > 0) shortTime(e.modified) else null, Files.sizeText(e.size)).filterNotNull().joinToString(" · "),
                                Modifier.weight(1f),
                            )
                        }
                        DropdownMenu(expanded = menuFor == e.uri.toString(), onDismissRequest = { menuFor = null }) {
                            DropdownMenuItem(text = { Text("開く") }, onClick = {
                                menuFor = null; model.rememberOpened(t.label, e); actions.start(Files.openIntent(e.uri, e.mime))
                            })
                            DropdownMenuItem(text = { Text("アプリを選んで開く") }, onClick = {
                                menuFor = null; actions.start(android.content.Intent.createChooser(Files.openIntent(e.uri, e.mime), "開く"))
                            })
                            DropdownMenuItem(text = { Text("共有") }, onClick = {
                                menuFor = null; actions.start(Files.shareIntent(e.uri, e.mime, e.name))
                            })
                        }
                    }
                }
                item {
                    Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Note("長押しで共有・アプリを選んで開く。")
                        FlowRow2 { PillButton("割り当てるフォルダを変える", { actions.pickFolder(t.id) }) }
                    }
                }
            }
        }
    }
}
