package jp.yaegaki.theta.home.ui

import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import jp.yaegaki.theta.home.HomeModel
import jp.yaegaki.theta.home.data.Files
import jp.yaegaki.theta.home.data.ViewItem
import jp.yaegaki.theta.home.data.Viewable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ファイルを開く。中で開ける形式（画像・動画・音声・PDF・文字・CSV・zip）はホームの上のポップアップで、
 * ほかは今までどおりほかのアプリで。siblings を渡すと、ポップアップの中で左右にめくって同じフォルダのものを見られる。
 */
fun openFile(model: HomeModel, actions: Actions, item: ViewItem, siblings: List<ViewItem> = listOf(item)) {
    // 本（EPUB・CBR・M4B）は DSBooks があればそちらで（取り込んで開く）
    if (Viewable.isBook(item.name) && model.dsbooksInstalled() && model.openInDsbooks(item.uri, item.name, actions.start)) return
    if (!model.inAppViewer || Viewable.kind(item.name, item.mime) == null) {
        actions.start(Files.openIntent(item.uri, item.mime))
        return
    }
    val list = siblings.filter { Viewable.kind(it.name, it.mime) != null }.ifEmpty { listOf(item) }
    model.openViewer(list, list.indexOfFirst { it.uri == item.uri }.coerceAtLeast(0))
}

/** 中で開くポップアップ。上に名前・何枚目・共有・ほかのアプリで開く・閉じる、左右にめくると次のファイル。 */
@Composable
fun ViewerOverlay(model: HomeModel) {
    val v = model.viewer ?: return
    BackHandler { model.closeViewer() }
    // 開き直したら初めのページから
    androidx.compose.runtime.key(v) { ViewerBody(model, v.first, v.second) }
}

@Composable
private fun ViewerBody(model: HomeModel, items: List<ViewItem>, start: Int) {
    val actions = LocalActions.current
    val p = LocalPalette.current
    val pager = rememberPagerState(initialPage = start) { items.size }
    val current = items.getOrNull(pager.currentPage) ?: return
    Column(Modifier.fillMaxSize().background(Color.Black).clickable(enabled = false) {}) {
        Row(Modifier.fillMaxWidth().background(p.panel).padding(start = 14.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(current.name, color = p.fg, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (items.size > 1) Text("${pager.currentPage + 1}/${items.size}", color = p.faint, fontSize = 11.sp, fontFamily = Hud.mono, modifier = Modifier.padding(horizontal = 6.dp))
            if (Viewable.kind(current.name, current.mime) in setOf("pdf", "cbz") && model.dsbooksInstalled()) ViewerAction("DSBooks") {
                if (!model.openInDsbooks(current.uri, current.name, actions.start)) actions.toast("DSBooks を 0.8.7 以降にすると、ここから開けます")
            }
            ViewerAction("共有") { actions.start(Files.shareIntent(current.uri, current.mime, current.name)) }
            ViewerAction("アプリで開く") { actions.start(Files.openIntent(current.uri, current.mime)) }
            GlyphButton(Glyph.CLOSE, "閉じる", { model.closeViewer() })
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), key = { items[it].uri.toString() }) { i ->
            ViewerPage(model, items[i], active = pager.currentPage == i)
        }
    }
}

@Composable
private fun ViewerAction(label: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Text(label, color = p.accent, fontSize = 12.sp, modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 14.dp))
}

@Composable
private fun ViewerPage(model: HomeModel, item: ViewItem, active: Boolean) {
    when (Viewable.kind(item.name, item.mime)) {
        "image" -> ImagePage(item)
        "video", "audio" -> if (active) MediaPage(item, audio = Viewable.kind(item.name, item.mime) == "audio") else Box(Modifier.fillMaxSize())
        "pdf" -> Box(Modifier.fillMaxSize().background(LocalPalette.current.bg)) {
            if (model.pdfPaged) PdfPaged(model, item.uri) else PdfPages(item.uri)
            PageModeBar(model, paged = model.pdfPaged, canScroll = true, modifier = Modifier.align(Alignment.TopEnd))
        }
        "cbz" -> Box(Modifier.fillMaxSize()) {
            ComicPage(model, item)
            PageModeBar(model, paged = true, canScroll = false, modifier = Modifier.align(Alignment.TopEnd))
        }
        "text" -> TextPage(item)
        "csv" -> CsvPage(item)
        "zip" -> ZipPage(item)
        "docx", "xlsx", "pptx" -> OfficePage(item)
        else -> Message("この形式は中で開けません。上の「アプリで開く」から。")
    }
}

@Composable
private fun Message(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, color = Color(0xFFBBBBBB), fontSize = 14.sp)
    }
}

/** 画像：動く GIF・WebP もそのまま。2 本指で拡大、拡大中は 1 本指で動かす。2 回押すと拡大・元に戻す。 */
@Composable
private fun ImagePage(item: ViewItem) {
    val ctx = LocalActions.current.activity
    val drawable by produceState<Result<Drawable>?>(null, item.uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                ImageDecoder.decodeDrawable(ImageDecoder.createSource(ctx.contentResolver, item.uri)) { decoder, info, _ ->
                    // とても大きい写真は、画面で見るのに十分な大きさまで縮めて読む
                    val longest = maxOf(info.size.width, info.size.height)
                    if (longest > 4096) decoder.setTargetSampleSize((longest + 4095) / 4096)
                }
            }.recoverCatching {
                // ImageDecoder が読めない形式・環境では、止まった絵として読む
                val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                ctx.contentResolver.openInputStream(item.uri)!!.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 4096) sample *= 2
                val bmp = ctx.contentResolver.openInputStream(item.uri)!!.use {
                    android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
                } ?: throw java.io.IOException("読めない画像")
                android.graphics.drawable.BitmapDrawable(ctx.resources, bmp)
            }
        }
    }
    val r = drawable
    when {
        r == null -> Message("読み込み中…")
        r.isFailure -> Message("この画像は中で開けませんでした。上の「アプリで開く」から。")
        else -> {
            var scale by remember(item.uri) { mutableFloatStateOf(1f) }
            var offset by remember(item.uri) { mutableStateOf(Offset.Zero) }
            val state = rememberTransformableState { zoom, pan, _ ->
                scale = (scale * zoom).coerceIn(1f, 8f)
                offset = if (scale == 1f) Offset.Zero else offset + pan * scale
            }
            AndroidView(
                factory = { c -> ImageView(c).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
                update = { iv ->
                    val d = r.getOrNull()
                    if (iv.drawable !== d) {
                        iv.setImageDrawable(d)
                        (d as? AnimatedImageDrawable)?.start()
                    }
                },
                modifier = Modifier.fillMaxSize()
                    // 拡大していないときは、左右になぞると次のファイルへ（ページめくりに任せる）
                    .transformable(state, canPan = { scale > 1f })
                    .pointerInput(item.uri) {
                        detectTapGestures(onDoubleTap = {
                            if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f
                        })
                    }
                    .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y },
            )
        }
    }
}

/** 動画・音声：ExoPlayer で再生。めくって離れたら止めて片付ける。 */
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
@Composable
private fun MediaPage(item: ViewItem, audio: Boolean) {
    val ctx = LocalActions.current.activity
    val p = LocalPalette.current
    val player = remember(item.uri) {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(MediaItem.fromUri(item.uri))
            prepare()
            playWhenReady = true
        }
    }
    androidx.compose.runtime.DisposableEffect(player) { onDispose { player.release() } }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (audio) GlyphIcon(Glyph.MUSIC, p.accent, 120.dp)
        AndroidView(
            factory = { c ->
                PlayerView(c).apply {
                    this.player = player
                    // 音声は操作盤を出したままに
                    if (audio) { controllerShowTimeoutMs = 0; controllerHideOnTouch = false; showController() }
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** 読むのは先頭の 1MB まで。 */
private const val TEXT_LIMIT = 1 shl 20

private fun readHead(ctx: android.content.Context, uri: android.net.Uri): Pair<ByteArray, Boolean> {
    ctx.contentResolver.openInputStream(uri)!!.use { s ->
        val buf = ByteArray(TEXT_LIMIT + 1)
        var n = 0
        while (n < buf.size) {
            val r = s.read(buf, n, buf.size - n)
            if (r < 0) break
            n += r
        }
        return buf.copyOf(minOf(n, TEXT_LIMIT)) to (n > TEXT_LIMIT)
    }
}

/** 文字：選んでコピーできる。JSON は整えて出す。 */
@Composable
private fun TextPage(item: ViewItem) {
    val ctx = LocalActions.current.activity
    val p = LocalPalette.current
    val text by produceState<Result<Pair<String, Boolean>>?>(null, item.uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val (bytes, cut) = readHead(ctx, item.uri)
                var t = Viewable.decode(bytes, cut)
                if (!cut && item.name.endsWith(".json", ignoreCase = true)) {
                    t = runCatching { if (t.trimStart().startsWith("[")) org.json.JSONArray(t).toString(2) else org.json.JSONObject(t).toString(2) }.getOrDefault(t)
                }
                t to cut
            }
        }
    }
    val r = text
    when {
        r == null -> Message("読み込み中…")
        r.isFailure -> Message("読めませんでした。上の「アプリで開く」から。")
        else -> {
            val (t, cut) = r.getOrThrow()
            Column(Modifier.fillMaxSize().background(p.bg).verticalScroll(rememberScrollState()).padding(14.dp)) {
                if (cut) Text("先頭の 1MB だけ出しています", color = p.faint, fontSize = 11.sp, modifier = Modifier.padding(bottom = 8.dp))
                SelectionContainer { Text(t, color = p.fg, fontSize = 13.sp, lineHeight = 19.sp, fontFamily = FontFamily.Monospace) }
            }
        }
    }
}

/** CSV・TSV：表で。1 行目は見出しとして強く。 */
@Composable
private fun CsvPage(item: ViewItem) {
    val ctx = LocalActions.current.activity
    val p = LocalPalette.current
    val table by produceState<Result<List<List<String>>>?>(null, item.uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val (bytes, cut) = readHead(ctx, item.uri)
                Viewable.parseCsv(Viewable.decode(bytes, cut), tab = item.name.endsWith(".tsv", ignoreCase = true))
            }
        }
    }
    val r = table
    when {
        r == null -> Message("読み込み中…")
        r.isFailure -> Message("読めませんでした。上の「アプリで開く」から。")
        else -> TableView(r.getOrThrow())
    }
}

/** 表（CSV・Excel のシート）。1 行目は見出しとして地を変え、左に行番号。 */
@Composable
private fun TableView(rows: List<List<String>>) {
    val p = LocalPalette.current
    val cols = rows.maxOfOrNull { it.size } ?: 0
    if (rows.isEmpty()) {
        Message("空です")
        return
    }
    // 列の幅は中身の長さから（長すぎる列は 240dp で切る）
    val widths = (0 until cols).map { c -> (rows.take(200).maxOfOrNull { it.getOrNull(c)?.length ?: 0 } ?: 0).coerceIn(3, 30) * 8 + 16 }
    Box(Modifier.fillMaxSize().background(p.bg).horizontalScroll(rememberScrollState())) {
        LazyColumn(Modifier.width(widths.sum().dp + 48.dp)) {
            itemsIndexed(rows) { ri, row ->
                Row(Modifier.background(if (ri == 0) p.raised else if (ri % 2 == 0) p.zebra else Color.Transparent)) {
                    Text("%d".format(ri + 1), color = p.faint, fontSize = 10.sp, fontFamily = Hud.mono, modifier = Modifier.width(48.dp).padding(6.dp))
                    widths.forEachIndexed { c, w ->
                        Text(row.getOrNull(c).orEmpty(), color = p.fg, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width(w.dp).border(0.5.dp, p.line).padding(horizontal = 6.dp, vertical = 5.dp))
                    }
                }
            }
        }
    }
}

/** zip：中のファイルの一覧（名前・大きさ）。Windows で作った日本語の名前も読めるように。 */
@Composable
private fun ZipPage(item: ViewItem) {
    val ctx = LocalActions.current.activity
    val p = LocalPalette.current
    val list by produceState<Result<List<Pair<String, Long>>>?>(null, item.uri) {
        value = withContext(Dispatchers.IO) {
            fun read(cs: java.nio.charset.Charset) = ctx.contentResolver.openInputStream(item.uri)!!.use { s ->
                java.util.zip.ZipInputStream(s, cs).use { z ->
                    generateSequence { z.nextEntry }.take(3000).map { it.name to it.size }.toList()
                }
            }
            runCatching { read(Charsets.UTF_8) }.recoverCatching { read(charset("MS932")) }
        }
    }
    val r = list
    when {
        r == null -> Message("読み込み中…")
        r.isFailure -> Message("中身を読めませんでした。上の「アプリで開く」から。")
        else -> {
            val entries = r.getOrThrow()
            LazyColumn(Modifier.fillMaxSize().background(p.bg), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
                item { Text("${entries.size} 件", color = p.faint, fontSize = 11.sp, fontFamily = Hud.mono, modifier = Modifier.padding(bottom = 6.dp)) }
                itemsIndexed(entries) { i, (name, size) ->
                    Row(Modifier.fillMaxWidth().background(if (i % 2 == 1) p.zebra else Color.Transparent).padding(horizontal = 6.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlyphIcon(if (name.endsWith("/")) Glyph.FOLDER else Glyph.FILE, p.muted, 16.dp)
                        Text(name, color = p.fg, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        if (size >= 0 && !name.endsWith("/")) Text(Files.sizeText(size), color = p.faint, fontSize = 11.sp, fontFamily = Hud.mono)
                    }
                }
            }
        }
    }
}


// ---- Word・Excel・PowerPoint（かんたんなプレビュー） ----

private sealed class OfficeDoc {
    class Sheets(val list: List<jp.yaegaki.theta.home.data.Office.Sheet>) : OfficeDoc()
    class Words(val file: java.io.File, val blocks: List<jp.yaegaki.theta.home.data.Office.Block>) : OfficeDoc()
    class Slides(val file: java.io.File, val list: List<jp.yaegaki.theta.home.data.Office.Slide>) : OfficeDoc()
}

/** docx・xlsx・pptx：文字・表・画像だけを取り出して出す。見た目どおりにはしない（正確には「アプリで開く」）。 */
@Composable
private fun OfficePage(item: ViewItem) {
    val ctx = LocalActions.current.activity
    val p = LocalPalette.current
    val kind = Viewable.kind(item.name, item.mime)
    val doc by produceState<Result<OfficeDoc>?>(null, item.uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val f = jp.yaegaki.theta.home.data.Office.copyToCache(ctx, item.uri)
                java.util.zip.ZipFile(f).use { z ->
                    when (kind) {
                        "xlsx" -> OfficeDoc.Sheets(jp.yaegaki.theta.home.data.Office.xlsx(z))
                        "docx" -> OfficeDoc.Words(f, jp.yaegaki.theta.home.data.Office.docx(z))
                        else -> OfficeDoc.Slides(f, jp.yaegaki.theta.home.data.Office.pptx(z))
                    }
                }
            }
        }
    }
    val r = doc
    when {
        r == null -> Message("読み込み中…")
        r.isFailure -> Message("中身を読めませんでした（古い形式・パスワード付きなど）。上の「アプリで開く」から。")
        else -> Column(Modifier.fillMaxSize().background(p.bg)) {
            Text("かんたんなプレビュー ・ 見た目どおりは「アプリで開く」", color = p.faint, fontSize = 10.sp, maxLines = 1,
                modifier = Modifier.fillMaxWidth().background(p.panel).padding(horizontal = 12.dp, vertical = 4.dp))
            when (val d = r.getOrThrow()) {
                is OfficeDoc.Sheets -> SheetsView(d.list)
                is OfficeDoc.Words -> WordsView(d.file, d.blocks)
                is OfficeDoc.Slides -> SlidesView(d.file, d.list)
            }
        }
    }
}

@Composable
private fun SheetsView(sheets: List<jp.yaegaki.theta.home.data.Office.Sheet>) {
    val p = LocalPalette.current
    var index by remember { mutableStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        if (sheets.size > 1) Row(Modifier.horizontalScroll(rememberScrollState()).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            sheets.forEachIndexed { i, s ->
                val on = i == index
                Text(s.name, color = if (on) p.onAccent else p.fg, fontSize = 12.sp,
                    modifier = Modifier.background(if (on) p.accent else Color.Transparent).border(1.dp, if (on) p.accent else p.line)
                        .clickable { index = i }.padding(horizontal = 10.dp, vertical = 5.dp))
            }
        }
        Box(Modifier.weight(1f)) { TableView(sheets.getOrNull(index)?.rows.orEmpty()) }
    }
}

/** Word：紙のような白地に、見出し・本文・箇条書き・表・画像。 */
@Composable
private fun WordsView(file: java.io.File, blocks: List<jp.yaegaki.theta.home.data.Office.Block>) {
    val ink = Color(0xFF1C1C1C)
    LazyColumn(
        Modifier.fillMaxSize().background(Color(0xFFE9E8E4)),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
    ) {
        itemsIndexed(blocks) { _, b ->
            Box(Modifier.fillMaxWidth().background(Color(0xFFFCFBF8)).padding(horizontal = 16.dp, vertical = 3.dp)) {
                when (b) {
                    is jp.yaegaki.theta.home.data.Office.Block.Para -> SelectionContainer {
                        Text(
                            (if (b.list) "・" else "") + b.text,
                            color = ink,
                            fontSize = when (b.level) { 1 -> 22.sp; 2 -> 18.sp; 3 -> 16.sp; else -> 14.sp },
                            fontWeight = if (b.level > 0) androidx.compose.ui.text.font.FontWeight.Bold else null,
                            lineHeight = if (b.level > 0) 28.sp else 22.sp,
                            modifier = Modifier.padding(top = if (b.level > 0) 10.dp else 0.dp, start = if (b.list) 8.dp else 0.dp),
                        )
                    }
                    is jp.yaegaki.theta.home.data.Office.Block.Table -> Column(Modifier.padding(vertical = 6.dp).border(1.dp, Color(0xFF9A9A9A))) {
                        b.rows.forEach { row ->
                            Row {
                                row.forEach { c ->
                                    Text(c, color = ink, fontSize = 12.sp, modifier = Modifier.weight(1f).border(0.5.dp, Color(0xFFBDBDBD)).padding(6.dp))
                                }
                            }
                        }
                    }
                    is jp.yaegaki.theta.home.data.Office.Block.Image -> ZipImage(file, b.path, Modifier.fillMaxWidth().padding(vertical = 6.dp))
                }
            }
        }
    }
}

/** PowerPoint：スライドごとのカード。1 つ目の文字を題に、残りを箇条書き、画像は下に。 */
@Composable
private fun SlidesView(file: java.io.File, slides: List<jp.yaegaki.theta.home.data.Office.Slide>) {
    val p = LocalPalette.current
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(slides) { i, s ->
            Column(Modifier.fillMaxWidth().background(Color(0xFFFCFBF8)).border(1.dp, p.line).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("SLIDE %02d".format(i + 1), color = Color(0xFF8A8A8A), fontSize = 10.sp, fontFamily = Hud.mono)
                s.texts.firstOrNull()?.let { Text(it, color = Color(0xFF1C1C1C), fontSize = 18.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) }
                s.texts.drop(1).forEach { Text("・$it", color = Color(0xFF2A2A2A), fontSize = 13.sp, lineHeight = 19.sp) }
                s.images.take(4).forEach { ZipImage(file, it, Modifier.fillMaxWidth().height(200.dp)) }
                if (s.texts.isEmpty() && s.images.isEmpty()) Text("（文字・画像なし）", color = Color(0xFF8A8A8A), fontSize = 12.sp)
            }
        }
    }
}

/** zip の中の画像。幅 1200px 程度に縮めて読む（EMF・WMF など読めないものは出さない）。 */
@Composable
private fun ZipImage(file: java.io.File, path: String, modifier: Modifier) {
    val bmp by produceState<android.graphics.Bitmap?>(null, file, path) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                java.util.zip.ZipFile(file).use { z ->
                    val e = z.getEntry(path) ?: return@use null
                    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    z.getInputStream(e).use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
                    var sample = 1
                    while (bounds.outWidth / sample > 1200) sample *= 2
                    z.getInputStream(e).use { android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }) }
                }
            }.getOrNull()
        }
    }
    bmp?.let { androidx.compose.foundation.Image(it.asImageBitmap(), null, modifier, contentScale = androidx.compose.ui.layout.ContentScale.Fit) }
}

// ---- 1 ページずつめくる（PDF・マンガ） ----

/** ページのめくり方：「1 枚ずつ／続けて」（PDF だけ）と「右開き／左開き」。小さく右上に。 */
@Composable
private fun PageModeBar(model: HomeModel, paged: Boolean, canScroll: Boolean, modifier: Modifier) {
    val p = LocalPalette.current
    @Composable
    fun chip(label: String, onClick: () -> Unit) {
        Text(label, color = p.fg, fontSize = 11.sp, fontFamily = Hud.mono, maxLines = 1,
            modifier = Modifier.background(p.panel.copy(alpha = 0.85f)).border(1.dp, p.line).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 5.dp))
    }
    Row(modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (canScroll) chip(if (paged) "1枚ずつ" else "続けて") { model.changePdfPaged(!paged) }
        if (paged) chip(if (model.pagesRtl) "右開き" else "左開き") { model.changePagesRtl(!model.pagesRtl) }
    }
}

/**
 * 1 ページずつめくる。右開きなら右から左へ（マンガ・縦書き）。2 回押すと拡大、拡大中は 1 本指で動かす。
 * load はページを画面に収まる大きさで読む（w・h は画面の大きさ）。下にページ数。
 */
@Composable
private fun PagedView(count: Int, rtl: Boolean, key: Any, load: suspend (Int, Int, Int) -> android.graphics.Bitmap?) {
    val p = LocalPalette.current
    val pager = rememberPagerState { count }
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val w = with(density) { maxWidth.roundToPx() }.coerceIn(200, 2400)
        val h = with(density) { maxHeight.roundToPx() }.coerceIn(200, 3200)
        HorizontalPager(pager, Modifier.fillMaxSize(), reverseLayout = rtl, beyondViewportPageCount = 1, key = { "$key/$it" }) { i ->
            val bmp by produceState<android.graphics.Bitmap?>(null, key, i, w, h) { value = load(i, w, h) }
            var scale by remember(i) { mutableFloatStateOf(1f) }
            var offset by remember(i) { mutableStateOf(Offset.Zero) }
            val state = rememberTransformableState { zoom, pan, _ ->
                scale = (scale * zoom).coerceIn(1f, 6f)
                offset = if (scale == 1f) Offset.Zero else offset + pan * scale
            }
            Box(
                Modifier.fillMaxSize()
                    .transformable(state, canPan = { scale > 1f })
                    .pointerInput(i) { detectTapGestures(onDoubleTap = { if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f }) },
                contentAlignment = Alignment.Center,
            ) {
                val b = bmp
                if (b == null) Text("${i + 1}", color = Color(0xFF777777), fontSize = 14.sp, fontFamily = Hud.mono)
                else androidx.compose.foundation.Image(
                    b.asImageBitmap(), "ページ ${i + 1}",
                    Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y },
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                )
            }
        }
        Text("${pager.currentPage + 1} / $count", color = p.fg, fontSize = 11.sp, fontFamily = Hud.mono,
            modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp).background(p.panel.copy(alpha = 0.8f)).padding(horizontal = 8.dp, vertical = 3.dp))
    }
}

/** PDF を 1 ページずつ。PdfRenderer は同時に 1 ページしか描けないので順番に。 */
@Composable
private fun PdfPaged(model: HomeModel, uri: android.net.Uri) {
    val ctx = LocalActions.current.activity
    val lock = remember { kotlinx.coroutines.sync.Mutex() }
    val renderer by produceState<android.graphics.pdf.PdfRenderer?>(null, uri) {
        val r = withContext(Dispatchers.IO) { runCatching { ctx.contentResolver.openFileDescriptor(uri, "r")?.let { fd -> android.graphics.pdf.PdfRenderer(fd) } }.getOrNull() }
        value = r
        awaitDispose { r?.let { runCatching { it.close() } } }
    }
    val r = renderer
    if (r == null) Message("PDF を開いています…")
    else PagedView(r.pageCount, model.pagesRtl, uri) { i, w, h ->
        withContext(Dispatchers.IO) {
            lock.lock()
            try {
                runCatching {
                    r.openPage(i).use { pg ->
                        // 画面に収まる大きさの 1.5 倍で描く（拡大しても読めるように）
                        val fit = minOf(w.toFloat() / pg.width, h.toFloat() / pg.height) * 1.5f
                        val bw = (pg.width * fit).toInt().coerceIn(1, 3000)
                        val bh = (pg.height * fit).toInt().coerceIn(1, 4000)
                        android.graphics.Bitmap.createBitmap(bw, bh, android.graphics.Bitmap.Config.ARGB_8888).also { b ->
                            b.eraseColor(android.graphics.Color.WHITE)
                            pg.render(b, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        }
                    }
                }.getOrNull()
            } finally { lock.unlock() }
        }
    }
}

/** マンガ（CBZ）：zip の中の画像を名前順（数字は数として）に 1 ページずつ。 */
@Composable
private fun ComicPage(model: HomeModel, item: ViewItem) {
    val ctx = LocalActions.current.activity
    val book by produceState<Result<Pair<java.io.File, List<String>>>?>(null, item.uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val f = jp.yaegaki.theta.home.data.Office.copyToCache(ctx, item.uri)
                val names = java.util.zip.ZipFile(f).use { z -> z.entries().asSequence().filter { !it.isDirectory }.map { it.name }.toList() }
                f to Viewable.comicPages(names)
            }
        }
    }
    val r = book
    when {
        r == null -> Message("読み込み中…")
        r.isFailure -> Message("読めませんでした。上の「アプリで開く」から。")
        r.getOrThrow().second.isEmpty() -> Message("画像が入っていません")
        else -> {
            val (file, pages) = r.getOrThrow()
            PagedView(pages.size, model.pagesRtl, item.uri) { i, w, h ->
                withContext(Dispatchers.IO) {
                    runCatching {
                        java.util.zip.ZipFile(file).use { z ->
                            val e = z.getEntry(pages[i]) ?: return@use null
                            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            z.getInputStream(e).use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
                            var sample = 1
                            while (bounds.outWidth / (sample * 2) >= w && bounds.outHeight / (sample * 2) >= h) sample *= 2
                            z.getInputStream(e).use { android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }) }
                        }
                    }.getOrNull()
                }
            }
        }
    }
}
