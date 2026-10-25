package jp.yaegaki.theta.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import jp.yaegaki.theta.home.data.BoardItem
import jp.yaegaki.theta.home.data.Store
import jp.yaegaki.theta.home.data.TabKind
import jp.yaegaki.theta.home.data.TabSpec
import kotlin.math.roundToInt

/**
 * ふつうのタブ（BOARD）。ウィジェット・フォルダ・ファイル・ページを縦に並べる。幅は全体か半分（半分どうしは横に並ぶ）。
 * ふだんは中身だけ。編集（見出しの歯車）にすると、下の帯をなぞって高さ・全体／半分・上へ下へ・外す・足す。
 * 並びは両方の画面で共通、高さは画面（閉じた／開いた）ごと。
 */
@Composable
fun BoardDrawer(model: HomeModel, t: TabSpec, editing: Boolean) {
    val items = model.boardItems(t.id)
    var adding by remember { mutableStateOf(false) }
    // 縦いっぱいのものがあれば、それが残りの高さを分け合う（全体はスクロールしない）。無ければ中身の高さで、はみ出たらスクロール
    val fills = items.any { it.fill }
    Column(
        (if (fills) Modifier.fillMaxSize() else Modifier.verticalScroll(rememberScrollState())).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (items.isEmpty()) Note("ウィジェット・フォルダ・画像や PDF・Web ページ・予定・通知などを、このタブに好きな数だけ並べられます。")
        rows(items).forEach { row ->
            val fillRow = row.any { it.fill }
            Row(if (fillRow) Modifier.weight(1f) else Modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { item ->
                    Box(Modifier.weight(1f).then(if (fillRow) Modifier.fillMaxHeight() else Modifier)) {
                        ItemView(model, t, item, items.indexOf(item), items.size, editing, fillRow)
                    }
                }
            }
        }
        if (editing || items.isEmpty()) FlowRow2 { PillButton("＋ 足す", { adding = true }, primary = true) }
    }
    if (adding) AddItemDialog(model, t.id) { adding = false }
}

/** 半分どうしが続けば 1 段に 2 つ、ほかは 1 段に 1 つ。 */
private fun rows(items: List<BoardItem>): List<List<BoardItem>> {
    val out = mutableListOf<List<BoardItem>>()
    var i = 0
    while (i < items.size) {
        val a = items[i]
        val b = items.getOrNull(i + 1)
        if (a.half && b?.half == true) {
            out += listOf(a, b)
            i += 2
        } else {
            out += listOf(a)
            i++
        }
    }
    return out
}

@Composable
private fun ItemView(model: HomeModel, t: TabSpec, item: BoardItem, index: Int, count: Int, editing: Boolean, fillRow: Boolean) {
    val p = LocalPalette.current
    val density = LocalDensity.current.density
    val maxH = LocalConfiguration.current.screenHeightDp - 120
    val saved = item.height(model.profile)
    var h by remember(item.id, saved) { mutableIntStateOf(saved) }
    // 1 つだけ置いた縦いっぱいのものは枠を付けない（今までの専用のタブと同じ見た目）
    val framed = item.type != BoardItem.WIDGET && !(fillRow && count == 1)
    Column(if (fillRow) Modifier.fillMaxSize() else Modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Box(Modifier.fillMaxWidth().then(if (fillRow) Modifier.weight(1f) else Modifier.height(h.dp)).then(if (framed) Modifier.border(lineWidth, p.line) else Modifier)) {
            when (item.type) {
                BoardItem.WIDGET -> WidgetView(model, item, h)
                BoardItem.FOLDER -> FolderDrawer(model, model.specOf(item).let { if (item.label.isEmpty()) it.copy(label = t.label) else it })
                BoardItem.FILE -> FileDrawer(model, model.specOf(item))
                BoardItem.WEB -> WebDrawer(model, model.specOf(item))
                in BoardItem.PANELS -> PanelView(model, item.type)
                else -> Note("この版では表示できないものです")
            }
        }
        if (editing && !item.fill) {
            // 下の帯：なぞって高さを変える
            Box(
                Modifier.fillMaxWidth().height(22.dp).background(p.raised)
                    .semantics { contentDescription = "なぞって高さを変える" }
                    .pointerInput(item.id) {
                        var start = 0
                        var total = 0f
                        detectVerticalDragGestures(
                            onDragStart = { start = h; total = 0f },
                            onDragEnd = { model.setItemHeight(item.id, h) },
                        ) { change, d ->
                            change.consume()
                            total += d
                            h = (start + total / density).roundToInt().coerceIn(80, maxH.coerceAtLeast(80))
                        }
                    },
                contentAlignment = Alignment.Center,
            ) { Box(Modifier.width(40.dp).height(3.dp).background(p.accent)) }
        }
        if (editing) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 半分の幅では詰めて出す
                Text(if (item.half) (if (item.fill) "FILL" else "${h}dp") else "${kindName(item.type)}  ${if (item.fill) "FILL" else "${h}dp"}",
                    color = p.faint, fontSize = 11.sp, fontFamily = Hud.mono, maxLines = 1, modifier = Modifier.weight(1f))
                TextButton(onClick = { model.updateBoardItem(item.copy(fill = !item.fill)) }) { Text(if (item.fill) "高さを決める" else "縦いっぱい", color = p.fg, fontSize = 12.sp) }
                TextButton(onClick = { model.updateBoardItem(item.copy(half = !item.half)) }) { Text(if (item.half) "全幅" else "半分", color = p.fg, fontSize = 12.sp) }
                if (index > 0) TextButton(onClick = { model.moveBoardItem(item.id, -1) }) { Text("▲", color = p.fg) }
                if (index < count - 1) TextButton(onClick = { model.moveBoardItem(item.id, 1) }) { Text("▼", color = p.fg) }
                TextButton(onClick = { model.removeBoardItem(item.id) }) { Text("外す", color = p.accent, fontSize = 12.sp) }
            }
        }
    }
}

private fun kindName(type: String) = when (type) {
    in BoardItem.PANELS -> BoardItem.PANELS[type]!!.defaultLabel
    BoardItem.WIDGET -> "WIDGET"
    BoardItem.FOLDER -> "FOLDER"
    BoardItem.FILE -> "FILE"
    BoardItem.WEB -> "WEB"
    else -> type.uppercase()
}

/** ウィジェット 1 つ。幅は置いた枠いっぱい、高さは決めた高さ。 */
@Composable
private fun WidgetView(model: HomeModel, item: BoardItem, h: Int) {
    val activity = LocalActions.current.activity
    val host = (activity.application as HomeApp).widgets
    val id = item.ref.toIntOrNull()
    when {
        model.safeMode -> Note("セーフモードなので、ウィジェットは止めています。")
        id == null || host.info(id) == null -> Note("このウィジェットは使えなくなりました（アプリが消えたなど）。編集から外してください。")
        else -> BoxWithConstraints(Modifier.fillMaxSize()) {
            val w = maxWidth.value.roundToInt()
            AndroidView(
                factory = { host.view(activity, id)!! },
                update = { v -> resize(v, w, h) },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** 足すものを選ぶ：ウィジェット・端末のフォルダ・ほかのタブと同じフォルダ・画像や PDF・Web ページ・予定や通知など。 */
@Composable
private fun AddItemDialog(model: HomeModel, tabId: String, onDismiss: () -> Unit) {
    val actions = LocalActions.current
    var widget by remember { mutableStateOf(false) }
    if (widget) {
        AddWidgetDialog(model, intoBoard = tabId) { widget = false; onDismiss() }
        return
    }
    val folderTabs = model.folderPlaces().filter { it.folderUri != null }.distinctBy { it.folderUri }
    val tabLabel = model.tab(tabId)?.label.orEmpty()
    fun add(type: String, ref: String = "", label: String = ""): BoardItem =
        BoardItem(Store.newId(), type, ref, label).also { model.addBoardItem(tabId, it) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("このタブに足す") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle("WIDGET")
                PillButton("＋ ウィジェット", { widget = true }, primary = true)
                SectionTitle("FOLDER")
                PillButton("＋ 端末のフォルダを選ぶ", {
                    val item = add(BoardItem.FOLDER, label = tabLabel)
                    onDismiss()
                    actions.pickFolder(item.id)
                })
                if (folderTabs.isNotEmpty()) {
                    Note("ほかのタブに置いたフォルダと同じ場所（NAS・ドライブのフォルダもここから）")
                    FlowRow2 {
                        folderTabs.forEach { f -> PillButton(f.label, { add(BoardItem.FOLDER, f.folderUri!!, f.label); onDismiss() }) }
                    }
                }
                SectionTitle("FILE・WEB")
                FlowRow2 {
                    PillButton("＋ 画像・PDF", {
                        onDismiss()
                        actions.openDocument(arrayOf("image/*", "application/pdf")) { uri ->
                            val item = add(BoardItem.FILE)
                            model.setTabFile(item.id, uri)
                        }
                    })
                    // URL は置いた枠の中で入れる
                    PillButton("＋ Web ページ", { add(BoardItem.WEB); onDismiss() })
                }
                SectionTitle("PANEL・予定や通知など")
                FlowRow2 {
                    BoardItem.PANELS.forEach { (type, kind) -> PillButton("＋ " + kind.defaultLabel, { add(type); onDismiss() }) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } },
    )
}
