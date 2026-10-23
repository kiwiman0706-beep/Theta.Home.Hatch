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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import jp.yaegaki.theta.home.data.ViewSpec
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
                TabKind.CATEGORY -> AppsDrawer(model, only = t.folderUri.orEmpty())
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
        verticalArrangement = Arrangement.spacedBy(0.dp),
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
        itemsIndexed(dayEvents, key = { _, it -> "${it.eventId}-${it.begin}" }) { zi, e ->
            RowButton(zebra = zi, onClick = { actions.start(Calendar.openIntent(e)) }) {
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
        itemsIndexed(list, key = { _, it -> it.key }) { zi, n ->
            RowButton(zebra = zi, onClick = {
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
        itemsIndexed(list, key = { _, it -> it.id }) { zi, person ->
            Column {
                RowButton(zebra = zi, onClick = { expanded = if (expanded == person.id) null else person.id }) {
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

/** アプリの一覧。カテゴリーごとに並べ、上の札で絞り込む。未分類があれば仕分けガイドへ。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppMenu(model: HomeModel, app: jp.yaegaki.theta.home.data.AppEntry, expanded: Boolean, close: () -> Unit, move: () -> Unit) {
    val actions = LocalActions.current
    DropdownMenu(expanded = expanded, onDismissRequest = close) {
        DropdownMenuItem(text = { Text("カテゴリーを変える") }, onClick = { close(); move() })
        DropdownMenuItem(text = { Text("アプリ情報") }, onClick = { actions.start(Apps.infoIntent(app.packageName)); close() })
        if (!Apps.isSystem(actions.activity, app.packageName)) {
            DropdownMenuItem(text = { Text("アンインストール") }, onClick = { actions.start(Apps.uninstallIntent(app.packageName)); close() })
        }
        if (model.iconMode == jp.yaegaki.theta.home.data.IconStyle.GEMINI && model.hasGeminiKey) {
            DropdownMenuItem(text = { Text("アイコンを Gemini で作り直す") }, onClick = { close(); model.clearGeminiIcons(app); model.startGeminiIcons(app) })
        }
    }
}

/** アプリの一覧の大きさ（S・M・L・XL）ごとのアイコンの辺と、グリッドの 1 マスの幅。 */
private val listIcon = listOf(24.dp, 32.dp, 40.dp, 52.dp)
private val appIcon = listOf(36.dp, 44.dp, 56.dp, 72.dp)
private val appCell = listOf(64.dp, 76.dp, 92.dp, 112.dp)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppsDrawer(model: HomeModel, only: String? = null) {
    val p = LocalPalette.current
    val actions = LocalActions.current
    var q by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf<String?>(only) } // null=すべて、"" = 未分類、それ以外はカテゴリー id
    if (only != null && filter != only) filter = only
    var menuFor by remember { mutableStateOf<String?>(null) }
    var moving by remember { mutableStateOf<jp.yaegaki.theta.home.data.AppEntry?>(null) }
    var sorting by remember { mutableStateOf(false) }
    val unsorted = model.apps.count { model.categoryOf(it) == null }
    // 見せ方（一覧・グリッド・大きさ）は、全体とカテゴリーのタブごとに覚える。カテゴリーのタブは初めからグリッド
    val viewKey = if (only != null) "apps:$only" else "apps"
    val spec = model.viewFor(listOf(viewKey), mostlyMedia = only != null)
    val visible = model.apps.filter { q.isBlank() || it.label.contains(q, ignoreCase = true) }
    // カテゴリーの並び順で見出しを付ける。未分類は最後
    val groups = (model.categories.map { c -> c.id to c.name } + ("" to "未分類"))
        .filter { (id, _) -> filter == null || filter == id }
        .map { (id, name) -> Triple(id, name, visible.filter { (model.appCategories[it.key] ?: "") == id || id == "" && model.categoryOf(it) == null }.distinct()) }
        .filter { it.third.isNotEmpty() }

    DrawerList {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                Field(q, { q = it }, "名前で絞り込み", Modifier.weight(1f))
                if (only == null) PillButton(if (unsorted > 0) "仕分け %d".format(unsorted) else "仕分け", { sorting = true }, primary = unsorted > 0)
                GlyphButton(if (spec.grid) Glyph.LIST else Glyph.GRID, if (spec.grid) "一覧で見る" else "グリッドで見る", { model.setView(viewKey, spec.copy(grid = !spec.grid)) })
                Box(Modifier.size(44.dp).clickable(onClickLabel = "大きさを変える") { model.setView(viewKey, spec.copy(size = (spec.size + 1) % ViewSpec.SIZES)) }, contentAlignment = Alignment.Center) {
                    Text(ViewSpec.NAMES[spec.size], color = p.muted, fontSize = 12.sp, fontFamily = Hud.mono, modifier = Modifier.border(1.dp, p.line).padding(horizontal = 5.dp, vertical = 2.dp))
                }
            }
        }
        if (only != null && model.categories.none { it.id == only }) item {
            Note("このタブのカテゴリーは消されました。タブを長押しして外してください。")
        }
        if (only == null) item {
            FlowRow(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                @Composable
                fun chip(label: String, id: String?) {
                    val on = filter == id
                    Text(
                        label, color = if (on) p.onAccent else p.fg, fontSize = 12.sp,
                        modifier = Modifier.background(if (on) p.accent else Color.Transparent).border(1.dp, if (on) p.accent else p.line)
                            .clickable { filter = if (on && id != null) null else id }.padding(horizontal = 10.dp, vertical = 7.dp),
                    )
                }
                chip("ALL %d".format(model.apps.size), null)
                model.categories.forEach { c -> chip("${c.name} ${model.appCategories.count { it.value == c.id }}", c.id) }
                if (unsorted > 0) chip("未分類 $unsorted", "")
            }
        }
        if (only == null && model.categories.isEmpty()) {
            item {
                DashedBox("アプリが多いときは「仕分け」で、カテゴリーを用意してから 1 つずつ振り分けられます。", Modifier.padding(bottom = 8.dp))
            }
        }
        groups.forEach { (id, name, list) ->
            item(key = "h:$id") {
                Row(Modifier.padding(top = 10.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle(name)
                    Text("%02d".format(list.size), color = p.faint, fontSize = 11.sp)
                    Ticks(Modifier.weight(1f).height(5.dp))
                }
            }
            if (spec.grid) item(key = "g:$id") {
                FlowRow(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    list.forEach { app ->
                        Box {
                            Column(
                                Modifier.width(appCell[spec.size])
                                    .combinedClickable(onClick = { actions.start(Apps.launchIntent(app)) }, onLongClick = { menuFor = app.key }, onClickLabel = "開く")
                                    .padding(vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                AppIcon(app, appIcon[spec.size])
                                Text(app.label, color = p.fg, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(horizontal = 2.dp))
                            }
                            AppMenu(model, app, menuFor == app.key, { menuFor = null }) { moving = app }
                        }
                    }
                }
            } else list.forEachIndexed { zi, app ->
                item(key = "$id:${app.key}") {
                    Box {
                        RowButton(
                            zebra = zi,
                            onClick = { actions.start(Apps.launchIntent(app)) },
                            onLongClick = { menuFor = app.key },
                            label = "開く",
                        ) {
                            AppIcon(app, listIcon[spec.size])
                            Text(app.label, color = p.fg, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        }
                        AppMenu(model, app, menuFor == app.key, { menuFor = null }) { moving = app }
                    }
                }
            }
        }
        item { Note("長押しで「カテゴリーを変える」「アンインストール」など。") }
    }

    moving?.let { app ->
        var creating by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { moving = null },
            title = { Text(app.label) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        model.categories.forEach { c ->
                            ChoiceButton(c.name, selected = model.categoryOf(app) == c, suggested = model.suggestedCategory(app) == c) { model.assign(app, c); moving = null }
                        }
                        ChoiceButton("未分類に戻す", selected = model.categoryOf(app) == null) { model.assign(app, null); moving = null }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Field(creating, { creating = it.take(12) }, "新しいカテゴリー", Modifier.weight(1f))
                        PillButton("作る", { if (creating.isNotBlank()) { model.assign(app, model.addCategory(creating)); moving = null } }, enabled = creating.isNotBlank())
                    }
                }
            },
            confirmButton = { TextButton(onClick = { moving = null }) { Text("閉じる") } },
        )
    }
    if (sorting) SortGuide(model) { sorting = false }
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
        itemsIndexed(model.bookmarks, key = { _, it -> it.id }) { zi, b ->
            Box {
                RowButton(zebra = zi, onClick = { actions.start(Intent(Intent.ACTION_VIEW, Uri.parse(b.url))) }, onLongClick = { menuFor = b.id }) {
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
        itemsIndexed(model.clips, key = { _, it -> it.id }) { zi, c ->
            Column {
                RowButton(zebra = zi, onClick = { expanded = if (expanded == c.id) null else c.id }) {
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
