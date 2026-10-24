package jp.yaegaki.theta.home.ui

import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import jp.yaegaki.theta.home.data.Thumbs
import jp.yaegaki.theta.home.data.ViewSpec
import jp.yaegaki.theta.home.data.SavedQuery
import android.Manifest
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.yaegaki.theta.home.HomeModel
import jp.yaegaki.theta.home.data.Apps
import jp.yaegaki.theta.home.data.Contacts
import jp.yaegaki.theta.home.data.Entry
import jp.yaegaki.theta.home.data.Files
import jp.yaegaki.theta.home.data.TabKind
import jp.yaegaki.theta.home.data.TabSpec
import jp.yaegaki.theta.home.hatch.HatchCatalog
import jp.yaegaki.theta.home.nas.Nas
import jp.yaegaki.theta.home.drive.GDrive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 一覧の 1 行。端末のファイル（Entry）と NAS のファイル（Nas.Item）を同じ形で扱う。 */
private data class Row(
    val key: String,
    val name: String,
    val isDir: Boolean,
    val size: Long,
    val modified: Long,
    val mime: String?,
    val entry: Entry? = null,
    val nas: Nas.Item? = null,
    val drive: GDrive.Item? = null,
    /** 2 行目を差し替えるとき（最近：どこで開いたか・いつ）。 */
    val note: String? = null,
) {
    val badge: String get() = when {
        isDir -> "DIR"
        drive?.isGoogleDoc == true -> when (drive.mime.substringAfterLast('.')) {
            "document" -> "GDOC"
            "spreadsheet" -> "GSHT"
            "presentation" -> "GSLD"
            "form" -> "GFRM"
            else -> "GAPP"
        }
        else -> name.substringAfterLast('.', "FILE").uppercase(Locale.ROOT).take(4)
    }
}

private fun Entry.row() = Row(uri.toString(), name, isDir, size, modified, mime, entry = this)

private fun Nas.Item.row() = Row("nas:$path", name, dir, size, modified, if (dir) null else Files.mimeOf(name), nas = this)

private fun GDrive.Item.row() = Row("drive:$id", name, isDir, size, modified, mime, drive = this)

/**
 * 背景（デスクトップ）のファイラー。スマホでもファイルの置き場をホームにする。
 * 上：時計・今日の予定・検索、ネットワーク・Tailscale・NAS の状態。下：場所（フォルダのタブ・NAS・最近）とファイルの一覧。
 * 広い画面では選んだファイルの中身を右に出す。
 */
@Composable
fun Filer(model: HomeModel) {
    val layout = LocalLayout.current
    var nasDialog by remember { mutableStateOf(false) }
    var driveDialog by remember { mutableStateOf(false) }
    // 狭い画面の背景はダッシュボード。ファイラーはフォルダのタブ・場所を押したときだけ
    if (layout.compact && !model.filerOpen) {
        Dashboard(model, openNasSettings = { nasDialog = true })
        if (nasDialog) NasDialog(model) { nasDialog = false }
        return
    }
    Column(Modifier.fillMaxSize().padding(horizontal = if (layout.compact) 14.dp else 28.dp, vertical = 12.dp)) {
        FilerHeader(model)
        Gap(8.dp)
        StatusStrip(model) { nasDialog = true }
        Gap(10.dp)
        if (model.query.isNotBlank()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { SearchResults(model) }
        } else {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val showPlaces = maxWidth >= 560.dp
                val showPreview = maxWidth >= 860.dp
                var selected by remember(model.location) { mutableStateOf<Row?>(null) }
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (showPlaces) Places(model, Modifier.width(176.dp).fillMaxHeight()) { nasDialog = true }
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        if (!showPlaces) PlaceChips(model)
                        FolderView(model, preview = showPreview, selected = selected, onSelect = { selected = it }, openNasSettings = { nasDialog = true }, openDriveSettings = { driveDialog = true })
                    }
                    if (showPreview) Preview(model, selected, Modifier.width(300.dp).fillMaxHeight())
                }
            }
        }
        FilerFooter(model)
    }
    if (nasDialog) NasDialog(model) { nasDialog = false }
    if (driveDialog) DriveDialog(model) { driveDialog = false }
}

/** ドライブのファイルを開く。Google ドキュメントなどはリンクで（ドキュメントのアプリかブラウザが開く）、ほかは取得してから。 */
private fun openDriveItem(model: HomeModel, actions: Actions, d: GDrive.Item) {
    if (d.isGoogleDoc) d.link?.let { actions.start(Intent(Intent.ACTION_VIEW, Uri.parse(it))) }
    else model.fetchDrive(d) { f -> actions.start(Files.openIntent(Files.localUri(actions.activity, f), d.mime)) }
}

/** 下端の帯：今の場所・件数・画面の種類。 */
@Composable
private fun FilerFooter(model: HomeModel) {
    val p = LocalPalette.current
    val loc = model.location
    val tab = model.tab(loc.removePrefix("tab:"))
    val (where, count) = when {
        loc == "nas" -> "NAS" + model.nasState.path.ifEmpty { "/" } to model.nasState.entries.size
        loc == "recent" -> "RECENT" to model.recents.size
        tab != null -> tab.label to (model.folders[tab.id]?.entries?.size ?: 0)
        else -> "----" to 0
    }
    Column(Modifier.padding(top = 6.dp)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Readout("LOC", where, Modifier.weight(1f, fill = false))
            Readout("ITEMS", "%03d".format(count))
            Readout("SCREEN", if (model.profile == "wide") "WIDE" else "NARROW", accent = true)
            Stripes(Modifier.weight(1f).height(10.dp), color = p.line)
        }
    }
}

/** 時計と日付。10 秒ごとに描き直す。 */
@Composable
internal fun ClockBlock(big: Boolean) {
    val p = LocalPalette.current
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis(); delay(10_000)
        }
    }
    val d = Date(now)
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(6.dp).background(p.accent))
                Text("SYS.TIME", color = p.faint, fontSize = 10.sp, fontFamily = Hud.mono, letterSpacing = 2.sp)
            }
            Text(
                SimpleDateFormat("HH:mm", Locale.US).format(d), color = p.fg, fontFamily = Hud.display, fontWeight = FontWeight.Light,
                fontSize = if (big) 72.sp else 52.sp, lineHeight = if (big) 76.sp else 56.sp, letterSpacing = 1.sp,
            )
        }
        Column(Modifier.padding(bottom = 8.dp)) {
            Text(SimpleDateFormat("yyyy.MM.dd", Locale.US).format(d), color = p.fg, fontSize = 14.sp, fontFamily = Hud.mono)
            Text(SimpleDateFormat("EEE", Locale.US).format(d).uppercase(Locale.US) + " / " + SimpleDateFormat("E曜日", Locale.JAPAN).format(d), color = p.accent, fontSize = 12.sp, fontFamily = Hud.mono)
        }
    }
}

/** 検索・アプリ・設定の並び。 */
@Composable
internal fun SearchRow(model: HomeModel, leading: (@Composable () -> Unit)? = null) {
    val p = LocalPalette.current
    val focus = remember { FocusRequester() }
    LaunchedEffect(model.focusSearch) { if (model.focusSearch > 0) runCatching { focus.requestFocus() } }
    Row(verticalAlignment = Alignment.CenterVertically) {
        leading?.invoke()
        Field(model.query, { model.query = it }, "SEARCH  検索", Modifier.weight(1f).focusRequester(focus), leading = { GlyphIcon(Glyph.SEARCH, p.accent) })
        Box(
            Modifier.size(44.dp)
                .combinedClickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = "アプリ", onLongClick = { model.pressApps(long = true) }, onLongClickLabel = "声でアプリを開く") { model.pressApps(long = false) }
                .semantics { contentDescription = "APPS（Alt+A）" },
            contentAlignment = Alignment.Center,
        ) { GlyphIcon(Glyph.GRID, if (model.open == model.appsId()) p.accent else p.muted) }
        GlyphButton(Glyph.GEAR, "設定", { model.settingsOpen = true })
    }
}

/** 予定を 1 行で：時刻（終日は ALL DAY）・色の帯・題名。終わった予定は薄く。 */
@Composable
internal fun EventLine(e: jp.yaegaki.theta.home.data.Event, onClick: () -> Unit) {
    val p = LocalPalette.current
    val done = !e.allDay && e.end < System.currentTimeMillis()
    Row(
        Modifier.fillMaxWidth().clickable(onClickLabel = "予定を開く", onClick = onClick).padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            if (e.allDay) "ALL DAY" else SimpleDateFormat("HH:mm", Locale.US).format(Date(e.begin)),
            color = if (done) p.faint else p.accent, fontSize = 12.sp, fontFamily = Hud.mono, modifier = Modifier.width(58.dp),
        )
        Box(Modifier.width(3.dp).height(16.dp).background(if (e.color != 0) Color(e.color) else p.widget))
        Text(e.title, color = if (done) p.faint else p.fg, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}

/** 今日の予定。max を超えた分は「+N 件」（押すとカレンダー）。 */
@Composable
internal fun TodayEvents(model: HomeModel, max: Int, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val actions = LocalActions.current
    val calTab = model.tabs.firstOrNull { it.kind == TabKind.CALENDAR }
    val list = model.todayEvents
    var picking by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("TODAY'S_EVENTS", color = p.faint, fontSize = 10.sp, fontFamily = Hud.mono, letterSpacing = 1.5.sp)
            Text("%02d".format(list.size), color = p.accent, fontSize = 10.sp, fontFamily = Hud.mono)
            if (model.has(Manifest.permission.READ_CALENDAR)) Text("CAL ⚙", color = p.faint, fontSize = 10.sp, fontFamily = Hud.mono,
                modifier = Modifier.clickable(onClickLabel = "表示するカレンダー") { picking = true }.padding(horizontal = 6.dp, vertical = 2.dp))
        }
        if (picking) CalendarPicker(model) { picking = false }
        when {
            !model.has(Manifest.permission.READ_CALENDAR) -> Text("予定の読み取りを許可すると、今日の予定が出ます", color = p.muted, fontSize = 13.sp,
                modifier = Modifier.clickable { actions.requestPermissions(arrayOf(Manifest.permission.READ_CALENDAR)) { model.refreshEvents() } })
            list.isEmpty() -> Text("今日の予定はありません", color = p.muted, fontSize = 13.sp)
            else -> {
                list.take(max).forEach { e -> EventLine(e) { actions.start(jp.yaegaki.theta.home.data.Calendar.openIntent(e)) } }
                if (list.size > max) Text("+${list.size - max} 件 ›", color = p.accent, fontSize = 12.sp, fontFamily = Hud.mono,
                    modifier = Modifier.clickable { calTab?.let { model.toggle(it.id) } }.padding(vertical = 4.dp))
            }
        }
    }
}

@Composable
private fun FilerHeader(model: HomeModel) {
    val p = LocalPalette.current
    val layout = LocalLayout.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (layout.compact) {
            // 狭い画面のファイラーは、ダッシュボードの上に開くもの。戻るでダッシュボードへ
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GlyphButton(Glyph.CLOSE, "ファイラーを閉じる", { model.filerOpen = false })
                Text("FILER", color = p.fg, fontSize = 26.sp, fontFamily = Hud.display)
                Text("// FILES", color = p.faint, fontSize = 11.sp, fontFamily = Hud.mono, letterSpacing = 2.sp)
                Spacer(Modifier.weight(1f))
                Stripes(Modifier.width(60.dp).height(8.dp))
            }
            SearchRow(model)
            return@Column
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            ClockBlock(big = true)
            TodayEvents(model, max = 3, modifier = Modifier.weight(1f).padding(bottom = 6.dp))
            Column(Modifier.padding(bottom = 8.dp), horizontalAlignment = Alignment.End) {
                Text("THETA // HOME.HATCH", color = p.faint, fontSize = 10.sp, fontFamily = Hud.mono, letterSpacing = 1.sp)
                Stripes(Modifier.width(96.dp).height(8.dp))
            }
        }
        Ticks(Modifier.fillMaxWidth().height(6.dp))
        SearchRow(model)
    }
}

/** ネットワーク・Tailscale・NAS の状態。つながっていなければ、Tailscale を開く・再接続の手助けを出す。 */
@Composable
internal fun StatusStrip(model: HomeModel, openNasSettings: () -> Unit) {
    val p = LocalPalette.current
    val actions = LocalActions.current
    val s = model.status
    /** 計器の 1 区画：左に状態の帯（白＝正常・オレンジ＝問題・灰＝不明）、項目名と値。 */
    @Composable
    fun chip(label: String, state: String, ok: Boolean?, onClick: () -> Unit) {
        val bar = when (ok) { true -> p.fg; false -> p.accent; null -> p.faint }
        Row(
            Modifier.border(1.dp, if (ok == false) p.accent else p.line).clickable(onClick = onClick).height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(bar))
            Column(Modifier.padding(horizontal = 10.dp, vertical = 5.dp)) {
                Text(label, color = p.faint, fontSize = 10.sp, fontFamily = Hud.mono, letterSpacing = 1.5.sp)
                Text(state, color = if (ok == false) p.accent else p.fg, fontSize = 13.sp, fontFamily = Hud.mono)
            }
        }
    }
    val nasOk = s?.nas == Nas.OK
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            chip("NET", if (s == null) "----" else if (s.network) "ONLINE" else "OFFLINE", s?.network) { model.refreshStatus(force = true) }
            chip("TAILSCALE", when {
                s == null -> "----"
                !s.tailscale -> "未導入"
                s.vpn -> "ON"
                else -> "OFF"
            }, if (s == null || !s.tailscale) null else s.vpn) { openTailscale(actions) }
            chip("NAS", when {
                model.checking -> "SCAN…"
                s == null -> "----"
                s.nas == Nas.OK -> "LINKED"
                s.nas == Nas.UNSET -> "未登録"
                s.nas == Nas.AUTH -> "AUTH ERR"
                s.nas == Nas.NEED_OTP -> "2FA ›"
                s.nas == Nas.CERT -> "CERT ›"
                else -> "NO LINK"
            }, if (s == null || s.nas == Nas.UNSET) null else nasOk) {
                if (s?.nas == Nas.OK) model.openNas("") else openNasSettings()
            }
            if (model.safeMode) chip("SAFE MODE", "普通に戻す ›", false) {
                actions.activity.startActivity(Intent(actions.activity, jp.yaegaki.theta.home.MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                actions.activity.finish()
            }
            if (!model.isDefaultHome) chip("HOME", "未設定 ›", false) { requestHomeRole(actions) }
            val u = model.update
            if (u.newer && u.latest != null) {
                chip("UPDATE", if (u.progress != null) "%d%%".format((u.progress * 100).toInt()) else "v${u.latest.version} ›", false) { installUpdate(model, actions) }
            }
            chip("REQUEST", "依頼 ↗", null) { openRequest(actions) }
        }
        if (s != null && s.nas != Nas.OK && s.nas != Nas.UNSET && !model.checking) {
            Text(s.message, color = p.muted, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (s.tailscale && !s.vpn) PillButton("Tailscale を開く", { openTailscale(actions) }, primary = true)
                PillButton("再接続", { model.refreshStatus(force = true) })
                if (s.nas == Nas.AUTH) PillButton("接続先を直す", openNasSettings)
                if (s.nas == Nas.NEED_OTP) PillButton("コードを入れる", openNasSettings, primary = true)
                if (s.nas == Nas.CERT) PillButton("証明書を確かめる", openNasSettings, primary = true)
            }
        }
    }
}

/** Tailscale アプリを開く。入っていなければストアのページへ。VPN を勝手にオンにはしない。 */
fun openTailscale(actions: Actions) {
    val pm = actions.activity.packageManager
    val i = pm.getLaunchIntentForPackage(Nas.TAILSCALE) ?: pm.getLeanbackLaunchIntentForPackage(Nas.TAILSCALE)
    actions.start(i ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${Nas.TAILSCALE}")))
}

@Composable
private fun Places(model: HomeModel, modifier: Modifier, openNasSettings: () -> Unit) {
    val p = LocalPalette.current
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        SectionTitle("LOCATION / 場所", Modifier.padding(start = 4.dp, bottom = 6.dp))
        var n = 0
        model.tabs.filter { it.kind == TabKind.FOLDER }.forEach { t ->
            PlaceButton(++n, t.label, when {
                t.nasPath != null -> "NAS"
                t.driveFolder != null -> "DRIVE"
                t.folderUri == null -> "未割り当て"
                else -> "LOCAL"
            }, isActive(model, t)) { model.showFolder(t) }
        }
        PlaceButton(++n, "DRIVE", if (model.driveOn) "GOOGLE" else "未接続", model.location == "drive" && model.driveTab == null) { model.openDrive(listOf(GDrive.ROOT to "DRIVE")) }
        PlaceButton(++n, "NAS", if (model.nas.configured()) "SHARED" else "未登録", model.location == "nas" && model.nasTab == null) {
            if (model.nas.configured()) model.openNas("") else openNasSettings()
        }
        PlaceButton(++n, "最近", "RECENT", model.location == "recent") { model.showRecent() }
        Gap(10.dp)
        Note("フォルダは端のタブとも同じ。設定でタブを増やせます。")
    }
}

private fun isActive(model: HomeModel, t: TabSpec) =
    model.location == "tab:${t.id}" || model.location == "nas" && model.nasTab == t.id || model.location == "drive" && model.driveTab == t.id

@Composable
private fun PlaceButton(index: Int, title: String, sub: String, active: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().background(if (active) p.raised else Color.Transparent)
            .border(1.dp, if (active) p.accent else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("%02d".format(index), color = if (active) p.accent else p.faint, fontSize = 12.sp, fontFamily = Hud.mono)
        Column(Modifier.weight(1f)) {
            Text(title, color = if (active) p.accent else p.fg, fontSize = 18.sp, fontFamily = Hud.display, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, color = p.faint, fontSize = 10.sp, fontFamily = Hud.mono, letterSpacing = 1.sp)
        }
        if (active) Box(Modifier.size(6.dp).background(p.accent))
    }
}

@Composable
internal fun PlaceChips(model: HomeModel) {
    val p = LocalPalette.current
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        @Composable
        fun chip(label: String, active: Boolean, onClick: () -> Unit) {
            Text(
                label, color = if (active) p.onAccent else p.fg, fontSize = 13.sp,
                modifier = Modifier.clip(RoundedCornerShape(0.dp)).background(if (active) p.accent else p.raised).clickable(onClick = onClick)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
        model.tabs.filter { it.kind == TabKind.FOLDER }.forEach { t -> chip(t.label, isActive(model, t)) { model.showFolder(t) } }
        chip("NAS", model.location == "nas" && model.nasTab == null) { model.openNas("") }
        chip("DRIVE", model.location == "drive" && model.driveTab == null) { model.openDrive(listOf(GDrive.ROOT to "DRIVE")) }
        chip("最近", model.location == "recent") { model.showRecent() }
    }
}

@Composable
private fun FolderView(model: HomeModel, preview: Boolean, selected: Row?, onSelect: (Row?) -> Unit, openNasSettings: () -> Unit, openDriveSettings: () -> Unit) {
    val actions = LocalActions.current
    val loc = model.location
    val tab = model.tab(loc.removePrefix("tab:")).takeIf { loc.startsWith("tab:") }
    var editing by remember { mutableStateOf<SavedQuery?>(null) }
    editing?.let { q -> QueryDialog(model, q) { editing = null } }

    fun open(r: Row, where: String) {
        val e = r.entry
        val n = r.nas
        when {
            e != null -> {
                model.rememberOpened(where, e)
                actions.start(Files.openIntent(e.uri, e.mime))
            }
            n != null -> model.fetchNas(n) { f -> actions.start(Files.openIntent(Files.localUri(actions.activity, f), r.mime)) }
            r.drive != null -> openDriveItem(model, actions, r.drive)
        }
    }

    fun share(r: Row) {
        val e = r.entry
        val n = r.nas
        val d = r.drive
        when {
            e != null -> actions.start(Files.shareIntent(e.uri, e.mime, e.name))
            n != null -> model.fetchNas(n) { f -> actions.start(Files.shareIntent(Files.localUri(actions.activity, f), r.mime, f.name)) }
            d != null && d.isGoogleDoc -> d.link?.let { actions.start(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, it), "共有")) }
            d != null -> model.fetchDrive(d) { f -> actions.start(Files.shareIntent(Files.localUri(actions.activity, f), d.mime, f.name)) }
        }
    }

    when {
        loc == "recent" -> {
            val rows = model.recents.map { r ->
                val e = Entry(Uri.parse(r.uri), r.name, r.mime, false, 0, r.time)
                Row(r.uri, r.name, false, 0, r.time, r.mime, entry = e, note = "${r.where} · ${shortTime(r.time)}")
            }
            Listing(
                model = model,
                viewKeys = listOf("recent"),
                crumbs = listOf("RECENT"),
                onCrumb = {},
                canUp = false,
                onUp = {},
                rows = rows,
                loading = false,
                error = null,
                onRetry = {},
                preview = preview,
                selected = selected,
                onTap = { r -> if (preview) onSelect(r) else r.entry?.let { actions.start(Files.openIntent(it.uri, it.mime)) } },
                onOpen = { r -> r.entry?.let { actions.start(Files.openIntent(it.uri, it.mime)) } },
                onShare = ::share,
                extraMenu = { _, _ -> },
                empty = "フォルダから開いたファイルがここに並びます",
            )
        }
        loc == "nas" -> {
            if (!model.nas.configured()) {
                NeedPermission("NAS（Synology）を登録すると、ここで中のフォルダを辿れます。外出先からは Tailscale 経由でつなぎます。", "NAS を登録", openNasSettings)
                return
            }
            val s = model.nasState
            val crumbs = listOf("NAS" to "") + if (s.path.startsWith("?")) listOf((model.query(s.path.drop(1))?.name ?: "SEARCH") to s.path)
            else s.path.split('/').filter { it.isNotEmpty() }.let { parts ->
                parts.mapIndexed { i, name -> name to "/" + parts.take(i + 1).joinToString("/") }
            }
            Listing(
                model = model,
                viewKeys = crumbs.reversed().map { "nas:" + it.second },
                crumbs = crumbs.map { it.first },
                onCrumb = { i -> model.loadNas(crumbs[i].second) },
                canUp = s.path.isNotEmpty(),
                onUp = { model.nasUp() },
                rows = s.entries.map { e -> e.row().let { r -> if (e.path.startsWith("?")) r.copy(note = model.query(e.path.drop(1))?.summary()) else r } },
                loading = s.loading,
                error = s.error,
                onRetry = { model.refreshStatus(force = true); model.loadNas(s.path) },
                preview = preview,
                selected = selected,
                onTap = { r -> if (r.isDir) model.loadNas(r.nas!!.path) else if (preview) onSelect(r) else open(r, "NAS") },
                onOpen = { open(it, "NAS") },
                onShare = ::share,
                extraMenu = { r, close ->
                    if (r.isDir) {
                        val path = r.nas!!.path
                        val q = if (path.startsWith("?")) model.query(path.drop(1)) else null
                        if (q != null) DropdownMenuItem(text = { Text("条件を変える・消す") }, onClick = { close(); editing = q })
                        else DropdownMenuItem(text = { Text("この中の検索フォルダを作る") }, onClick = { close(); editing = newQuery("nas", path, r.name) })
                        model.tabs.filter { it.kind == TabKind.FOLDER }.forEach { t ->
                            DropdownMenuItem(text = { Text("「${t.label}」のタブにする") }, onClick = { close(); model.assignNasFolder(t.id, path); actions.toast("「${t.label}」を NAS の ${r.name} にしました") })
                        }
                    }
                },
                footer = if (s.path.isEmpty()) ({ FlowRow2 { PillButton("＋ 検索フォルダを作る", { editing = newQuery("nas") }) } }) else null,
            )
        }
        loc == "drive" -> {
            if (!model.driveOn) {
                NeedPermission("Google ドライブを、端末のフォルダと同じように辿れます（読み取りのみ）。初回だけ Google Cloud での登録が要ります。", "Google ドライブにつなぐ", openDriveSettings)
                return
            }
            val s = model.driveState
            val stack = s.stack.ifEmpty { listOf(GDrive.ROOT to "DRIVE") }
            Listing(
                model = model,
                viewKeys = stack.reversed().map { "drive:" + it.first },
                crumbs = stack.map { it.second },
                onCrumb = { i -> model.loadDrive(stack.take(i + 1)) },
                canUp = stack.size > 1,
                onUp = { model.driveUp() },
                rows = s.entries.map { e -> e.row().let { r -> if (e.id.startsWith(SavedQuery.PREFIX)) r.copy(note = model.query(e.id)?.summary()) else r } },
                loading = s.loading,
                error = s.error,
                onRetry = { model.loadDrive(stack) },
                preview = preview,
                selected = selected,
                onTap = { r -> if (r.isDir) model.loadDrive(stack + (r.drive!!.id to r.name)) else if (preview) onSelect(r) else open(r, "DRIVE") },
                onOpen = { open(it, "DRIVE") },
                onShare = ::share,
                extraMenu = { r, close ->
                    val d = r.drive
                    val q = d?.let { model.query(it.id) }
                    if (q != null) DropdownMenuItem(text = { Text("条件を変える・消す") }, onClick = { close(); editing = q })
                    else if (r.isDir && d != null && !d.id.startsWith("~")) DropdownMenuItem(text = { Text("この中の検索フォルダを作る") }, onClick = { close(); editing = newQuery("drive", d.id, d.name) })
                    if (r.isDir && d != null && !d.id.startsWith("~")) {
                        model.tabs.filter { it.kind == TabKind.FOLDER }.forEach { t ->
                            DropdownMenuItem(text = { Text("「${t.label}」のタブにする") }, onClick = { close(); model.assignDriveFolder(t.id, d.id, d.name); actions.toast("「${t.label}」をドライブの ${d.name} にしました") })
                        }
                    }
                    if (d?.link != null) DropdownMenuItem(text = { Text("ブラウザ・ドライブで開く") }, onClick = { close(); actions.start(Intent(Intent.ACTION_VIEW, Uri.parse(d.link))) })
                },
                footer = if (stack.last().first == GDrive.ROOT) ({ FlowRow2 { PillButton("＋ 検索フォルダを作る", { editing = newQuery("drive") }) } }) else null,
            )
        }
        tab != null -> {
            val state = model.folders[tab.id]
            if (tab.folderUri == null) {
                NeedPermission("「${tab.label}」に、よく使うフォルダを 1 つ割り当てます（端末内・SD カードなど）。NAS・Google ドライブのフォルダや検索フォルダにするときは、NAS・DRIVE で開いて長押し →「タブにする」。", "フォルダを選ぶ") {
                    actions.pickFolder(tab.id)
                }
                return
            }
            if (state?.error != null) {
                NeedPermission(state.error, "フォルダを選び直す") { actions.pickFolder(tab.id) }
                return
            }
            val st = state ?: return
            Listing(
                model = model,
                viewKeys = st.stack.reversed().map { "doc:" + it.first },
                crumbs = st.stack.map { it.second },
                onCrumb = { i -> model.jump(tab.id, i) },
                canUp = st.stack.size > 1,
                onUp = { model.up(tab.id) },
                rows = st.entries.map { it.row() },
                loading = st.loading,
                error = null,
                onRetry = {},
                preview = preview,
                selected = selected,
                onTap = { r -> if (r.isDir) model.enter(tab.id, r.entry!!) else if (preview) onSelect(r) else open(r, tab.label) },
                onOpen = { open(it, tab.label) },
                onShare = ::share,
                extraMenu = { _, _ -> },
                footer = { FlowRow2 { PillButton("割り当てるフォルダを変える", { actions.pickFolder(tab.id) }) } },
            )
        }
        else -> Note("左の「場所」かフォルダのタブを選んでください")
    }
}

/** 一覧の大きさ（S・M・L・XL）ごとの、サムネイルの辺とグリッドの 1 マスの最小幅。 */
private val listThumb = listOf(40.dp, 56.dp, 76.dp, 104.dp)
private val gridCell = listOf(84.dp, 112.dp, 152.dp, 208.dp)

/**
 * フォルダの中身。一覧（LIST）とグリッド（GRID）、大きさ 4 段。見せ方はフォルダごとに覚える。
 * viewKeys は今のフォルダから上へ順に並べたキー。設定の無いフォルダは親の見せ方を引き継ぐ。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Listing(
    model: HomeModel,
    viewKeys: List<String>,
    crumbs: List<String>,
    onCrumb: (Int) -> Unit,
    canUp: Boolean,
    onUp: () -> Unit,
    rows: List<Row>,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    preview: Boolean,
    selected: Row?,
    onTap: (Row) -> Unit,
    onOpen: (Row) -> Unit,
    onShare: (Row) -> Unit,
    extraMenu: @Composable (Row, () -> Unit) -> Unit,
    footer: (@Composable () -> Unit)? = null,
    empty: String = "空のフォルダです",
) {
    val p = LocalPalette.current
    val actions = LocalActions.current
    var menuFor by remember { mutableStateOf<String?>(null) }
    val files = rows.filter { !it.isDir }
    val mostlyMedia = files.isNotEmpty() && files.count { Thumbs.kind(it.name, it.mime).let { k -> k == "image" || k == "video" } } * 10 >= files.size * 6
    val spec = model.viewFor(viewKeys, mostlyMedia)
    val full: androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (canUp) GlyphButton(Glyph.BACK, "上のフォルダへ", onUp)
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                crumbs.forEachIndexed { i, name ->
                    if (i > 0) Text(" › ", color = p.faint, fontSize = 12.sp)
                    Text(name, color = if (i == crumbs.lastIndex) p.fg else p.muted, fontSize = 13.sp,
                        modifier = Modifier.clickable(onClickLabel = "ここへ戻る") { onCrumb(i) }.padding(vertical = 12.dp))
                }
            }
            if (loading) Text("読み込み中…", color = p.faint, fontSize = 12.sp)
            GlyphButton(if (spec.grid) Glyph.LIST else Glyph.GRID, if (spec.grid) "一覧で見る" else "グリッドで見る", { model.setView(viewKeys.first(), spec.copy(grid = !spec.grid)) })
            Box(
                Modifier.size(44.dp).clickable(onClickLabel = "大きさを変える（今 ${ViewSpec.NAMES[spec.size]}）") {
                    model.setView(viewKeys.first(), spec.copy(size = (spec.size + 1) % ViewSpec.SIZES))
                },
                contentAlignment = Alignment.Center,
            ) {
                Text(ViewSpec.NAMES[spec.size], color = p.muted, fontSize = 12.sp, fontFamily = Hud.mono, modifier = Modifier.border(1.dp, p.line).padding(horizontal = 5.dp, vertical = 2.dp))
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
        LazyVerticalGrid(
            columns = if (spec.grid) GridCells.Adaptive(gridCell[spec.size]) else GridCells.Fixed(1),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(if (spec.grid) 8.dp else 0.dp),
            verticalArrangement = Arrangement.spacedBy(if (spec.grid) 10.dp else 0.dp),
        ) {
            if (error != null) item(span = full) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(error, color = p.muted, fontSize = 13.sp)
                    PillButton("再接続", onRetry, primary = true)
                }
            }
            if (!loading && error == null && rows.isEmpty()) item(span = full) { Note(empty) }
            itemsIndexed(rows, key = { _, it -> it.key }) { zi, r ->
                val sub = r.note ?: if (r.isDir) null else listOfNotNull(if (r.modified > 0) shortTime(r.modified) else null, if (r.size > 0) Files.sizeText(r.size) else null).joinToString(" · ")
                val chosen = preview && selected?.key == r.key
                Box {
                    if (spec.grid) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .background(if (chosen) p.raised else Color.Transparent)
                                .combinedClickable(onClick = { onTap(r) }, onLongClick = { menuFor = r.key }, onClickLabel = if (r.isDir) "フォルダを開く" else "ファイルを開く")
                                .padding(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Thumb(model, r, Modifier.fillMaxWidth().aspectRatio(1f))
                            Text(r.name, color = p.fg, fontSize = if (spec.size == 0) 11.sp else 12.sp, lineHeight = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (spec.size >= 2 && !sub.isNullOrEmpty()) Text(sub, color = p.muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    } else {
                        RowButton(zebra = zi,
                            onClick = { onTap(r) },
                            onLongClick = { menuFor = r.key },
                            label = if (r.isDir) "フォルダを開く" else "ファイルを開く",
                            modifier = if (chosen) Modifier.background(p.raised, RoundedCornerShape(0.dp)) else Modifier,
                        ) {
                            Thumb(model, r, Modifier.size(listThumb[spec.size]))
                            TwoLines(r.name, sub, Modifier.weight(1f))
                        }
                    }
                    DropdownMenu(expanded = menuFor == r.key, onDismissRequest = { menuFor = null }) {
                        val close = { menuFor = null }
                        if (!r.isDir) {
                            DropdownMenuItem(text = { Text("開く") }, onClick = { close(); onOpen(r) })
                            DropdownMenuItem(text = { Text("共有") }, onClick = { close(); onShare(r) })
                            r.entry?.let { e ->
                                DropdownMenuItem(text = { Text("アプリを選んで開く") }, onClick = { close(); actions.start(Intent.createChooser(Files.openIntent(e.uri, e.mime), "開く")) })
                            }
                        }
                        extraMenu(r, close)
                    }
                }
            }
            footer?.let { f -> item(span = full) { Column { Gap(10.dp); f() } } }
        }
    }
}

/** サムネイル。写真・動画・PDF は中身を、それ以外は種類の札（フォルダはオレンジ）を出す。 */
@Composable
private fun Thumb(model: HomeModel, r: Row, modifier: Modifier) {
    val p = LocalPalette.current
    val ctx = LocalActions.current.activity
    BoxWithConstraints(modifier.clipToBounds().background(if (r.isDir) p.accent else p.raised).border(1.dp, if (r.isDir) p.accent else p.line), contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, maxHeight)
        val px = with(LocalDensity.current) { side.roundToPx() }
        val kind = if (r.isDir) null else Thumbs.kind(r.name, r.mime) ?: if (r.drive?.thumb != null) "doc" else null
        val bmp by produceState(r.entry?.let { Thumbs.cached(it.uri.toString(), px) }, r.key, px, kind) {
            if (kind == null) return@produceState
            val e = r.entry
            val n = r.nas
            value = when {
                e != null -> Thumbs.local(ctx, e.uri, r.name, r.mime, px)
                n != null -> Thumbs.nas(model.nas, n, px)
                r.drive != null -> Thumbs.drive(model.drive, r.drive, px)
                else -> null
            }
        }
        val b = bmp
        if (b != null) {
            Image(b.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (kind == "video") Text("▶", color = Color.White, fontSize = (side.value * 0.22f).coerceIn(10f, 28f).sp,
                modifier = Modifier.align(Alignment.BottomStart).background(Color(0x99000000)).padding(horizontal = 4.dp))
        } else if (r.isDir) {
            val virtual = r.drive?.id?.startsWith(SavedQuery.PREFIX) == true || r.nas?.path?.startsWith("?") == true
            GlyphIcon(if (virtual) Glyph.SEARCH else Glyph.FOLDER, p.onAccent, size = side * 0.5f)
        } else {
            Text(r.badge, color = p.muted, fontSize = (side.value * 0.24f).coerceIn(10f, 22f).sp, fontFamily = Hud.mono, maxLines = 1)
        }
    }
}

/** 広い画面で、選んだファイルの中身を右に出す。画像と文字だけ。NAS のファイルは開くときに取得する。 */
@Composable
private fun Preview(model: HomeModel, r: Row?, modifier: Modifier) {
    val p = LocalPalette.current
    val actions = LocalActions.current
    val ctx = actions.activity
    Column(modifier.border(1.dp, p.line).corners(p.accent).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("PREVIEW", color = p.accent, fontSize = 11.sp, fontFamily = Hud.mono, letterSpacing = 2.sp)
            Stripes(Modifier.weight(1f).height(6.dp), color = p.line)
        }
        if (r == null) {
            Note("ファイルを選ぶと、ここに中身が出ます")
            return@Column
        }
        Text(r.name, color = p.fg, fontSize = 20.sp, fontFamily = Hud.display)
        Text(listOfNotNull(if (r.modified > 0) shortTime(r.modified) else null, Files.sizeText(r.size)).joinToString(" · "), color = p.muted, fontSize = 12.sp)
        val e = r.entry
        if (e != null && e.mime?.startsWith("image/") == true) {
            val bmp by produceState<android.graphics.Bitmap?>(null, e.uri) {
                value = withContext(Dispatchers.IO) {
                    runCatching {
                        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        ctx.contentResolver.openInputStream(e.uri)?.use { BitmapFactory.decodeStream(it, null, o) }
                        var sample = 1
                        while (maxOf(o.outWidth, o.outHeight) / sample > 1200) sample *= 2
                        ctx.contentResolver.openInputStream(e.uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
                    }.getOrNull()
                }
            }
            bmp?.let { Image(it.asImageBitmap(), contentDescription = r.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)) }
        } else if (e != null && (e.mime?.startsWith("text/") == true || e.name.endsWith(".md") || e.name.endsWith(".csv"))) {
            val text by produceState("…", e.uri) {
                value = withContext(Dispatchers.IO) {
                    runCatching { ctx.contentResolver.openInputStream(e.uri)?.use { ByteArray(32 * 1024).let { buf -> String(buf, 0, maxOf(0, it.read(buf))) } } }.getOrNull() ?: "読めませんでした"
                }
            }
            Text(text, color = p.fg, fontSize = 13.sp, modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()))
        } else if (r.nas != null) {
            Note("NAS のファイルです。開く・共有のときに端末へ取得します。")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("開く", {
                if (e != null) {
                    model.rememberOpened("", e); actions.start(Files.openIntent(e.uri, e.mime))
                } else r.nas?.let { n -> model.fetchNas(n) { f -> actions.start(Files.openIntent(Files.localUri(ctx, f), r.mime)) } }
            }, primary = true)
            PillButton("共有", {
                if (e != null) actions.start(Files.shareIntent(e.uri, e.mime, e.name))
                else r.nas?.let { n -> model.fetchNas(n) { f -> actions.start(Files.shareIntent(Files.localUri(ctx, f), r.mime, f.name)) } }
            })
        }
    }
}

/** NAS の接続先。パスワードは端末の鍵で暗号化して保存する。 */
@Composable
fun NasDialog(model: HomeModel, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    var url by remember { mutableStateOf(model.nas.base()) }
    var account by remember { mutableStateOf(model.nas.account()) }
    var password by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var tried by remember { mutableStateOf(false) }
    val st = model.status
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("NAS（Synology）") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Field(url, { url = it.trim() }, "100.x.x.x（:5000 は省略可）")
                Field(account, { account = it }, "アカウント")
                Field(password, { password = it }, if (model.nas.configured()) "パスワード（変えるときだけ）" else "パスワード", password = true)
                if (st?.nas == Nas.NEED_OTP || otp.isNotEmpty()) Field(otp, { otp = it.filter(Char::isDigit).take(8) }, "2 段階認証のコード（6 桁）")
                if (tried || st?.nas == Nas.NEED_OTP || st?.nas == Nas.CERT) {
                    val ok = st?.nas == Nas.OK
                    Text(
                        if (model.checking) "確かめています…" else st?.message.orEmpty(),
                        color = if (ok) p.fg else p.accent, fontSize = 13.sp, lineHeight = 19.sp,
                    )
                    val fp = st?.cert
                    if (!model.checking && st?.nas == Nas.CERT && fp != null) {
                        Text("SHA-256\n$fp", color = p.muted, fontSize = 11.sp, fontFamily = Hud.mono, lineHeight = 16.sp)
                        Text(
                            if (model.nas.trustedCert().isEmpty()) "DSM の「コントロールパネル → セキュリティ → 証明書」で同じ指紋か確かめてから信頼してください。"
                            else "前に信頼した証明書と違います。NAS で証明書を更新したのでなければ、つながないでください。",
                            color = p.muted, fontSize = 12.sp, lineHeight = 18.sp,
                        )
                        PillButton("この証明書を信頼する", { model.trustNasCert(fp) }, primary = true)
                    }
                }
                Text(
                    "DSBooks と同じ DSM の File Station でつなぎます。家の中は NAS の IP（192.168.x.x）、外出先からは Tailscale のアドレス（100.x.x.x か 〇〇.ts.net）。" +
                        "https（:5001）の自己署名証明書は、初回に指紋を確かめて信頼できます。2 段階認証は、求められたら 1 回だけコードを入れれば、この端末は次から要りません。",
                    color = p.muted, fontSize = 12.sp, lineHeight = 18.sp,
                )
            }
        },
        confirmButton = {
            if (tried && st?.nas == Nas.OK && !model.checking) TextButton(onClick = onDismiss) { Text("閉じる") }
            else TextButton(onClick = { tried = true; model.saveNas(url, account, password, otp); otp = "" }, enabled = url.isNotBlank() && account.isNotBlank() && !model.checking) { Text("保存して接続") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("やめる") } },
    )
}

private data class Hit(val kind: String, val name: String, val where: String, val go: () -> Unit)

@Composable
internal fun SearchResults(model: HomeModel) {
    val p = LocalPalette.current
    val actions = LocalActions.current
    val q = model.query.trim()
    val hits = remember(q, model.apps, model.people, model.recents, model.bookmarks, model.tabs, model.nasState) {
        buildList {
            model.tabs.filter { it.label.contains(q, true) }.forEach { t -> add(Hit("タブ", t.label, t.kind.defaultLabel) { model.query = ""; model.toggle(t.id) }) }
            model.apps.filter { it.label.contains(q, true) }.take(8).forEach { a -> add(Hit("アプリ", a.label, "") { model.query = ""; actions.start(Apps.launchIntent(a)) }) }
            model.recents.filter { it.name.contains(q, true) }.forEach { r ->
                add(Hit("ファイル", r.name, r.where) { actions.start(Files.openIntent(Uri.parse(r.uri), r.mime)) })
            }
            val here = model.location.removePrefix("tab:").let { model.folders[it]?.entries }.orEmpty()
            here.filter { it.name.contains(q, true) }.forEach { e -> add(Hit("ここ", e.name, "今のフォルダ") { actions.start(Files.openIntent(e.uri, e.mime)) }) }
            if (model.location == "nas") model.nasState.entries.filter { it.name.contains(q, true) }.forEach { n ->
                add(Hit("NAS", n.name, "今のフォルダ") { if (n.dir) { model.query = ""; model.loadNas(n.path) } else model.fetchNas(n) { f -> actions.start(Files.openIntent(Files.localUri(actions.activity, f), Files.mimeOf(n.name))) } })
            }
            model.people.filter { it.name.contains(q, true) }.take(6).forEach { person ->
                val tab = model.tabs.firstOrNull { it.kind == TabKind.PEOPLE }
                add(Hit("人", person.name, person.phone ?: person.email ?: "") {
                    model.query = ""
                    if (tab != null) model.toggle(tab.id) else actions.start(Contacts.viewIntent(person))
                })
            }
            model.bookmarks.filter { it.name.contains(q, true) || it.url.contains(q, true) }.forEach { b ->
                add(Hit("栞", b.name, Uri.parse(b.url).host ?: "") { actions.start(Intent(Intent.ACTION_VIEW, Uri.parse(b.url))) })
            }
            HatchCatalog.apps.filter { it.name.contains(q, true) }.forEach { a ->
                val tab = model.tabs.firstOrNull { it.kind == TabKind.HATCH }
                add(Hit("Hatch", a.name, a.summary) {
                    model.query = ""
                    val launch = a.packageName?.let { actions.activity.packageManager.getLaunchIntentForPackage(it) }
                    if (launch != null) actions.start(launch) else tab?.let { model.toggle(it.id) }
                })
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        SectionTitle("検索結果 ${hits.size} 件")
        hits.forEachIndexed { zi, h ->
            RowButton(zebra = zi, onClick = h.go) {
                Text(h.kind, color = p.muted, fontSize = 11.sp, modifier = Modifier.width(56.dp))
                Text(h.name, color = p.fg, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(h.where, color = p.muted, fontSize = 12.sp, maxLines = 1)
            }
        }
        if (hits.isEmpty()) Text("見つかりませんでした", color = p.faint, fontSize = 14.sp, modifier = Modifier.padding(vertical = 10.dp))
        Text(
            "Web で「$q」を検索", color = p.accent, fontSize = 14.sp,
            modifier = Modifier.fillMaxWidth().clickable {
                actions.start(Intent(Intent.ACTION_WEB_SEARCH).putExtra(android.app.SearchManager.QUERY, q))
            }.padding(vertical = 12.dp),
        )
    }
}

/** 機能追加を頼むための開発の会話（Claude Code）を開く。 */
fun openRequest(actions: Actions) {
    actions.start(Intent(Intent.ACTION_VIEW, Uri.parse(jp.yaegaki.theta.home.hatch.SelfUpdate.REQUEST_URL)))
}

/** 新しい版を入れる：提供元不明のアプリの許可 → 取得とハッシュ照合 → Android の確認画面。 */
fun installUpdate(model: HomeModel, actions: Actions) {
    val ctx = actions.activity
    jp.yaegaki.theta.home.hatch.HatchInstaller.permissionIntent(ctx)?.let {
        actions.toast("「この提供元のアプリを許可」をオンにしてから、もう一度押してください")
        actions.start(it)
        return
    }
    actions.toast("新しい版を取得しています…")
    model.downloadUpdate { file ->
        runCatching { jp.yaegaki.theta.home.hatch.HatchInstaller.install(ctx, file) }.onFailure { actions.toast("更新を始められませんでした") }
    }
}
