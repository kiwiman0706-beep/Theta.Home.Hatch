package jp.yaegaki.theta.home.ui

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.zIndex
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
    // ウィジェットのタブは中身の高さだけ（ピン留めしたときは縦いっぱい）
    val wrap = w != null && !docked
    var editing by remember(id) { mutableStateOf(false) }
    Column(if (wrap) Modifier.fillMaxWidth() else Modifier.fillMaxSize()) {
        Stripes(Modifier.fillMaxWidth().height(4.dp), color = p.group(group))
        // 見出しは 1 行：[ 種類 ] // 番号  名前  （編集）（ピン）（閉じる）
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("[$kicker]·$code", color = p.group(group), fontSize = 10.sp, letterSpacing = 1.sp, fontFamily = Hud.mono)
            Text(title, color = p.fg, fontSize = 18.sp, fontFamily = Hud.display, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (w != null) GlyphButton(Glyph.GEAR, if (editing) "編集を終える" else "大きさ・並び・足す", { editing = !editing }, filled = editing)
            if (docked) {
                GlyphButton(Glyph.PIN, "ピン留めを外す", { model.unpin(id) }, filled = true)
            } else if (layout.maxDocked > 0) {
                GlyphButton(Glyph.PIN, "横に並べて出しておく", { model.pin(id, layout.maxDocked) })
            }
            if (!docked) GlyphButton(Glyph.CLOSE, "閉じる", { model.close() })
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
        Box(if (wrap) Modifier.fillMaxWidth() else Modifier.weight(1f).fillMaxWidth()) {
            if (w != null) {
                WidgetTabDrawer(model, w, editing)
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

/** 通知 1 件。押すと通知を押したのと同じ画面（開けなければそのアプリ）。app を渡すと頭にアプリのアイコン。 */
@Composable
private fun NoticeRow(n: Notice, zebra: Int, app: jp.yaegaki.theta.home.data.AppEntry? = null) {
    val p = LocalPalette.current
    val actions = LocalActions.current
    val ctx = actions.activity
    RowButton(zebra = zebra, onClick = {
        if (!HatchNotificationListener.open(ctx, n)) {
            ctx.packageManager.getLaunchIntentForPackage(n.packageName)?.let(actions.start)
        }
    }) {
        if (app != null) AppIcon(app, 32.dp) else Initial(n.initial)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row {
                Text(n.title, color = p.fg, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(shortTime(n.time), color = p.muted, fontSize = 11.sp)
            }
            Text(n.text, color = p.muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** アイコンの右上に付ける通知の件数。 */
@Composable
private fun CountBadge(n: Int, modifier: Modifier) {
    if (n <= 0) return
    val p = LocalPalette.current
    Text(if (n > 99) "99+" else n.toString(), color = p.onAccent, fontSize = 10.sp, fontFamily = Hud.mono,
        modifier = modifier.background(p.accent).padding(horizontal = 4.dp))
}

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
        itemsIndexed(list, key = { _, it -> it.key }) { zi, n -> NoticeRow(n, zi) }
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
private fun AppMenu(model: HomeModel, app: jp.yaegaki.theta.home.data.AppEntry, expanded: Boolean, close: () -> Unit, shortcuts: () -> Unit, move: () -> Unit) {
    val actions = LocalActions.current
    DropdownMenu(expanded = expanded, onDismissRequest = close) {
        DropdownMenuItem(text = { Text("ショートカット（タブに置く）") }, onClick = { close(); shortcuts() })
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

@Composable
private fun ShortcutMenu(model: HomeModel, sc: jp.yaegaki.theta.home.data.PinnedShortcut, expanded: Boolean, close: () -> Unit, rename: () -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = close) {
        DropdownMenuItem(text = { Text("名前を変える") }, onClick = { close(); rename() })
        DropdownMenuItem(text = { Text("このショートカットを外す") }, onClick = { close(); model.removeShortcut(sc.id) })
    }
}

/** アプリの持っているショートカット。押すと開き、「置く」でこのカテゴリーのタブに置く。既定のホームのときだけ読める。 */
@Composable
private fun AppShortcutsDialog(model: HomeModel, app: jp.yaegaki.theta.home.data.AppEntry, catId: String, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalActions.current.activity
    val ok = remember { jp.yaegaki.theta.home.data.Shortcuts.available(ctx) }
    val list = remember(app.packageName) { if (ok) jp.yaegaki.theta.home.data.Shortcuts.forApp(ctx, app.packageName) else emptyList() }
    val target = catId.ifEmpty { model.appCategories[app.key].orEmpty() }
    val targetName = model.categories.firstOrNull { it.id == target }?.name ?: "未分類"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(app.label) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                when {
                    !ok -> Text("ショートカットは、このアプリを既定のホームにすると使えます。", color = p.muted, fontSize = 13.sp)
                    list.isEmpty() -> Text("このアプリにはショートカットがありません。LINE のトークルームなどは、そのアプリの「ホーム画面に追加」からも置けます。", color = p.muted, fontSize = 13.sp, lineHeight = 19.sp)
                    else -> Text("「置く」で「$targetName」のタブに。LINE のトークルームなどは、そのアプリの「ホーム画面に追加」でも置けます。", color = p.muted, fontSize = 12.sp, lineHeight = 18.sp)
                }
                list.forEachIndexed { zi, s ->
                    RowButton(zebra = zi, onClick = { jp.yaegaki.theta.home.data.Shortcuts.start(ctx, s); onDismiss() }) {
                        Text(jp.yaegaki.theta.home.data.Shortcuts.label(s), color = p.fg, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        PillButton("置く", { model.addShortcut(s, target); onDismiss() })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } },
    )
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
    val notices by HatchNotificationListener.notices.collectAsState()
    val reorder = rememberReorderState()
    val counts = notices.groupingBy { it.packageName }.eachCount()
    // 見せ方（一覧・グリッド・大きさ）は、全体とカテゴリーのタブごとに覚える。カテゴリーのタブは初めからグリッド
    val viewKey = if (only != null) "apps:$only" else "apps"
    val spec = model.viewFor(listOf(viewKey), mostlyMedia = only != null)
    val visible = model.apps.filter { q.isBlank() || it.label.contains(q, ignoreCase = true) }
    // カテゴリーの並び順で見出しを付ける。未分類は最後
    val groups = (model.categories.map { c -> c.id to c.name } + ("" to "未分類"))
        .filter { (id, _) -> filter == null || filter == id }
        .map { (id, name) -> Triple(id, name, model.ordered(id, visible.filter { (model.appCategories[it.key] ?: "") == id || id == "" && model.categoryOf(it) == null }.distinct())) }
        .filter { g -> g.third.isNotEmpty() || model.shortcuts.any { it.catId == g.first } }
    val shortcutsOf = { catId: String -> model.shortcuts.filter { it.catId == catId && (q.isBlank() || it.label.contains(q, ignoreCase = true)) } }
    var scMenu by remember { mutableStateOf<String?>(null) }
    var scRenaming by remember { mutableStateOf<jp.yaegaki.theta.home.data.PinnedShortcut?>(null) }
    var shortcutsFor by remember { mutableStateOf<Pair<jp.yaegaki.theta.home.data.AppEntry, String>?>(null) }

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
            val scs = shortcutsOf(id)
            item(key = "h:$id") {
                Row(Modifier.padding(top = 10.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle(name)
                    Text("%02d".format(list.size + scs.size), color = p.faint, fontSize = 11.sp)
                    Ticks(Modifier.weight(1f).height(5.dp))
                }
            }
            if (spec.grid) item(key = "g:$id") {
                FlowRow(Modifier.zIndex(if (list.any { it.key == reorder.dragging }) 10f else 0f).fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    list.forEach { app ->
                        Box(Modifier.zIndex(if (reorder.dragging == app.key) 10f else 0f)) {
                            Column(
                                Modifier.width(appCell[spec.size])
                                    // 長押し→ドラッグで同じカテゴリーの中を並べ替え。動かさずに離すとメニュー
                                    .reorderable(reorder, app.key, "app:$id", p.accent, onMenu = { menuFor = app.key }) { from, to -> model.moveAppTo(id, list, from, to) }
                                    .combinedClickable(onClick = { actions.start(Apps.launchIntent(app)) }, onLongClick = {}, onClickLabel = "開く")
                                    .padding(vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Box {
                                    AppIcon(app, appIcon[spec.size])
                                    CountBadge(counts[app.packageName] ?: 0, Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-4).dp))
                                }
                                Text(app.label, color = p.fg, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(horizontal = 2.dp))
                            }
                            AppMenu(model, app, menuFor == app.key, { menuFor = null }, { shortcutsFor = app to id }) { moving = app }
                        }
                    }
                    // 置いたショートカット（LINE のトークルームなど）。アプリと同じマスに、角に小さな印
                    scs.forEach { sc ->
                        Box(Modifier.zIndex(if (reorder.dragging == "sc:" + sc.id) 10f else 0f)) {
                            Column(
                                Modifier.width(appCell[spec.size])
                                    .reorderable(reorder, "sc:" + sc.id, "sc:$id", p.accent, onMenu = { scMenu = sc.id }) { from, to -> model.moveShortcutTo(from.removePrefix("sc:"), to.removePrefix("sc:")) }
                                    .combinedClickable(onClick = { model.launchShortcut(sc) }, onLongClick = {}, onClickLabel = "開く")
                                    .padding(vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Box {
                                    ShortcutIcon(sc, appIcon[spec.size])
                                    Box(Modifier.align(Alignment.BottomEnd).size(10.dp).background(p.accent))
                                }
                                Text(sc.label, color = p.fg, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(horizontal = 2.dp))
                            }
                            ShortcutMenu(model, sc, scMenu == sc.id, { scMenu = null }) { scRenaming = sc }
                        }
                    }
                }
            } else {
                scs.forEachIndexed { zi, sc ->
                    item(key = "sc:$id:${sc.id}") {
                        Box(Modifier.zIndex(if (reorder.dragging == "sc:" + sc.id) 10f else 0f)) {
                            RowButton(
                                zebra = list.size + zi,
                                onClick = { model.launchShortcut(sc) },
                                onLongClick = {},
                                label = "開く",
                                modifier = Modifier.reorderable(reorder, "sc:" + sc.id, "sc:$id", p.accent, onMenu = { scMenu = sc.id }) { from, to -> model.moveShortcutTo(from.removePrefix("sc:"), to.removePrefix("sc:")) },
                            ) {
                                ShortcutIcon(sc, listIcon[spec.size])
                                Text(sc.label, color = p.fg, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Text("SHORTCUT", color = p.faint, fontSize = 9.sp, fontFamily = Hud.mono)
                            }
                            ShortcutMenu(model, sc, scMenu == sc.id, { scMenu = null }) { scRenaming = sc }
                        }
                    }
                }
            }
            if (!spec.grid) list.forEachIndexed { zi, app ->
                item(key = "$id:${app.key}") {
                    Box(Modifier.zIndex(if (reorder.dragging == app.key) 10f else 0f)) {
                        RowButton(
                            zebra = zi,
                            onClick = { actions.start(Apps.launchIntent(app)) },
                            onLongClick = {},
                            label = "開く",
                            modifier = Modifier.reorderable(reorder, app.key, "app:$id", p.accent, onMenu = { menuFor = app.key }) { from, to -> model.moveAppTo(id, list, from, to) },
                        ) {
                            AppIcon(app, listIcon[spec.size])
                            Text(app.label, color = p.fg, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            CountBadge(counts[app.packageName] ?: 0, Modifier)
                        }
                        AppMenu(model, app, menuFor == app.key, { menuFor = null }, { shortcutsFor = app to id }) { moving = app }
                    }
                }
            }
        }
        // カテゴリーのタブ：そのカテゴリーのアプリに届いている通知を、アプリの下に並べる
        if (only != null) {
            val pkgs = groups.flatMap { it.third }.associateBy { it.packageName }
            val mine = notices.filter { it.packageName in pkgs }
            item(key = "n:head") {
                Row(Modifier.padding(top = 14.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle("NOTICE / 通知")
                    Text("%02d".format(mine.size), color = if (mine.isEmpty()) p.faint else p.accent, fontSize = 11.sp)
                    Ticks(Modifier.weight(1f).height(5.dp))
                }
            }
            if (!HatchNotificationListener.isEnabled(actions.activity)) item(key = "n:perm") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Note("通知へのアクセスを許可すると、このカテゴリーのアプリの通知がここに並びます（端末の中だけで使い、保存も送信もしません）。")
                    PillButton("通知へのアクセスを許可", { actions.start(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) })
                }
            } else if (mine.isEmpty()) item(key = "n:none") { Note("新しい通知はありません") }
            itemsIndexed(mine, key = { _, it -> "n:" + it.key }) { zi, n -> NoticeRow(n, zi, pkgs[n.packageName]) }
        }
        item { Note("長押しして動かすと並べ替え、動かさずに離すと「カテゴリーを変える」「アンインストール」など。") }
    }

    shortcutsFor?.let { (app, catId) -> AppShortcutsDialog(model, app, catId) { shortcutsFor = null } }
    scRenaming?.let { sc ->
        var name by remember(sc.id) { mutableStateOf(sc.label) }
        AlertDialog(
            onDismissRequest = { scRenaming = null },
            title = { Text("ショートカットの名前") },
            text = { Field(name, { name = it.take(20) }, "名前") },
            confirmButton = { TextButton(onClick = { model.renameShortcut(sc.id, name); scRenaming = null }, enabled = name.isNotBlank()) { Text("保存") } },
            dismissButton = { TextButton(onClick = { scRenaming = null }) { Text("やめる") } },
        )
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
