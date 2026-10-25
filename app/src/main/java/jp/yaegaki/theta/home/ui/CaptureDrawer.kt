package jp.yaegaki.theta.home.ui

import android.Manifest
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import jp.yaegaki.theta.home.HomeModel
import jp.yaegaki.theta.home.capture.Capture
import jp.yaegaki.theta.home.capture.CaptureKind
import jp.yaegaki.theta.home.capture.Ink
import jp.yaegaki.theta.home.capture.RecordingService
import jp.yaegaki.theta.home.capture.Stroke
import jp.yaegaki.theta.home.data.Files
import kotlinx.coroutines.delay
import java.io.File

/** 思いついたことをすぐ残す：録音・写真・動画・手書き・テキスト。保存先はアプリ専用領域。 */
@Composable
fun CaptureDrawer(model: HomeModel) {
    val p = LocalPalette.current
    val actions = LocalActions.current
    val ctx = actions.activity
    val recordingSince by RecordingService.startedAt.collectAsState()
    var inking by remember { mutableStateOf(false) }
    var writing by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(recordingSince) {
        while (recordingSince != null) {
            now = System.currentTimeMillis(); delay(1000)
        }
        model.refreshCaptures()
    }

    fun camera(video: Boolean) {
        val f = Capture.newFile(ctx, CaptureKind.CAMERA, if (video) ".mp4" else ".jpg")
        f.createNewFile()
        val uri = Files.localUri(ctx, f)
        val done: (Boolean) -> Unit = { ok -> if (!ok || f.length() == 0L) f.delete(); model.refreshCaptures() }
        if (video) actions.captureVideo(uri, done) else actions.takePicture(uri, done)
    }

    fun record() {
        if (recordingSince != null) {
            RecordingService.stop(ctx); return
        }
        val need = buildList {
            if (!model.has(Manifest.permission.RECORD_AUDIO)) add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 33 && !model.has(Manifest.permission.POST_NOTIFICATIONS)) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (need.isEmpty()) RecordingService.start(ctx)
        else actions.requestPermissions(need.toTypedArray()) {
            if (model.has(Manifest.permission.RECORD_AUDIO)) RecordingService.start(ctx) else actions.toast("録音にはマイクの許可が必要です")
        }
    }

    DrawerList {
        item {
            FlowRow2 {
                val rec = recordingSince
                PillButton(if (rec != null) "■ 停止 ${(now - rec) / 1000}秒" else "● 録音", ::record, primary = rec != null)
                PillButton("写真", { camera(false) })
                PillButton("動画", { camera(true) })
                PillButton("手書き", { inking = true })
                PillButton("テキスト", { writing = true })
            }
            Spacer(Modifier.height(12.dp))
        }
        itemsIndexed(model.captures, key = { _, it -> it.second.absolutePath }) { zi, (kind, f) ->
            Column {
                RowButton(zebra = zi, onClick = { expanded = if (expanded == f.absolutePath) null else f.absolutePath }) {
                    Badge(kind.label)
                    TwoLines(f.name, "${shortTime(f.lastModified())} · ${Files.sizeText(f.length())}", Modifier.weight(1f))
                }
                if (expanded == f.absolutePath) {
                    val uri = Files.localUri(ctx, f)
                    val mime = Files.mimeOf(f.name)
                    FlowRow2 {
                        PillButton("開く", { openFile(model, actions, jp.yaegaki.theta.home.data.ViewItem(uri, f.name, mime)) })
                        PillButton("共有", { actions.start(Files.shareIntent(uri, mime, f.name)) })
                        PillButton("書き出す", { actions.export(uri, f.name, mime) })
                        PillButton("削除", { f.delete(); model.refreshCaptures() })
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
        if (model.captures.isEmpty()) item { Note("まだありません。") }
        item { Note("記録はこのアプリの中に保存します。アプリを消すと一緒に消えるので、残したいものは「書き出す」で好きなフォルダへ。") }
    }

    if (inking) InkDialog(onDismiss = { inking = false }) { file -> inking = false; model.refreshCaptures(); actions.toast("保存しました：${file.name}") }
    if (writing) TextNoteDialog(onDismiss = { writing = false }) { file -> writing = false; model.refreshCaptures(); actions.toast("保存しました：${file.name}") }
}

/** 手書き。ペン優先なら指の入力は無視する（手のひらで線が出ない）。筆圧で太さが変わる。消しゴム側は線を消す。 */
@Composable
private fun InkDialog(onDismiss: () -> Unit, onSaved: (File) -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalActions.current.activity
    val density = LocalDensity.current.density
    val strokes = remember { mutableStateListOf<Stroke>() }
    var tick by remember { mutableIntStateOf(0) }
    var penOnly by remember { mutableStateOf(true) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(p.panel).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("戻す", { if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex) })
                PillButton("全部消す", { strokes.clear() })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = penOnly, onCheckedChange = { penOnly = it })
                    Text("ペン優先", color = p.fg, fontSize = 13.sp)
                }
                Spacer(Modifier.weight(1f))
                PillButton("やめる", onDismiss)
                PillButton("保存", {
                    if (strokes.isEmpty()) return@PillButton
                    val f = Capture.newFile(ctx, CaptureKind.INK, ".png")
                    val bmp = Ink.render(strokes, size.width, size.height, density, android.graphics.Color.BLACK, android.graphics.Color.WHITE)
                    f.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                    bmp.recycle()
                    onSaved(f)
                }, primary = true)
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(androidx.compose.ui.graphics.Color.White)
                    .onSizeChanged { size = it }
                    .pointerInput(penOnly) {
                        awaitPointerEventScope {
                            var current: Stroke? = null
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: continue
                                if (penOnly && change.type == PointerType.Touch) continue
                                if (change.type == PointerType.Eraser) {
                                    if (change.pressed) {
                                        val pos = change.position
                                        strokes.removeAll { s -> s.points.any { (it[0] - pos.x) * (it[0] - pos.x) + (it[1] - pos.y) * (it[1] - pos.y) < 400f * density } }
                                    }
                                    change.consume(); continue
                                }
                                if (change.pressed && !change.previousPressed) {
                                    current = Stroke().also { strokes.add(it) }
                                }
                                if (change.pressed) {
                                    change.historical.forEach { h -> current?.points?.add(floatArrayOf(h.position.x, h.position.y, change.pressure)) }
                                    current?.points?.add(floatArrayOf(change.position.x, change.position.y, change.pressure))
                                    tick++
                                    change.consume()
                                } else {
                                    current = null
                                }
                            }
                        }
                    },
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    // 点を足すたびに描き直すため、tick を読む
                    if (tick < 0) return@Canvas
                    for (s in strokes) {
                        if (s.points.size == 1) {
                            val pt = s.points[0]
                            drawCircle(androidx.compose.ui.graphics.Color.Black, Ink.widthAt(pt[2], density) / 2f, Offset(pt[0], pt[1]))
                        }
                        for (i in 1 until s.points.size) {
                            val a = s.points[i - 1]
                            val b = s.points[i]
                            drawLine(androidx.compose.ui.graphics.Color.Black, Offset(a[0], a[1]), Offset(b[0], b[1]), Ink.widthAt(b[2], density), StrokeCap.Round)
                        }
                    }
                }
                if (strokes.isEmpty()) {
                    Text(if (penOnly) "S ペンで書いてください（指の入力は無視します）" else "指かペンで書いてください", color = androidx.compose.ui.graphics.Color(0xFF5F635C), fontSize = 13.sp, modifier = Modifier.align(Alignment.Center))
                }
            }
        }
    }
}

@Composable
private fun TextNoteDialog(onDismiss: () -> Unit, onSaved: (File) -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalActions.current.activity
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(p.panel).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("テキスト", color = p.fg, fontSize = 18.sp, modifier = Modifier.weight(1f))
                PillButton("やめる", onDismiss)
                PillButton("保存", {
                    if (body.isBlank()) return@PillButton
                    val f = Capture.newFile(ctx, CaptureKind.TEXT, ".md", title)
                    f.writeText(body)
                    onSaved(f)
                }, primary = true, enabled = body.isNotBlank())
            }
            Field(title, { title = it }, "ファイル名（なくてもよい）")
            Field(body, { body = it }, "メモ", Modifier.weight(1f).fillMaxWidth(), singleLine = false)
        }
    }
}
