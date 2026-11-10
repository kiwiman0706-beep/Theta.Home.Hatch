package app.thetahatch.home.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.thetahatch.home.HomeModel
import app.thetahatch.home.data.Extract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * 貼り付けて登録：文章（貼り付け・声）や画像（スクショ・写真）から、Gemini で連絡先（kind = "contact"）か予定（"event"）を読み取る。
 * 読み取った下書きを直してから、端末の「連絡先に追加」「予定を追加」の画面へ渡す。
 */
@Composable
fun RegisterDialog(model: HomeModel, kind: String, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    val actions = LocalActions.current
    val ctx = actions.activity
    val scope = rememberCoroutineScope()
    val contact = kind == "contact"
    var text by remember { mutableStateOf("") }
    var partial by remember { mutableStateOf("") }
    val images = remember { mutableStateListOf<Bitmap>() }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val contacts = remember { mutableStateListOf<Extract.Contact>() }
    val events = remember { mutableStateListOf<Extract.Event>() }
    var read by remember { mutableStateOf(false) }
    val voice = rememberVoice(
        onPartial = { partial = it },
        onFinal = { list -> text = listOf(text, list.first()).filter { it.isNotBlank() }.joinToString("\n"); partial = "" },
    )

    fun addImage(uri: Uri) {
        scope.launch {
            val b = withContext(Dispatchers.IO) { loadScaled(ctx, uri, 1600) }
            if (b != null) images += b else actions.toast("画像を読めませんでした")
        }
    }
    fun paste() {
        val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
        val clip = cm?.primaryClip
        val t = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString().orEmpty()
        val u = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
        when {
            u != null -> addImage(u)
            t.isNotBlank() -> text = listOf(text, t).filter { it.isNotBlank() }.joinToString("\n")
            else -> actions.toast("クリップボードが空です")
        }
    }
    fun run() {
        voice.stop()
        val body = listOf(text, partial).filter { it.isNotBlank() }.joinToString("\n")
        if (body.isBlank() && images.isEmpty()) { actions.toast("文章か画像を入れてください"); return }
        busy = true; error = null
        scope.launch {
            val jpegs = withContext(Dispatchers.Default) {
                images.map { b -> ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray() }
            }
            runCatching { model.extractForRegister(kind, body, jpegs) }
                .onSuccess { json ->
                    runCatching {
                        if (contact) { contacts.clear(); contacts += Extract.parseContacts(json) } else { events.clear(); events += Extract.parseEvents(json) }
                    }.onFailure { error = "読み取りの形が違いました：${it.message}" }
                    read = true
                }
                .onFailure { error = it.message }
            busy = false
        }
    }

    AlertDialog(
        onDismissRequest = { voice.stop(); onDismiss() },
        title = { Text(if (contact) "貼り付けて連絡先に" else "貼り付けて予定に") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!read) {
                    Field(
                        value = listOf(text, partial).filter { it.isNotBlank() }.joinToString("\n"),
                        onChange = { text = it; partial = "" },
                        placeholder = if (contact) "メールの署名・名刺の文字などを貼る・話す" else "メールの本文・案内などを貼る・話す（「来週火曜 10 時 歯医者」でも）",
                        singleLine = false,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
                    )
                    FlowRow2 {
                        PillButton(if (voice.listening) "● 聞き取り中" else "話して入れる", { if (voice.listening) voice.stop() else voice.start() }, primary = voice.listening)
                        PillButton("貼り付け", { paste() })
                        PillButton("画像を選ぶ", { actions.openDocument(arrayOf("image/*")) { addImage(it) } })
                    }
                    if (images.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        images.forEachIndexed { i, b ->
                            Image(b.asImageBitmap(), "画像 ${i + 1}（押すと外す）", Modifier.size(64.dp).clickable(onClickLabel = "外す") { images.removeAt(i) }, contentScale = ContentScale.Crop)
                        }
                    }
                    Note("Gemini が読み取ります（設定のアイコンの Gemini と同じキー）。読み取ったものは、確かめてから端末の画面で保存します。")
                } else if (contact) {
                    if (contacts.isEmpty()) Text("連絡先が見つかりませんでした", color = p.muted, fontSize = 13.sp)
                    contacts.forEachIndexed { i, c -> ContactDraft(c, { contacts[i] = it }) { actions.start(Extract.contactIntent(contacts[i])) } }
                } else {
                    if (events.isEmpty()) Text("予定が見つかりませんでした", color = p.muted, fontSize = 13.sp)
                    events.forEachIndexed { i, e -> EventDraft(e, { events[i] = it }) { actions.start(Extract.eventIntent(events[i])) } }
                }
                if (busy) Text("読み取っています…", color = p.accent, fontSize = 13.sp)
                error?.let { Text(it, color = p.accent, fontSize = 13.sp) }
            }
        },
        confirmButton = {
            if (!read) TextButton(onClick = { if (!busy) run() }) { Text("読み取る") }
            else TextButton(onClick = { read = false }) { Text("入れ直す") }
        },
        dismissButton = { TextButton(onClick = { voice.stop(); onDismiss() }) { Text("閉じる") } },
    )
}

@Composable
private fun ContactDraft(c: Extract.Contact, onChange: (Extract.Contact) -> Unit, onAdd: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionTitle(c.name.ifEmpty { c.company.ifEmpty { "連絡先" } })
        Field(c.name, { onChange(c.copy(name = it)) }, "名前")
        Field(c.kana, { onChange(c.copy(kana = it)) }, "よみ")
        Field(c.company, { onChange(c.copy(company = it)) }, "会社")
        Field(listOf(c.department, c.title).filter { it.isNotEmpty() }.joinToString(" "), { onChange(c.copy(department = "", title = it)) }, "部署・役職")
        Field(c.phones.joinToString(" / "), { onChange(c.copy(phones = it.split("/").map(String::trim).filter(String::isNotEmpty))) }, "電話（/ で区切る）")
        Field(c.emails.joinToString(" / "), { onChange(c.copy(emails = it.split("/").map(String::trim).filter(String::isNotEmpty))) }, "メール（/ で区切る）")
        Field(c.address, { onChange(c.copy(address = it)) }, "住所", singleLine = false)
        Field(c.url, { onChange(c.copy(url = it)) }, "ウェブ")
        Field(c.note, { onChange(c.copy(note = it)) }, "メモ", singleLine = false)
        PillButton("連絡先に追加", onAdd, primary = true)
    }
}

@Composable
private fun EventDraft(e: Extract.Event, onChange: (Extract.Event) -> Unit, onAdd: () -> Unit) {
    val p = LocalPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionTitle(e.title.ifEmpty { "予定" })
        Field(e.title, { onChange(e.copy(title = it)) }, "題名")
        Field(e.start.replace('T', ' '), { onChange(e.copy(start = it.trim().replace(' ', 'T'))) }, "始まり（2026-11-10 14:00）")
        Field(e.end.replace('T', ' '), { onChange(e.copy(end = it.trim().replace(' ', 'T'))) }, "終わり")
        PillButton(if (e.allDay) "終日（押すと時刻あり）" else "時刻あり（押すと終日）", { onChange(e.copy(allDay = !e.allDay)) })
        Field(e.location, { onChange(e.copy(location = it)) }, "場所")
        Field(e.note, { onChange(e.copy(note = it)) }, "メモ", singleLine = false)
        if (Extract.millis(e.start) == null) Text("始まりの日時が読めません（2026-11-10 14:00 の形に）", color = p.accent, fontSize = 12.sp)
        PillButton("予定に追加", onAdd, primary = true, enabled = Extract.millis(e.start) != null)
    }
}


/** 長い辺が max を超えないように縮めて読む。 */
private fun loadScaled(ctx: android.content.Context, uri: Uri, max: Int): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > max * 2) sample *= 2
    val b = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return@runCatching null
    val scale = max.toFloat() / maxOf(b.width, b.height)
    if (scale >= 1f) b else Bitmap.createScaledBitmap(b, (b.width * scale).toInt(), (b.height * scale).toInt(), true)
}.getOrNull()
