package jp.yaegaki.theta.home.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.yaegaki.theta.home.HomeModel
import jp.yaegaki.theta.home.data.Apps
import jp.yaegaki.theta.home.data.Calendar
import jp.yaegaki.theta.home.data.ClipItem
import jp.yaegaki.theta.home.data.Contacts
import jp.yaegaki.theta.home.data.Files
import jp.yaegaki.theta.home.data.TabKind
import jp.yaegaki.theta.home.data.TabSpec
import jp.yaegaki.theta.home.notify.HatchNotificationListener
import jp.yaegaki.theta.home.notify.Notice
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** ドロワーの枠：見出し、ピン留め・閉じる、中身。 */
@Composable
fun DrawerFrame(model: HomeModel, id: String, docked: Boolean) {
    val p = LocalPalette.current
    val layout = LocalLayout.current
    val t = model.tab(id)
    val w = model.widgetTab(id)
    if (t == null && w == null) return
    val group = t?.kind?.group ?: 1
    val title = t?.label ?: w!!.label
    val kicker = when (group) {
        0 -> "FOLDER"
        1 -> "WIDGET"
        else -> "LAUNCH"
    }
    val code = if (t != null) "%02d".format(model.tabs.indexOf(t) + 1) else "W%02d".format(model.widgetTabs.indexOf(w) + 1)
    Column(Modifier.fillMaxSize()) {
        Stripes(Modifier.fillMaxWidth().height(5.dp), color = p.group(group))
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 6.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f)) {
                Text("[ $kicker ] // $code", color = p.group(group), fontSize = 11.sp, letterSpacing = 2.sp, fontFamily = Hud.mono)
                Text(title, color = p.fg, fontSize = 30.sp, fontFamily = Hud.display, lineHeight = 36.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (docked) {
                GlyphButton(Glyph.PIN, "ピン留めを外す", { model.unpin(id) }, filled = true)
            } else if (layout.maxDocked > 0) {
                GlyphButton(Glyph.PIN, "横に並べて出しておく", { model.pin(id, layout.maxDocked) })
            }
            if (!docked) GlyphButton(Glyph.CLOSE, "閉じる", { model.close() })
        }
        Ticks(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(6.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (w != null) {
                WidgetTabDrawer(model, w)
                return@Box
            }
            when (t!!.kind) {
                TabKind.FOLDER -> Unit
                TabKind.CALENDAR -> CalendarDrawer(model)
                TabKind.MAIL -> NoticeDrawer(mail = true)
                TabKind.LINE -> NoticeDrawer(mail = false)
                TabKind.PEOPLE -> PeopleDrawer(model)
                TabKind.APPS -> AppsDrawer(model)
                TabKind.BOOKMARKS -> BookmarkDrawer(model)
                TabKind.CLIP -> ClipDrawer(model)
                TabKind.CAPTURE -> CaptureDrawer(model)
                TabKind.HATCH -> HatchDrawer(model)
            }
        }
    }
}

/** ドロワーの中身を縦に並べる共通の入れ物。 */
@Composable
fun DrawerList(content: LazyListScope.() -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        content = content,
    )
}

@Composable
fun NeedPermission(text: String, button: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(text, color = LocalPalette.current.muted, fontSize = 14.sp, lineHeight = 22.sp)
        PillButton(button, onClick, primary = true)
    }
}

private val timeFmt = SimpleDateFormat("H:mm", Locale.JAPAN)
private val dayFmt = SimpleDateFormat("M/d", Locale.JAPAN)

fun shortTime(t: Long): String {
    val now = System.currentTimeMillis()
    val d = Date(t)
    return if (dayFmt.format(d) == dayFmt.format(Date(now))) timeFmt.format(d) else dayFmt.format(d)
}

// ---- 予定 ----

@Composable
private fun CalendarDrawer(model: HomeModel) {
    val p = LocalPalette.current
    val actions = LocalActions.current
    if (!model.has(Manifest.permission.READ_CALENDAR)) {
        NeedPermission("端末のカレンダー（Google カレンダーと同期しているもの）から予定を表示します。", "予定の読み取りを許可") {
            actions.requestPermissions(arrayOf(Manifest.permission.READ_CALENDAR)) { model.refreshEvents() }
        }
        return
    }
    var day by remember { mutableIntStateOf(0) }
    val start = remember {
        java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val oneDay = 24L * 3600_000
    val dayEvents = model.events.filter { e ->
        val b = e.localBegin
        val from = start + day * oneDay
        if (e.allDay) b < from + oneDay && b + (e.end - e.begin) > from else b in from until from + oneDay || (b < from && e.end > from)
    }
    DrawerList {
        item {
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (i in 0 until 7) {
                    val c = java.util.Calendar.getInstance().apply { timeInMillis = start + i * oneDay }
                    val on = i == day
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(0.dp))
                            .background(if (on) p.accent else p.raised)
                            .clickable(onClickLabel = "この日の予定") { day = i }
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("日月火水木金土"[c.get(java.util.Calendar.DAY_OF_WEEK) - 1].toString(), color = if (on) p.onAccent else p.muted, fontSize = 11.sp)
                        Text(c.get(java.util.Calendar.DAY_OF_MONTH).toString(), color = if (on) p.onAccent else p.fg, fontSize = 17.sp)
                    }
                }
            }
        }
        if (dayEvents.isEmpty()) item { Note("予定はありません") }
        items(dayEvents, key = { "${it.eventId}-${it.begin}" }) { e ->
            RowButton(onClick = { actions.start(Calendar.openIntent(e)) }) {
                Text(if (e.allDay) "終日" else timeFmt.format(Date(e.begin)), color = p.fg, fontSize = 15.sp, modifier = Modifier.width(48.dp))
                Box(Modifier.width(3.dp).height(32.dp).background(if (e.color != 0) Color(e.color) else p.widget))
                TwoLines(e.title, e.location, Modifier.weight(1f))
            }
        }
        item {
            FlowRow2 {
                PillButton("＋ 予定を追加", { actions.start(Calendar.newEventIntent()) })
                PillButton("カレンダーを開く", { actions.start(Calendar.openAppIntent()) })
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlowRow2(content: @Composable () -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

// ---- メール・LINE（届いた通知から） ----

@Composable
private fun NoticeDrawer(mail: Boolean) {
    val p = LocalPalette.current
    val actions = LocalActions.current
    val ctx = actions.activity
    val all by HatchNotificationListener.notices.collectAsState()
    val list = all.filter { if (mail) it.packageName in Notice.MAIL else it.isLine }
    val enabled = HatchNotificationListener.isEnabled(ctx)
    DrawerList {
        if (!enabled) {
            item {
                NeedPermission(
                    if (mail) "届いたメールの通知をここに並べます。通知の内容は端末の中だけで使い、保存も送信もしません。"
                    else "LINE には公開 API がないため、届いた通知をここに並べ、押すと LINE の該当トークを開きます。通知の内容は端末の中だけで使います。",
                    "通知へのアクセスを許可",
                ) {
                    actions.start(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
            }
        } else if (list.isEmpty()) {
            item { Note(if (mail) "新しいメールの通知はありません" else "新しいトークの通知はありません") }
        }
        items(list, key = { it.key }) { n ->
            RowButton(onClick = {
                if (!HatchNotificationListener.open(ctx, n)) {
                    ctx.packageManager.getLaunchIntentForPackage(n.packageName)?.let(actions.start)
                }
            }) {
                Initial(n.initial)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row {
                        Text(n.title, color = p.fg, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(shortTime(n.time), color = p.muted, fontSize = 11.sp)
                    }
                    Text(n.text, color = p.muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        item {
            FlowRow2 {
                if (mail) {
                    PillButton("メールを開く", { actions.start(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_EMAIL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) })
                    PillButton("作成", { actions.start(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))) })
                } else {
                    PillButton("LINE を開く", {
                        val i = ctx.packageManager.getLaunchIntentForPackage(Contacts.LINE_PACKAGE)
                        if (i != null) actions.start(i) else actions.toast("LINE が入っていません")
                    })
                }
            }
        }
    }
}

// ---- 人 ----

@Composable
private fun PeopleDrawer(model: HomeModel) {
    val p = LocalPalette.current
    val actions = LocalActions.current
    if (!model.has(Manifest.permission.READ_CONTACTS)) {
        NeedPermission("端末の連絡先を表示します。スター（お気に入り）を付けた人が先頭に並び、ホームにも出ます。", "連絡先の読み取りを許可") {
            actions.requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS)) { model.refreshPeople() }
        }
        return
    }
    var q by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf<Long?>(null) }
    val list = model.people.filter { q.isBlank() || it.name.contains(q, ignoreCase = true) }
    DrawerList {
        item { Field(q, { q = it }, "名前で絞り込み", Modifier.padding(bottom = 8.dp)) }
        items(list, key = { it.id }) { person ->
            Column {
                RowButton(onClick = { expanded = if (expanded == person.id) null else person.id }) {
                    Initial(person.initial)
                    TwoLines(person.name, listOfNotNull(person.phone, person.email).joinToString(" · ").ifEmpty { null }, Modifier.weight(1f))
                    if (person.starred) Text("★", color = p.accent, fontSize = 13.sp)
                }
                if (expanded == person.id) {
                    FlowRow2 {
                        person.phone?.let { PillButton("電話", { actions.start(Contacts.dialIntent(it)) }) }
                        person.email?.let { PillButton("メール", { actions.start(Contacts.mailIntent(it)) }) }
                        PillButton("LINE", {
                            val i = actions.activity.packageManager.getLaunchIntentForPackage(Contacts.LINE_PACKAGE)
                            if (i != null) actions.start(i) else actions.toast("LINE が入っていません")
                        })
                        PillButton("詳細", { actions.start(Contacts.viewIntent(person)) })
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
        if (list.isEmpty()) item { Note("連絡先がありません") }
    }
}

// ---- アプリ ----

@Composable
private fun AppsDrawer(model: HomeModel) {
    val p = LocalPalette.current
    val actions = LocalActions.current
    var q by remember { mutableStateOf("") }
    var menuFor by remember { mutableStateOf<String?>(null) }
    val list = model.apps.filter { q.isBlank() || it.label.contains(q, ignoreCase = true) }
    DrawerList {
        item { Field(q, { q = it }, "名前で絞り込み", Modifier.padding(bottom = 8.dp)) }
        var last = ""
        list.forEach { app ->
            val h = Apps.heading(app.label)
            val showHead = h != last
            last = h
            item(key = app.key) {
                Box {
                    RowButton(
                        onClick = { actions.start(Apps.launchIntent(app)) },
                        onLongClick = { menuFor = app.key },
                        label = "開く",
                    ) {
                        Text(if (showHead) h else "", color = p.faint, fontSize = 12.sp, modifier = Modifier.width(18.dp))
                        Text(app.label, color = p.fg, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        if (app.key in model.favoriteApps) Text("ホーム", color = p.accent, fontSize = 11.sp)
                    }
                    DropdownMenu(expanded = menuFor == app.key, onDismissRequest = { menuFor = null }) {
                        DropdownMenuItem(
                            text = { Text(if (app.key in model.favoriteApps) "ホームから外す" else "ホームに置く") },
                            onClick = { model.toggleFavorite(app); menuFor = null },
                        )
                        DropdownMenuItem(text = { Text("アプリ情報") }, onClick = { actions.start(Apps.infoIntent(app.packageName)); menuFor = null })
                    }
                }
            }
        }
        item { Note("長押しで「ホームに置く」。ホームの下段に最大 8 個まで並びます。") }
    }
}

// ---- 栞 ----

@Composable
private fun BookmarkDrawer(model: HomeModel) {
    val p = LocalPalette.current
    val actions = LocalActions.current
    var adding by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<String?>(null) }
    DrawerList {
        if (model.bookmarks.isEmpty()) item { Note("まだありません。下の「＋ 追加」か、ブラウザーの共有メニュー →「Clip に保存」→ Clip から「栞へ」で登録できます。") }
        items(model.bookmarks, key = { it.id }) { b ->
            Box {
                RowButton(onClick = { actions.start(Intent(Intent.ACTION_VIEW, Uri.parse(b.url))) }, onLongClick = { menuFor = b.id }) {
                    Initial(b.name.take(1))
                    TwoLines(b.name, Uri.parse(b.url).host ?: b.url, Modifier.weight(1f))
                }
                DropdownMenu(expanded = menuFor == b.id, onDismissRequest = { menuFor = null }) {
                    DropdownMenuItem(text = { Text("削除") }, onClick = { model.removeBookmark(b); menuFor = null })
                }
            }
        }
        item { FlowRow2 { PillButton("＋ 追加", { adding = true }) } }
    }
    if (adding) BookmarkDialog(onDismiss = { adding = false }) { name, url -> model.addBookmark(name, url); adding = false }
}

@Composable
fun BookmarkDialog(initialUrl: String = "", onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf(initialUrl) }
    val valid = url.startsWith("https://") || url.startsWith("http://")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("栞を追加") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Field(name, { name = it }, "名前（空ならドメイン名）")
                Field(url, { url = it.trim() }, "https://")
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, url) }, enabled = valid) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("やめる") } },
    )
}

// ---- Clip ----

@Composable
private fun ClipDrawer(model: HomeModel) {
    val p = LocalPalette.current
    val actions = LocalActions.current
    val ctx = actions.activity
    var expanded by remember { mutableStateOf<String?>(null) }
    var toBookmark by remember { mutableStateOf<ClipItem?>(null) }
    DrawerList {
        item {
            DashedBox("ほかのアプリの共有メニューで「Clip に保存」を選ぶと、テキスト・URL・ファイルがここに一時保存されます（DeskHatch の Clip と同じ考え方）。", Modifier.padding(bottom = 10.dp))
        }
        items(model.clips, key = { it.id }) { c ->
            Column {
                RowButton(onClick = { expanded = if (expanded == c.id) null else c.id }) {
                    Badge(
                        when {
                            c.isFile -> c.fileName!!.substringAfterLast('.', "FILE").uppercase(Locale.ROOT).take(4)
                            c.isUrl -> "URL"
                            else -> "TXT"
                        },
                    )
                    TwoLines(c.text ?: c.fileName!!.substringAfter('_'), shortTime(c.time), Modifier.weight(1f))
                }
                if (expanded == c.id) {
                    FlowRow2 {
                        when {
                            c.isFile -> {
                                val f = File(HomeModel.clipDir(ctx), c.fileName!!)
                                val uri = Files.localUri(ctx, f)
                                PillButton("開く", { actions.start(Files.openIntent(uri, c.mime)) })
                                PillButton("共有", { actions.start(Files.shareIntent(uri, c.mime, f.name)) })
                                PillButton("書き出す", { actions.export(uri, c.fileName.substringAfter('_'), c.mime) })
                            }
                            c.isUrl -> {
                                PillButton("開く", { actions.start(Intent(Intent.ACTION_VIEW, Uri.parse(c.text))) })
                                PillButton("栞へ", { toBookmark = c })
                                PillButton("コピー", { copy(ctx, c.text!!); actions.toast("コピーしました") })
                            }
                            else -> {
                                PillButton("コピー", { copy(ctx, c.text!!); actions.toast("コピーしました") })
                                PillButton("共有", {
                                    actions.start(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, c.text), "共有"))
                                })
                            }
                        }
                        PillButton("削除", { model.removeClip(c) })
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
        if (model.clips.isEmpty()) item { Note("まだありません") }
    }
    toBookmark?.let { c ->
        BookmarkDialog(initialUrl = c.text ?: "", onDismiss = { toBookmark = null }) { name, url ->
            model.addBookmark(name, url); toBookmark = null; actions.toast("栞に追加しました")
        }
    }
}

private fun copy(ctx: Context, text: String) {
    ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Clip", text))
}

/** 枠付きの小さな面（ウィジェットの台など）。 */
@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val p = LocalPalette.current
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(0.dp)).border(1.dp, p.line, RoundedCornerShape(0.dp))) { content() }
}

@Composable
fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(LocalPalette.current.line))
}

@Composable
fun Dot(color: Color) {
    Box(Modifier.size(8.dp).clip(RoundedCornerShape(0.dp)).background(color))
}

@Composable
fun VBar(color: Color) {
    Box(Modifier.width(3.dp).fillMaxHeight().background(color))
}
