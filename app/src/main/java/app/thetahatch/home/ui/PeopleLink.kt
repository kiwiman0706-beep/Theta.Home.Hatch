package app.thetahatch.home.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.thetahatch.home.HomeModel
import app.thetahatch.home.data.Contacts
import app.thetahatch.home.data.FavPerson
import app.thetahatch.home.data.PeopleLinks
import app.thetahatch.home.data.Person
import app.thetahatch.home.data.Shortcuts
import app.thetahatch.home.notify.HatchNotificationListener

/**
 * LINE のトークを選ぶ。person があればその人に結ぶ（名前の近いものを上に）。null ならよく連絡する人に足す（グループなど）。
 * 出せるのは、Home から見える LINE のトーク（最近のトーク・ホーム画面に追加したもの）だけ。
 */
@Composable
fun LineRoomDialog(model: HomeModel, person: Person?, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalActions.current.activity
    val rooms = remember { model.lineRooms() }
    val sorted = if (person == null) rooms else rooms.sortedByDescending { PeopleLinks.similarity(Shortcuts.label(it), person.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (person != null) "${person.name} と結ぶ LINE のトーク" else "LINE のトークを足す") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (rooms.isEmpty()) Note(
                    if (!Shortcuts.available(ctx)) "このホームが既定のホームアプリのときだけ、LINE のトークを読めます。"
                    else "LINE のトークが見つかりません。LINE で一度やりとりするか、トーク →「≡」→「ホーム画面に追加」で追加してください。",
                )
                sorted.forEachIndexed { zi, s ->
                    val near = person != null && PeopleLinks.similarity(Shortcuts.label(s), person.name) >= 80
                    RowButton(zebra = zi, onClick = {
                        if (person != null) model.linkLine(person, s) else model.addFavLine(s)
                        onDismiss()
                    }) {
                        LineIcon(model, s.id, 32.dp, s)
                        Text(Shortcuts.label(s), color = p.fg, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        if (near) Text("近い", color = p.accent, fontSize = 11.sp)
                    }
                }
                Note("出るのは最近のトークと、LINE で「ホーム画面に追加」したトークです。結んだトークは、最近の一覧から消えても開けるように残します。")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } },
    )
}

/** LINE のトークのアイコン（保存したもの、無ければその場で読む、それも無ければ L）。 */
@Composable
private fun LineIcon(model: HomeModel, id: String, size: Dp, info: android.content.pm.ShortcutInfo? = null) {
    val p = LocalPalette.current
    val ctx = LocalActions.current.activity
    val bmp = remember(id) {
        model.lineIcon(id) ?: info?.let { s -> Shortcuts.saveIcon(ctx, s, "line_" + s.id); model.lineIcon(id) }
    }
    if (bmp != null) Image(bmp.asImageBitmap(), null, Modifier.size(size).clip(CircleShape), contentScale = ContentScale.Crop)
    else Box(Modifier.size(size).clip(CircleShape).background(p.launch), contentAlignment = Alignment.Center) { Text("L", color = p.onAccent, fontSize = 13.sp) }
}

/** 人の顔（連絡先の写真）か、LINE のトークのアイコン。 */
@Composable
private fun FavFace(model: HomeModel, f: FavPerson, size: Dp) {
    val person = f.contactKey?.let { model.personByKey(it) }
    when {
        person != null -> Face(person, size)
        f.lineId != null -> LineIcon(model, f.lineId!!, size)
        else -> Initial(f.label.take(1).ifEmpty { "?" }, size)
    }
}

/**
 * よく連絡する人：顔を横に並べ、押すとすぐ連絡（LINE と結んでいれば LINE、無ければ電話）。長押しで LINE・電話・メール・並べ替え・外す。
 * 顔の右上に LINE の未読の数。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FavPeopleRow(model: HomeModel) {
    val p = LocalPalette.current
    val actions = LocalActions.current
    val notices by HatchNotificationListener.notices.collectAsState()
    var menu by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    var linking by remember { mutableStateOf<Person?>(null) }
    linking?.let { pe -> LineRoomDialog(model, pe) { linking = null } }
    val favs = model.favPeople
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        favs.forEachIndexed { i, f ->
            val person = f.contactKey?.let { model.personByKey(it) }
            val link = person?.let { model.lineLinks[it.lookupKey] } ?: f.lineId?.let { app.thetahatch.home.data.LineLink(it, f.label) }
            val unread = PeopleLinks.unread(notices, link)
            fun primary() {
                when {
                    link != null -> model.openLine(link.shortcutId, actions.start)
                    person?.phone != null -> actions.start(Contacts.dialIntent(person.phone))
                    person != null -> actions.start(Contacts.viewIntent(person))
                }
            }
            Box {
                Column(
                    Modifier.width(64.dp).combinedClickable(onClick = ::primary, onLongClick = { menu = f.key }, onClickLabel = if (link != null) "LINE を開く" else "電話", onLongClickLabel = "ほかの連絡・並べ替え")
                        .semantics { contentDescription = f.label + if (unread > 0) "（未読 $unread）" else "" },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box {
                        FavFace(model, f, 52.dp)
                        if (unread > 0) Box(
                            Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-4).dp).size(20.dp).clip(CircleShape).background(p.accent2),
                            contentAlignment = Alignment.Center,
                        ) { Text(if (unread > 9) "9+" else "$unread", color = p.onAccent, fontSize = 10.sp) }
                    }
                    Text(person?.name ?: f.label, color = p.fg, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
                }
                DropdownMenu(expanded = menu == f.key, onDismissRequest = { menu = null }) {
                    Text(person?.name ?: f.label, color = p.muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                    link?.let { l -> DropdownMenuItem(text = { Text("LINE（${l.label}）") }, onClick = { menu = null; model.openLine(l.shortcutId, actions.start) }) }
                    if (person != null && link == null) DropdownMenuItem(text = { Text("LINE と結ぶ") }, onClick = { menu = null; linking = person })
                    if (person != null && model.lineLinks.containsKey(person.lookupKey)) DropdownMenuItem(text = { Text("LINE を外す") }, onClick = { menu = null; model.unlinkLine(person) })
                    person?.phone?.let { ph ->
                        DropdownMenuItem(text = { Text("電話") }, onClick = { menu = null; actions.start(Contacts.dialIntent(ph)) })
                        DropdownMenuItem(text = { Text("SMS") }, onClick = { menu = null; actions.start(Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", ph, null))) })
                    }
                    person?.email?.let { m -> DropdownMenuItem(text = { Text("メール") }, onClick = { menu = null; actions.start(Contacts.mailIntent(m)) }) }
                    person?.let { pe -> DropdownMenuItem(text = { Text("連絡先を開く") }, onClick = { menu = null; actions.start(Contacts.viewIntent(pe)) }) }
                    if (i > 0) DropdownMenuItem(text = { Text("◀ 前へ") }, onClick = { model.moveFav(f.key, -1) })
                    if (i < favs.lastIndex) DropdownMenuItem(text = { Text("▶ 後ろへ") }, onClick = { model.moveFav(f.key, 1) })
                    DropdownMenuItem(text = { Text("よく連絡する人から外す") }, onClick = { menu = null; model.toggleFav(f) })
                }
            }
        }
        Column(Modifier.width(64.dp).clickable(onClickLabel = "よく連絡する人を足す") { adding = true }, horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(52.dp).border(1.dp, p.line, CircleShape), contentAlignment = Alignment.Center) { Text("＋", color = p.accent, fontSize = 22.sp) }
            Text(if (favs.isEmpty()) "足す" else "", color = p.muted, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
        }
    }
    if (adding) AddFavDialog(model) { adding = false }
}

/** よく連絡する人を足す：連絡先（スター付きが先）か、LINE のトーク（グループなど）。 */
@Composable
private fun AddFavDialog(model: HomeModel, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    var q by remember { mutableStateOf("") }
    var line by remember { mutableStateOf(false) }
    if (line) { LineRoomDialog(model, null) { line = false; onDismiss() }; return }
    val list = model.people.filter { q.isBlank() || it.name.contains(q, true) }.sortedBy { !it.starred }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("よく連絡する人を足す") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FlowRow2 { PillButton("LINE のトーク（グループなど）から", { line = true }) }
                Field(q, { q = it }, "連絡先を名前で絞り込み")
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    if (list.isEmpty()) Note("連絡先がありません（PEOPLE のタブで連絡先の読み取りを許可）")
                    list.forEachIndexed { zi, person ->
                        val key = "c:" + person.lookupKey
                        val on = model.isFav(key)
                        RowButton(zebra = zi, onClick = { model.toggleFav(FavPerson(key, person.name)) }) {
                            Face(person, 32.dp)
                            Text(person.name, color = p.fg, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            if (model.lineLinks.containsKey(person.lookupKey)) Text("LINE", color = p.launch, fontSize = 11.sp)
                            Text(if (on) "✓" else "＋", color = p.accent, fontSize = 16.sp)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } },
    )
}
