package jp.yaegaki.theta.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.yaegaki.theta.home.HomeModel
import jp.yaegaki.theta.home.data.SavedQuery
import jp.yaegaki.theta.home.data.Store

/**
 * 保存した検索（仮想フォルダ）を作る・直す。条件は選ぶだけで、Drive の検索式や NAS の API を知らなくてよい。
 * 本文・スター・共有はドライブだけ（NAS の File Station には本文検索が無い）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QueryDialog(model: HomeModel, initial: SavedQuery, inline: Boolean = false, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    val drive = initial.source == "drive"
    val isNew = model.query(initial.id) == null
    var name by remember { mutableStateOf(initial.name) }
    var kinds by remember { mutableStateOf(initial.kinds) }
    var nameContains by remember { mutableStateOf(initial.nameContains) }
    var text by remember { mutableStateOf(initial.text) }
    var days by remember { mutableStateOf(initial.days) }
    var starred by remember { mutableStateOf(initial.starred) }
    var shared by remember { mutableStateOf(initial.shared) }
    var folder by remember { mutableStateOf(initial.folderId to initial.folderName) }
    val q = initial.copy(
        name = name.trim().ifEmpty { "SEARCH" }, kinds = kinds, nameContains = nameContains, text = text, days = days,
        starred = starred, shared = shared, folderId = folder.first, folderName = folder.second,
    )

    @Composable
    fun chip(label: String, on: Boolean, onClick: () -> Unit) {
        Text(
            label, color = if (on) p.onAccent else p.fg, fontSize = 13.sp,
            modifier = Modifier.background(if (on) p.accent else Color.Transparent).border(1.dp, if (on) p.accent else p.line)
                .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }

    val form: @Composable () -> Unit = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text((if (drive) "GOOGLE DRIVE" else "NAS") + "  //  " + q.summary(), color = p.muted, fontSize = 11.sp, fontFamily = Hud.mono, lineHeight = 16.sp)
                SectionTitle("フォルダの名前")
                Field(name, { name = it }, "例 請求書")
                SectionTitle("種類（選ばなければすべて）")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SavedQuery.KINDS.forEach { (k, label) -> chip(label, k in kinds) { kinds = if (k in kinds) kinds - k else kinds + k } }
                }
                SectionTitle("ファイル名に含む")
                Field(nameContains, { nameContains = it }, "言葉（空ならすべて）")
                if (drive) {
                    SectionTitle("本文に含む（PDF・文書の中身も）")
                    Field(text, { text = it }, "言葉（空ならすべて）")
                }
                SectionTitle("更新した時期")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SavedQuery.DAYS.forEach { (d, label) -> chip(label, days == d) { days = d } }
                }
                if (drive) {
                    SectionTitle("ほか")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        chip("スター付き", starred) { starred = !starred }
                        chip("共有されたもの", shared) { shared = !shared }
                    }
                }
                SectionTitle("場所")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    chip("どこでも", folder.first.isEmpty()) { folder = "" to "" }
                    if (initial.folderId.isNotEmpty()) chip(initial.folderName, folder.first == initial.folderId) { folder = initial.folderId to initial.folderName }
                }
                Note(
                    if (drive) "場所はフォルダを長押し →「この中の検索フォルダを作る」で選べます（そのフォルダの直下だけ）。"
                    else "場所はフォルダを長押し →「この中の検索フォルダを作る」で選べます（サブフォルダも含む）。どこでもにすると共有フォルダ全部を探すので、少し時間がかかります。",
                )
            }
    }
    if (inline) {
        Column(Modifier.background(p.bg).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (isNew) "検索フォルダを作る" else "検索フォルダを直す", color = p.fg, fontSize = 20.sp)
            form()
        }
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "検索フォルダを作る" else "検索フォルダを直す") },
        text = { form() },
        confirmButton = {
            TextButton(onClick = { model.saveQuery(q); onDismiss() }) { Text(if (isNew) "作る" else "保存") }
        },
        dismissButton = {
            if (!isNew) TextButton(onClick = { model.deleteQuery(initial.id); onDismiss() }) { Text("消す") }
            else TextButton(onClick = onDismiss) { Text("やめる") }
        },
    )
}

/** 新しい検索フォルダの下書き。 */
fun newQuery(source: String, folderId: String = "", folderName: String = "") =
    SavedQuery(SavedQuery.PREFIX + Store.newId(), folderName.ifEmpty { "SEARCH" }, source, folderId = folderId, folderName = folderName)
