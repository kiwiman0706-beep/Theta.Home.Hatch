package jp.yaegaki.theta.home.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.yaegaki.theta.home.HomeModel
import jp.yaegaki.theta.home.capture.Capture
import jp.yaegaki.theta.home.capture.CaptureKind
import jp.yaegaki.theta.home.data.Files
import java.io.File

/**
 * ピン留めの「入力」：声・文字・写真で書いて、そのまま LINE・メール・ほかへ送る。
 * どれを押しても、先に MemoHatch に残す（設定で切れる）。送り先と LINE の決まった宛先は「送り先」で選ぶ。
 */
@Composable
fun QuickInput(model: HomeModel) {
    val start = model.quickOpen ?: return
    androidx.compose.runtime.key(start) { QuickBody(model, start) }
}

@Composable
private fun QuickBody(model: HomeModel, startMode: String) {
    val p = LocalPalette.current
    val actions = LocalActions.current
    val ctx = actions.activity
    var mode by remember { mutableStateOf(startMode) }
    var text by remember { mutableStateOf("") }
    var partial by remember { mutableStateOf("") }
    var photo by remember { mutableStateOf<File?>(null) }
    var editTargets by remember { mutableStateOf(false) }
    val voice = rememberVoice(
        onPartial = { partial = it },
        onFinal = { list -> text = listOf(text, list.first()).filter { it.isNotBlank() }.joinToString(" "); partial = "" },
    )

    fun dropPhoto() { photo?.delete(); photo = null }
    fun close() { voice.stop(); dropPhoto(); model.quickOpen = null }
    fun shoot() {
        val f = Capture.newFile(ctx, CaptureKind.CAMERA, ".jpg")
        f.createNewFile()
        actions.takePicture(Files.localUri(ctx, f)) { ok ->
            if (ok && f.length() > 0L) { photo?.delete(); photo = f } else f.delete()
        }
    }
    fun send(target: String) {
        voice.stop()
        val body = listOf(text, partial).filter { it.isNotBlank() }.joinToString(" ")
        if (body.isBlank() && photo == null) { actions.toast("何も入っていません"); return }
        val uri: Uri? = photo?.let { Files.localUri(ctx, it) }
        // 送った写真は撮ったもの（CAPTURE）として残す
        photo = null
        model.sendQuick(body, uri, target, actions.start)
    }

    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) {
        when (startMode) {
            "text" -> runCatching { focus.requestFocus() }
            "voice" -> voice.start()
            "photo" -> shoot()
        }
    }
    BackHandler { close() }

    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClickLabel = "閉じる") { close() },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier.widthIn(max = 560.dp).fillMaxWidth().imePadding().navigationBarsPadding()
                .background(p.panel).border(lineWidth, p.line)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("INPUT", color = p.accent, fontSize = 12.sp, fontFamily = Hud.mono, modifier = Modifier.weight(1f))
                GlyphButton(Glyph.MIC, if (voice.listening) "聞き取りを止める" else "声で入れる", {
                    mode = "voice"
                    if (voice.listening) voice.stop() else voice.start()
                }, filled = voice.listening)
                GlyphButton(Glyph.PENCIL, "文字で入れる", { voice.stop(); mode = "text" }, filled = mode == "text")
                GlyphButton(Glyph.CAMERA, "写真を撮る", { voice.stop(); mode = "photo"; shoot() }, filled = mode == "photo" && photo != null)
                GlyphButton(Glyph.CLOSE, "閉じる", { close() })
            }
            Field(
                value = if (partial.isEmpty()) text else listOf(text, partial).filter { it.isNotBlank() }.joinToString(" "),
                onChange = { text = it; partial = "" },
                placeholder = when {
                    voice.listening -> "聞き取り中…"
                    mode == "photo" -> "写真に添える言葉（なくても可）"
                    else -> "ここに書く・マイクで話す"
                },
                singleLine = false,
                modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp).focusRequester(focus),
            )
            photo?.let { f ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Thumb(f)
                    Column(Modifier.weight(1f)) {
                        TextButton(onClick = { shoot() }) { Text("撮り直す", color = p.fg, fontSize = 13.sp) }
                        TextButton(onClick = { dropPhoto() }) { Text("写真を外す", color = p.accent, fontSize = 13.sp) }
                    }
                }
            }
            SectionTitle("SEND")
            FlowRow2 {
                model.quickTargets.forEachIndexed { i, t -> PillButton(model.quickTargetLabel(t), { send(t) }, primary = i == 0) }
                PillButton("メモだけ", { send("memo") })
                PillButton("送り先…", { editTargets = true })
            }
            Note(
                if (!model.quickMemo) "MemoHatch には残しません（送り先… で変えられます）"
                else if (model.memoInstalled()) "どれを押しても、先に MemoHatch に残します"
                else "MemoHatch が入っていないので、このホームの Clip に残します",
            )
        }
    }
    if (editTargets) TargetsDialog(model) { editTargets = false }
}

/** 撮った写真の小さな見本。 */
@Composable
private fun Thumb(f: File) {
    val bmp = remember(f, f.length()) {
        runCatching {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.path, o)
            var s = 1
            while (o.outWidth / (s * 2) >= 240 && o.outHeight / (s * 2) >= 240) s *= 2
            BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s })?.asImageBitmap()
        }.getOrNull()
    }
    val p = LocalPalette.current
    if (bmp != null) Image(bmp, "撮った写真", Modifier.size(96.dp).border(lineWidth, p.line), contentScale = ContentScale.Crop)
    else Box(Modifier.size(96.dp).border(lineWidth, p.line), contentAlignment = Alignment.Center) { GlyphIcon(Glyph.CAMERA, p.faint) }
}

/** 送り先を選ぶ：LINE（アプリ）・LINE の決まった宛先・メール・ほかへ共有・Clip、それと MemoHatch に残すか。 */
@Composable
private fun TargetsDialog(model: HomeModel, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    var chosen by remember { mutableStateOf(model.quickTargets) }
    val recipients = remember { runCatching { model.lineRecipients() }.getOrDefault(emptyList()) }
    // 選んである宛先は、今の最近の会話に無くても出しておく
    val lineTargets = (chosen.filter { it.startsWith("line:") } + recipients.map { (id, label) -> "line:$id|$label" })
        .distinctBy { it.substringBefore('|') }
    fun toggle(t: String) {
        val key = t.substringBefore('|')
        chosen = if (chosen.any { it.substringBefore('|') == key }) chosen.filterNot { it.substringBefore('|') == key } else chosen + t
    }

    @Composable
    fun Row1(t: String) {
        val on = chosen.any { it.substringBefore('|') == t.substringBefore('|') }
        RowButton(onClick = { toggle(t) }) {
            Checkbox(checked = on, onCheckedChange = null)
            Text(model.quickTargetLabel(t), color = p.fg, fontSize = 15.sp, modifier = Modifier.weight(1f).padding(start = 8.dp))
            if (on) Text("${chosen.indexOfFirst { it.substringBefore('|') == t.substringBefore('|') } + 1}", color = p.faint, fontSize = 11.sp, fontFamily = Hud.mono)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("送り先") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Note("押した順に並びます（1 番目が目立つボタン）")
                listOf("line", "mail", "share", "clip").forEach { Row1(it) }
                SectionTitle("LINE の決まった宛先")
                if (lineTargets.isEmpty()) Note("LINE で最近話した相手が見つかりません（このホームを既定のホームにして、LINE で一度共有すると出ます）")
                lineTargets.forEach { Row1(it) }
                Note("宛先を決めて送るのは試しの機能です。LINE が受け付けないときは、LINE の送り先を選ぶ画面になります")
                SectionTitle("MEMOHATCH")
                RowButton(onClick = { model.changeQuickMemo(!model.quickMemo) }) {
                    Text("いつも MemoHatch に残す", color = p.fg, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Switch(checked = model.quickMemo, onCheckedChange = { model.changeQuickMemo(it) })
                }
            }
        },
        confirmButton = { TextButton(onClick = { model.changeQuickTargets(chosen); onDismiss() }) { Text("決める") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("やめる") } },
    )
}
