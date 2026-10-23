package jp.yaegaki.theta.home.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.yaegaki.theta.home.HomeModel
import jp.yaegaki.theta.home.capture.RecordingService
import jp.yaegaki.theta.home.data.AppEntry
import jp.yaegaki.theta.home.data.Apps
import jp.yaegaki.theta.home.data.Calendar
import jp.yaegaki.theta.home.data.Files
import jp.yaegaki.theta.home.data.TabKind
import jp.yaegaki.theta.home.hatch.HatchCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class Hit(val kind: String, val name: String, val where: String, val go: () -> Unit)

/** ホーム：時計・次の予定・最近のファイル・よく使うアプリ・よく連絡する人だけ。ほかは端のタブにしまう。 */
@Composable
fun HomeContent(model: HomeModel) {
    val p = LocalPalette.current
    val actions = LocalActions.current
    val layout = LocalLayout.current
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis(); delay(10_000)
        }
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(model.focusSearch) { if (model.focusSearch > 0) runCatching { focus.requestFocus() } }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (layout.compact) 22.dp else 44.dp, vertical = if (layout.compact) 20.dp else 36.dp),
        verticalArrangement = Arrangement.spacedBy(26.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Field(
                model.query, { model.query = it }, "アプリ・ファイル・人・栞を検索",
                Modifier.weight(1f).focusRequester(focus),
                leading = { GlyphIcon(Glyph.SEARCH, p.muted) },
            )
            GlyphButton(Glyph.GEAR, "設定", { model.settingsOpen = true })
        }

        if (model.query.isNotBlank()) {
            SearchResults(model)
            return@Column
        }

        Clock(now, layout.compact)
        RecordingBanner()

        if (layout.wideHome) {
            Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(26.dp)) {
                    NextEvents(model)
                    People(model)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(26.dp)) {
                    RecentFiles(model)
                    FavoriteApps(model)
                }
            }
        } else {
            NextEvents(model)
            RecentFiles(model)
            FavoriteApps(model)
            People(model)
        }

        val side = if (model.railLeft) "左" else "右"
        Text("${side}端のタブを押す、または端から内側へなぞるとドロワーが出ます。", color = p.faint, fontSize = 12.sp)
    }
}

@Composable
private fun Clock(now: Long, compact: Boolean) {
    val p = LocalPalette.current
    val d = Date(now)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            SimpleDateFormat("H:mm", Locale.JAPAN).format(d),
            color = p.fg, fontSize = if (compact) 76.sp else 104.sp, fontWeight = FontWeight.Light,
            fontFamily = FontFamily.SansSerif, letterSpacing = (-2).sp, lineHeight = if (compact) 80.sp else 108.sp,
        )
        Text(SimpleDateFormat("M月d日 EEEE", Locale.JAPAN).format(d), color = p.muted, fontSize = 16.sp)
    }
}

@Composable
private fun RecordingBanner() {
    val p = LocalPalette.current
    val actions = LocalActions.current
    val since by RecordingService.startedAt.collectAsState()
    val start = since ?: return
    val now by produceState(System.currentTimeMillis(), start) {
        while (true) {
            value = System.currentTimeMillis(); delay(1000)
        }
    }
    Row(
        Modifier.clip(RoundedCornerShape(24.dp)).background(p.raised).padding(start = 18.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Dot(p.accent)
        Text("録音中 ${(now - start) / 1000}秒", color = p.fg, fontSize = 14.sp)
        PillButton("停止", { RecordingService.stop(actions.activity) }, primary = true)
    }
}

@Composable
private fun NextEvents(model: HomeModel) {
    val p = LocalPalette.current
    val actions = LocalActions.current
    val calTab = model.tabs.firstOrNull { it.kind == TabKind.CALENDAR }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("次の予定", Modifier.weight(1f))
            if (calTab != null) Text("すべて", color = p.accent, fontSize = 12.sp, modifier = Modifier.clickable { model.toggle(calTab.id) }.padding(8.dp))
        }
        if (!model.has(Manifest.permission.READ_CALENDAR)) {
            Text(
                "予定を表示する", color = p.accent, fontSize = 14.sp,
                modifier = Modifier.clickable {
                    actions.requestPermissions(arrayOf(Manifest.permission.READ_CALENDAR)) { model.refreshEvents() }
                }.padding(vertical = 12.dp),
            )
            return@Column
        }
        val next = model.events.take(2)
        if (next.isEmpty()) Text("予定はありません", color = p.faint, fontSize = 14.sp, modifier = Modifier.padding(vertical = 10.dp))
        next.forEach { e ->
            RowButton(onClick = { actions.start(Calendar.openIntent(e)) }) {
                Text(
                    if (e.allDay) "終日" else SimpleDateFormat("H:mm", Locale.JAPAN).format(Date(e.begin)),
                    color = p.fg, fontSize = 19.sp, modifier = Modifier.width(62.dp),
                )
                Box(Modifier.width(3.dp).height(34.dp).background(p.widget))
                TwoLines(e.title, listOfNotNull(dayLabel(e.localBegin), e.location).joinToString(" · "), Modifier.weight(1f))
            }
        }
    }
}

private fun dayLabel(t: Long): String? {
    val f = SimpleDateFormat("yyyyMMdd", Locale.US)
    val today = f.format(Date())
    val tomorrow = f.format(Date(System.currentTimeMillis() + 24L * 3600_000))
    return when (f.format(Date(t))) {
        today -> null
        tomorrow -> "明日"
        else -> SimpleDateFormat("M/d（E）", Locale.JAPAN).format(Date(t))
    }
}

@Composable
private fun RecentFiles(model: HomeModel) {
    val p = LocalPalette.current
    val actions = LocalActions.current
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        SectionTitle("最近のファイル")
        if (model.recents.isEmpty()) {
            Text("フォルダのタブから開いたファイルがここに並びます", color = p.faint, fontSize = 13.sp, modifier = Modifier.padding(vertical = 10.dp))
        }
        model.recents.take(4).forEach { r ->
            RowButton(onClick = { actions.start(Files.openIntent(Uri.parse(r.uri), r.mime)) }) {
                Badge(r.name.substringAfterLast('.', "FILE").uppercase(Locale.ROOT).take(4))
                Text(r.name, color = p.fg, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(r.where, color = p.muted, fontSize = 12.sp)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FavoriteApps(model: HomeModel) {
    val p = LocalPalette.current
    val actions = LocalActions.current
    val apps = model.favoriteApps.mapNotNull { key -> model.apps.firstOrNull { it.key == key } }
    val appsTab = model.tabs.firstOrNull { it.kind == TabKind.APPS }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("よく使うアプリ")
        if (apps.isEmpty()) {
            Text(
                "アプリのタブで長押し →「ホームに置く」", color = p.faint, fontSize = 13.sp,
                modifier = Modifier.clickable(enabled = appsTab != null) { appsTab?.let { model.toggle(it.id) } }.padding(vertical = 6.dp),
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            apps.forEach { app -> AppTile(app, model.monoIcons) { actions.start(Apps.launchIntent(app)) } }
        }
    }
}

@Composable
private fun AppTile(app: AppEntry, mono: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalActions.current.activity
    val px = with(LocalDensity.current) { 36.dp.roundToPx() }
    val icon by produceState<android.graphics.Bitmap?>(null, app.key) { value = withContext(Dispatchers.IO) { Apps.icon(ctx, app, px) } }
    val filter = remember(mono) { if (mono) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) else null }
    Column(
        Modifier.width(64.dp).clip(RoundedCornerShape(12.dp)).clickable(onClickLabel = "${app.label}を開く", onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        icon?.let { Image(it.asImageBitmap(), contentDescription = null, colorFilter = filter, modifier = Modifier.size(36.dp)) }
            ?: Spacer(Modifier.size(36.dp))
        Text(app.label, color = p.muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun People(model: HomeModel) {
    val p = LocalPalette.current
    val starred = model.people.filter { it.starred }.take(6)
    val peopleTab = model.tabs.firstOrNull { it.kind == TabKind.PEOPLE } ?: return
    if (!model.has(Manifest.permission.READ_CONTACTS)) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("よく連絡する人")
        if (starred.isEmpty()) Text("連絡先でスターを付けた人がここに並びます", color = p.faint, fontSize = 13.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            starred.forEach { person ->
                Column(
                    Modifier.width(60.dp).clip(RoundedCornerShape(12.dp)).clickable(onClickLabel = "${person.name}への連絡") { model.toggle(peopleTab.id) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Initial(person.initial, 52.dp)
                    Text(person.name, color = p.muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun SearchResults(model: HomeModel) {
    val p = LocalPalette.current
    val actions = LocalActions.current
    val q = model.query.trim()
    val hits = remember(q, model.apps, model.people, model.recents, model.bookmarks, model.tabs) {
        buildList {
            model.tabs.filter { it.label.contains(q, true) }.forEach { t -> add(Hit("タブ", t.label, t.kind.defaultLabel) { model.query = ""; model.toggle(t.id) }) }
            model.apps.filter { it.label.contains(q, true) }.take(8).forEach { a -> add(Hit("アプリ", a.label, "") { model.query = ""; actions.start(Apps.launchIntent(a)) }) }
            model.recents.filter { it.name.contains(q, true) }.forEach { r ->
                add(Hit("ファイル", r.name, r.where) { actions.start(Files.openIntent(Uri.parse(r.uri), r.mime)) })
            }
            model.people.filter { it.name.contains(q, true) }.take(6).forEach { person ->
                val tab = model.tabs.firstOrNull { it.kind == TabKind.PEOPLE }
                add(Hit("人", person.name, person.phone ?: person.email ?: "") {
                    model.query = ""
                    if (tab != null) model.toggle(tab.id) else actions.start(jp.yaegaki.theta.home.data.Contacts.viewIntent(person))
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
        hits.forEach { h ->
            RowButton(onClick = h.go) {
                Text(h.kind, color = p.muted, fontSize = 11.sp, modifier = Modifier.width(56.dp))
                Text(h.name, color = p.fg, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(h.where, color = p.muted, fontSize = 12.sp, maxLines = 1)
            }
        }
        if (hits.isEmpty()) Text("見つかりませんでした", color = p.faint, fontSize = 14.sp, modifier = Modifier.padding(vertical = 10.dp))
        Spacer(Modifier.height(8.dp))
        Text(
            "Web で「$q」を検索", color = p.accent, fontSize = 14.sp,
            modifier = Modifier.fillMaxWidth().clickable {
                actions.start(Intent(Intent.ACTION_WEB_SEARCH).putExtra(android.app.SearchManager.QUERY, q))
            }.padding(vertical = 12.dp),
        )
    }
}
