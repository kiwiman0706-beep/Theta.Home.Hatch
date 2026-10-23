package jp.yaegaki.theta.home.ui

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
import androidx.compose.foundation.lazy.itemsIndexed
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
) {
    val badge: String get() = if (isDir) "DIR" else name.substringAfterLast('.', "FILE").uppercase(Locale.ROOT).take(4)
}

private fun Entry.row() = Row(uri.toString(), name, isDir, size, modified, mime, entry = this)

private fun Nas.Item.row() = Row("nas:$path", name, dir, size, modified, if (dir) null else Files.mimeOf(name), nas = this)

/**
 * 背景（デスクトップ）のファイラー。スマホでもファイルの置き場をホームにする。
 * 上：時計・次の予定・検索、ネットワーク・Tailscale・NAS の状態。下：場所（フォルダのタブ・NAS・最近）とファイルの一覧。
 * 広い画面では選んだファイルの中身を右に出す。
 */
@Composable
fun Filer(model: HomeModel) {
    val layout = LocalLayout.current
    var nasDialog by remember { mutableStateOf(false) }
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
                        FolderView(model, preview = showPreview, selected = selected, onSelect = { selected = it }) { nasDialog = true }
                    }
                    if (showPreview) Preview(model, selected, Modifier.width(300.dp).fillMaxHeight())
                }
            }
        }
        FilerFooter(model)
    }
    if (nasDialog) NasDialog(model) { nasDialog = false }
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

@Composable
private fun FilerHeader(model: HomeModel) {
    val p = LocalPalette.current
    val layout = LocalLayout.current
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis(); delay(10_000)
        }
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(model.focusSearch) { if (model.focusSearch > 0) runCatching { focus.requestFocus() } }
    val next = if (model.has(Manifest.permission.READ_CALENDAR)) model.events.firstOrNull() else null
    val calTab = model.tabs.firstOrNull { it.kind == TabKind.CALENDAR }
    val d = Date(now)
    val nextText = next?.let { (if (it.allDay) "ALL DAY" else SimpleDateFormat("HH:mm", Locale.US).format(Date(it.begin))) + "  " + it.title }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(6.dp).background(p.accent))
                    Text("SYS.TIME", color = p.faint, fontSize = 10.sp, fontFamily = Hud.mono, letterSpacing = 2.sp)
                }
                Text(
                    SimpleDateFormat("HH:mm", Locale.US).format(d), color = p.fg, fontFamily = Hud.display, fontWeight = FontWeight.Light,
                    fontSize = if (layout.compact) 52.sp else 72.sp, lineHeight = if (layout.compact) 56.sp else 76.sp, letterSpacing = 1.sp,
                )
            }
            Column(Modifier.padding(bottom = 8.dp)) {
                Text(SimpleDateFormat("yyyy.MM.dd", Locale.US).format(d), color = p.fg, fontSize = 14.sp, fontFamily = Hud.mono)
                Text(SimpleDateFormat("EEE", Locale.US).format(d).uppercase(Locale.US) + " / " + SimpleDateFormat("E曜日", Locale.JAPAN).format(d), color = p.accent, fontSize = 12.sp, fontFamily = Hud.mono)
            }
            if (!layout.compact && nextText != null) {
                Readout("NEXT_EVENT", nextText, Modifier.weight(1f).padding(bottom = 8.dp).clickable(onClickLabel = "予定を開く") { calTab?.let { model.toggle(it.id) } }, accent = true)
            } else {
                Spacer(Modifier.weight(1f))
            }
            if (!layout.compact) {
                Column(Modifier.padding(bottom = 8.dp), horizontalAlignment = Alignment.End) {
                    Text("THETA // HOME.HATCH", color = p.faint, fontSize = 10.sp, fontFamily = Hud.mono, letterSpacing = 1.sp)
                    Stripes(Modifier.width(96.dp).height(8.dp))
                }
            }
        }
        Ticks(Modifier.fillMaxWidth().height(6.dp))
        if (layout.compact && nextText != null) {
            Readout("NEXT_EVENT", nextText, Modifier.clickable(onClickLabel = "予定を開く") { calTab?.let { model.toggle(it.id) } }, accent = true)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Field(model.query, { model.query = it }, "SEARCH  検索", Modifier.weight(1f).focusRequester(focus), leading = { GlyphIcon(Glyph.SEARCH, p.accent) })
            GlyphButton(Glyph.GEAR, "設定", { model.settingsOpen = true })
        }
    }
}

/** ネットワーク・Tailscale・NAS の状態。つながっていなければ、Tailscale を開く・再接続の手助けを出す。 */
@Composable
private fun StatusStrip(model: HomeModel, openNasSettings: () -> Unit) {
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
                else -> "NO LINK"
            }, if (s == null || s.nas == Nas.UNSET) null else nasOk) {
                if (s?.nas == Nas.OK) model.openNas("") else openNasSettings()
            }
        }
        if (s != null && s.nas != Nas.OK && s.nas != Nas.UNSET && !model.checking) {
            Text(s.message, color = p.muted, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (s.tailscale && !s.vpn) PillButton("Tailscale を開く", { openTailscale(actions) }, primary = true)
                PillButton("再接続", { model.refreshStatus(force = true) })
                if (s.nas == Nas.AUTH) PillButton("接続先を直す", openNasSettings)
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
            PlaceButton(++n, t.label, if (t.nasPath != null) "NAS" else if (t.folderUri == null) "未割り当て" else "LOCAL", isActive(model, t)) { model.showFolder(t) }
        }
        PlaceButton(++n, "NAS", if (model.nas.configured()) "SHARED" else "未登録", model.location == "nas" && model.nasTab == null) {
            if (model.nas.configured()) model.openNas("") else openNasSettings()
        }
        PlaceButton(++n, "最近", "RECENT", model.location == "recent") { model.showRecent() }
        Gap(10.dp)
        Note("フォルダは端のタブとも同じ。設定でタブを増やせます。")
    }
}

private fun isActive(model: HomeModel, t: TabSpec) =
    model.location == "tab:${t.id}" || model.location == "nas" && model.nasTab == t.id

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
private fun PlaceChips(model: HomeModel) {
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
        chip("最近", model.location == "recent") { model.showRecent() }
    }
}

@Composable
private fun FolderView(model: HomeModel, preview: Boolean, selected: Row?, onSelect: (Row?) -> Unit, openNasSettings: () -> Unit) {
    val actions = LocalActions.current
    val loc = model.location
    val tab = model.tab(loc.removePrefix("tab:")).takeIf { loc.startsWith("tab:") }

    fun open(r: Row, where: String) {
        val e = r.entry
        val n = r.nas
        when {
            e != null -> {
                model.rememberOpened(where, e)
                actions.start(Files.openIntent(e.uri, e.mime))
            }
            n != null -> model.fetchNas(n) { f -> actions.start(Files.openIntent(Files.localUri(actions.activity, f), r.mime)) }
        }
    }

    fun share(r: Row) {
        val e = r.entry
        val n = r.nas
        when {
            e != null -> actions.start(Files.shareIntent(e.uri, e.mime, e.name))
            n != null -> model.fetchNas(n) { f -> actions.start(Files.shareIntent(Files.localUri(actions.activity, f), r.mime, f.name)) }
        }
    }

    when {
        loc == "recent" -> {
            DrawerList {
                if (model.recents.isEmpty()) item { Note("フォルダから開いたファイルがここに並びます") }
                itemsIndexed(model.recents, key = { _, it -> it.uri }) { zi, r ->
                    RowButton(zebra = zi, onClick = { actions.start(Files.openIntent(Uri.parse(r.uri), r.mime)) }) {
                        Badge(r.name.substringAfterLast('.', "FILE").uppercase(Locale.ROOT).take(4))
                        TwoLines(r.name, "${r.where} · ${shortTime(r.time)}", Modifier.weight(1f))
                    }
                }
            }
        }
        loc == "nas" -> {
            if (!model.nas.configured()) {
                NeedPermission("NAS（Synology）を登録すると、ここで中のフォルダを辿れます。外出先からは Tailscale 経由でつなぎます。", "NAS を登録", openNasSettings)
                return
            }
            val s = model.nasState
            val crumbs = listOf("NAS" to "") + s.path.split('/').filter { it.isNotEmpty() }.let { parts ->
                parts.mapIndexed { i, name -> name to "/" + parts.take(i + 1).joinToString("/") }
            }
            Listing(
                crumbs = crumbs.map { it.first },
                onCrumb = { i -> model.loadNas(crumbs[i].second) },
                canUp = s.path.isNotEmpty(),
                onUp = { model.nasUp() },
                rows = s.entries.map { it.row() },
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
                        model.tabs.filter { it.kind == TabKind.FOLDER }.forEach { t ->
                            DropdownMenuItem(text = { Text("「${t.label}」のタブにする") }, onClick = { close(); model.assignNasFolder(t.id, r.nas!!.path); actions.toast("「${t.label}」を NAS の ${r.name} にしました") })
                        }
                    }
                },
            )
        }
        tab != null -> {
            val state = model.folders[tab.id]
            if (tab.folderUri == null) {
                NeedPermission("「${tab.label}」に、よく使うフォルダを 1 つ割り当てます（端末内・SD カード・Google ドライブなど）。NAS のフォルダにするときは、NAS で開いて長押し →「タブにする」。", "フォルダを選ぶ") {
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

@Composable
private fun Listing(
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
) {
    val p = LocalPalette.current
    val actions = LocalActions.current
    var menuFor by remember { mutableStateOf<String?>(null) }
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
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
        DrawerList {
            if (error != null) item {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(error, color = p.muted, fontSize = 13.sp)
                    PillButton("再接続", onRetry, primary = true)
                }
            }
            if (!loading && error == null && rows.isEmpty()) item { Note("空のフォルダです") }
            itemsIndexed(rows, key = { _, it -> it.key }) { zi, r ->
                Box {
                    RowButton(zebra = zi, 
                        onClick = { onTap(r) },
                        onLongClick = { menuFor = r.key },
                        label = if (r.isDir) "フォルダを開く" else "ファイルを開く",
                        modifier = if (preview && selected?.key == r.key) Modifier.background(p.raised, RoundedCornerShape(0.dp)) else Modifier,
                    ) {
                        Badge(r.badge, highlight = r.isDir)
                        TwoLines(r.name, if (r.isDir) null else listOfNotNull(if (r.modified > 0) shortTime(r.modified) else null, Files.sizeText(r.size)).joinToString(" · "), Modifier.weight(1f))
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
            footer?.let { f -> item { Gap(10.dp); f() } }
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
    var url by remember { mutableStateOf(model.nas.base()) }
    var account by remember { mutableStateOf(model.nas.account()) }
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("NAS（Synology）") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Field(url, { url = it.trim() }, "http://100.x.x.x:5000")
                Field(account, { account = it }, "アカウント")
                Field(password, { password = it }, if (model.nas.configured()) "パスワード（変えるときだけ）" else "パスワード", password = true)
                Text(
                    "DSBooks と同じ DSM の File Station でつなぎます。外出先からは Tailscale 経由のアドレス（Tailscale の画面に出る 100.x.x.x、または 〇〇.ts.net）を入れてください。2 段階認証のアカウントには未対応です。",
                    color = LocalPalette.current.muted, fontSize = 12.sp, lineHeight = 18.sp,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { model.saveNas(url, account, password); onDismiss() }, enabled = url.isNotBlank() && account.isNotBlank()) { Text("保存して接続") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("やめる") } },
    )
}

private data class Hit(val kind: String, val name: String, val where: String, val go: () -> Unit)

@Composable
private fun SearchResults(model: HomeModel) {
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
