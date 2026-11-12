package app.thetahatch.home.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.thetahatch.home.HomeModel
import app.thetahatch.home.hub.Catalog
import app.thetahatch.home.hub.HatchShelf
import app.thetahatch.home.hub.UpdateHub
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Hatch のアプリのタブ：そのアプリの棚（MemoHatch のピン留めメモ、Music のお気に入りなど）と、アプリを開くボタン。
 * id は catalog の id（"memo"・"music" など）。
 */
@Composable
fun ShelfView(model: HomeModel, id: String) {
    val p = LocalPalette.current
    val actions = LocalActions.current
    val ctx = actions.activity
    val app = Catalog.apps.value.firstOrNull { it.id == id }
    val pkg = app?.installedPackage(UpdateHub.versionOf(ctx))
    var tick by remember { mutableStateOf(0) }
    val items by produceState<List<HatchShelf.Item>?>(null, pkg, tick, model.resumeTick) {
        value = pkg?.let { withContext(Dispatchers.IO) { HatchShelf.read(ctx, it) } }
    }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(app?.name ?: id, color = p.fg, fontSize = 15.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (pkg != null) {
                PillButton("開く", { ctx.packageManager.getLaunchIntentForPackage(pkg)?.let(actions.start) }, primary = true)
                MiniButton("↻", "読み直す") { tick++ }
            }
        }
        // MemoHatch のタブ：いちばん上に、新しいメモのボタン（MemoHatch の入力の段と同じ）
        if (id == "memo" && pkg != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    Triple(Glyph.FILE, "文字のメモ", "text"), Triple(Glyph.LIST, "チェックリスト", "list"),
                    Triple(Glyph.PENCIL, "手書き", "ink"), Triple(Glyph.MIC, "声のメモ", "audio"),
                ).forEach { (g, desc, type) ->
                    Box(
                        Modifier.weight(1f).height(44.dp).background(p.accent).clickable(onClickLabel = desc) { model.memoNew(type, actions.start) }
                            .semantics { contentDescription = desc },
                        contentAlignment = Alignment.Center,
                    ) { GlyphIcon(g, p.onAccent, 22.dp) }
                }
                Box(
                    Modifier.weight(1f).height(44.dp).background(p.accent).clickable(onClickLabel = "カメラ") { if (!model.openCamera(actions.start)) model.memoNew("photo", actions.start) }
                        .semantics { contentDescription = "カメラ（Theta Camera Hatch）" },
                    contentAlignment = Alignment.Center,
                ) { GlyphIcon(Glyph.CAMERA, p.onAccent, 22.dp) }
            }
        }
        when {
            app == null -> Note("シリーズの一覧に無いアプリです")
            pkg == null -> FlowRow2 { PillButton("入れる（Hatch の一覧）", { model.showHatch(id) }) }
            items == null -> Note("${app.name} はまだ Home の棚に対応していません。対応すると、ここにピン留め・お気に入りなどが出ます。")
            items!!.isEmpty() -> Note("出すものがありません（${app.name} でピン留め・お気に入りにすると出ます）")
            else -> items!!.groupBy { it.sectionTitle.ifEmpty { it.section } }.forEach { (title, list) ->
                if (title.isNotEmpty()) SectionTitle(title)
                // タイル：幅に合わせて 2〜4 列。画像・題名・本文がその場で読める（横スクロールはしない）
                androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val cols = (maxWidth / 170.dp).toInt().coerceIn(2, 4)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        list.chunked(cols).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                                row.forEach { item -> Box(Modifier.weight(1f)) { ShelfTile(pkg, item) } }
                                repeat(cols - row.size) { Box(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 画像（ジャケットなど）を横に並べる。押すとそのアプリで開く・再生する。 */
@Composable
fun ShelfStrip(pkg: String, list: List<HatchShelf.Item>, size: androidx.compose.ui.unit.Dp = 84.dp, tall: Boolean = false) {
    val p = LocalPalette.current
    val actions = LocalActions.current
    val ctx = actions.activity
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        list.forEach { item ->
            val bmp by produceState<android.graphics.Bitmap?>(null, item.image) { value = withContext(Dispatchers.IO) { HatchShelf.image(ctx, item) } }
            Column(Modifier.width(size).clickable(onClickLabel = "開く") { HatchShelf.intent(pkg, item)?.let(actions.start) }) {
                // 本の表紙は縦長（2:3）
                val h = if (tall) size * 1.45f else size
                Box(Modifier.width(size).height(h).background(p.raised).border(lineWidth, p.line), contentAlignment = Alignment.Center) {
                    bmp?.let { Image(it.asImageBitmap(), item.title, Modifier.width(size).height(h), contentScale = ContentScale.Crop) }
                        ?: Text(item.title.take(2), color = p.muted, fontSize = 16.sp)
                }
                Text(item.title, color = p.fg, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
                if (item.subtitle.isNotEmpty()) Text(item.subtitle, color = p.faint, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** タイル 1 つ：上に色の帯、画像（あれば）、題名、本文（8 行まで）。押すとそのアプリで開く。 */
@Composable
private fun ShelfTile(pkg: String, item: HatchShelf.Item) {
    val p = LocalPalette.current
    val actions = LocalActions.current
    val ctx = actions.activity
    val band = runCatching { Color(android.graphics.Color.parseColor(item.color)) }.getOrNull() ?: p.line
    val bmp by produceState<android.graphics.Bitmap?>(null, item.image) { value = withContext(Dispatchers.IO) { HatchShelf.image(ctx, item, 480) } }
    Column(
        Modifier.fillMaxWidth().background(p.raised).border(lineWidth, p.line)
            .clickable(onClickLabel = "開く") { HatchShelf.intent(pkg, item)?.let(actions.start) },
    ) {
        Box(Modifier.fillMaxWidth().height(3.dp).background(band))
        bmp?.let { b ->
            Image(b.asImageBitmap(), item.title, Modifier.fillMaxWidth().heightIn(max = 160.dp), contentScale = ContentScale.FillWidth)
        }
        Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (item.title.isNotEmpty()) Text(item.title, color = p.fg, fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val body = item.text.ifEmpty { item.subtitle }
            if (body.isNotEmpty() && body != item.title) Text(body, color = p.muted, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 8, overflow = TextOverflow.Ellipsis)
        }
    }
}
