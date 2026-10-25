package jp.yaegaki.theta.home.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import jp.yaegaki.theta.home.HomeModel
import jp.yaegaki.theta.home.data.AppEntry
import jp.yaegaki.theta.home.data.Apps
import jp.yaegaki.theta.home.data.Files
import jp.yaegaki.theta.home.data.TabSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

// ---- フォルダのタブ：ドロワーの中にフォルダの中身 ----

/**
 * フォルダのタブを開いたときの中身。背景のファイラーと同じ一覧（見せ方もフォルダごとに共通）で、場所はタブごとに覚える。
 * 戻るで上のフォルダへ、いちばん上で戻るとドロワーを閉じる。
 */
@Composable
fun FolderDrawer(model: HomeModel, t: TabSpec) {
    var nasDialog by remember { mutableStateOf(false) }
    var driveDialog by remember { mutableStateOf(false) }
    LaunchedEffect(t.id, t.folderUri) { model.openFolderDrawer(t) }
    val canUp = when {
        t.nasPath != null -> model.nasState(t.id).path.isNotEmpty()
        t.driveFolder != null -> model.driveState(t.id).stack.size > 1
        else -> (model.folders[t.id]?.stack?.size ?: 0) > 1
    }
    BackHandler(enabled = canUp) { model.drawerUp(t) }
    val loc = when {
        t.nasPath != null -> "nas"
        t.driveFolder != null -> "drive"
        else -> "tab:${t.id}"
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp)) {
        FolderView(
            model, preview = false, selected = null, onSelect = {},
            openNasSettings = { nasDialog = true }, openDriveSettings = { driveDialog = true },
            loc = loc, key = t.id, spec = t,
        )
    }
    if (nasDialog) NasDialog(model) { nasDialog = false }
    if (driveDialog) DriveDialog(model) { driveDialog = false }
}

// ---- WEB のタブ：決めたページを中に出す ----

/** WEB のタブ。ページの中のリンクはこのタブの中で開く（http・https 以外はアプリで）。戻るでページを戻る。 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebDrawer(model: HomeModel, t: TabSpec) {
    val p = LocalPalette.current
    val actions = LocalActions.current
    var editing by remember(t.id) { mutableStateOf(t.folderUri == null) }
    val url = t.folderUri
    if (editing || url == null) {
        UrlForm(url.orEmpty(), onSave = { model.setTabUrl(t.id, it); editing = false }, onCancel = if (url != null) ({ editing = false }) else null)
        return
    }
    var web by remember { mutableStateOf<WebView?>(null) }
    var canBack by remember { mutableStateOf(false) }
    var current by remember(url) { mutableStateOf(url) }
    var loading by remember { mutableStateOf(true) }
    BackHandler(enabled = canBack) { web?.goBack() }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            if (canBack) GlyphButton(Glyph.BACK, "ページを戻る", { web?.goBack() })
            Text(
                current.removePrefix("https://").removePrefix("http://"), color = if (loading) p.accent else p.muted, fontSize = 11.sp, fontFamily = Hud.mono,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
            )
            TextAction("↻", "読み直す") { web?.reload() }
            TextAction("⌂", "決めたページへ") { web?.loadUrl(url) }
            TextAction("↗", "ブラウザで開く") { actions.start(Intent(Intent.ACTION_VIEW, Uri.parse(current))) }
            TextAction("✎", "URL を変える") { editing = true }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val u = request.url
                            if (u.scheme == "http" || u.scheme == "https") return false
                            actions.start(Intent(Intent.ACTION_VIEW, u))
                            return true
                        }

                        override fun onPageStarted(view: WebView, u: String?, favicon: Bitmap?) {
                            loading = true
                            u?.let { current = it }
                        }

                        override fun onPageFinished(view: WebView, u: String?) {
                            loading = false
                            canBack = view.canGoBack()
                            u?.let { current = it }
                        }
                    }
                    loadUrl(url)
                    web = this
                }
            },
            onRelease = { it.stopLoading(); it.destroy() },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
    }
}

@Composable
private fun UrlForm(initial: String, onSave: (String) -> Unit, onCancel: (() -> Unit)?) {
    var text by remember { mutableStateOf(initial) }
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Note("このタブの中に出すページの URL。よく見るサイト・社内のページ・NAS の管理画面など。")
        Field(text, { text = it }, "https://…")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("このページにする", { onSave(text) }, primary = true, enabled = text.isNotBlank())
            if (onCancel != null) PillButton("やめる", onCancel)
        }
    }
}

@Composable
private fun TextAction(glyph: String, label: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(Modifier.size(40.dp).combinedClickable(onClickLabel = label, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(glyph, color = p.fg, fontSize = 17.sp)
    }
}

// ---- FILE のタブ：決めた画像・PDF を中に出す ----

/** FILE のタブ。画像は指で拡大（2 回押すと元に戻る）、PDF はページを縦に並べる。 */
@Composable
fun FileDrawer(model: HomeModel, t: TabSpec) {
    val p = LocalPalette.current
    val actions = LocalActions.current
    val ctx = actions.activity
    val pick = { actions.openDocument(arrayOf("image/*", "application/pdf")) { model.setTabFile(t.id, it) } }
    val uri = t.folderUri?.let(Uri::parse)
    if (uri == null) {
        NeedPermission("画像や PDF を 1 つ選ぶと、このタブを開くだけで見られます（地図・時刻表・資料など）。", "ファイルを選ぶ", pick)
        return
    }
    val info = remember(uri) {
        runCatching {
            val mime = ctx.contentResolver.getType(uri)
            val name = ctx.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
            mime to (name ?: uri.lastPathSegment.orEmpty())
        }.getOrNull()
    }
    if (info == null) {
        NeedPermission("ファイルを読めませんでした（消された・移された・許可がなくなったなど）。", "ファイルを選び直す", pick)
        return
    }
    val (mime, name) = info
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(name, color = p.muted, fontSize = 12.sp, fontFamily = Hud.mono, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            TextAction("↗", "アプリで開く") { actions.start(Files.openIntent(uri, mime)) }
            TextAction("✎", "ファイルを変える") { pick() }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (mime == "application/pdf") PdfPages(uri) else ZoomImage(uri)
        }
    }
}

@Composable
private fun ZoomImage(uri: Uri) {
    val ctx = LocalActions.current.activity
    val p = LocalPalette.current
    val bmp by produceState<Bitmap?>(null, uri) { value = withContext(Dispatchers.IO) { decodeScaled(ctx, uri, 2048) } }
    var scale by remember(uri) { mutableStateOf(1f) }
    var offset by remember(uri) { mutableStateOf(Offset.Zero) }
    val b = bmp
    if (b == null) {
        Text("読み込み中…", color = p.muted, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
        return
    }
    Image(
        b.asImageBitmap(), null, contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxSize()
            .pointerInput(uri) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 8f)
                    offset = if (scale == 1f) Offset.Zero else offset + pan
                }
            }
            .pointerInput(uri) { detectTapGestures(onDoubleTap = { scale = 1f; offset = Offset.Zero }) }
            .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y },
    )
}

private fun decodeScaled(ctx: android.content.Context, uri: Uri, max: Int): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (bounds.outWidth / sample > max || bounds.outHeight / sample > max) sample *= 2
    ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
}.getOrNull()

/** PDF のページを、ドロワーの幅で縦に並べる。PdfRenderer は同時に 1 ページしか描けないので順番に。 */
@Composable
internal fun PdfPages(uri: Uri) {
    val ctx = LocalActions.current.activity
    val p = LocalPalette.current
    val lock = remember { Mutex() }
    val renderer by produceState<PdfRenderer?>(null, uri) {
        val r = withContext(Dispatchers.IO) { runCatching { ctx.contentResolver.openFileDescriptor(uri, "r")?.let(::PdfRenderer) }.getOrNull() }
        value = r
        awaitDispose { r?.let { runCatching { it.close() } } }
    }
    val r = renderer
    if (r == null) {
        Text("PDF を開いています…", color = p.muted, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
        return
    }
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        val widthPx = with(LocalDensity.current) { maxWidth.roundToPx() }.coerceIn(200, 2000)
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items((0 until r.pageCount).toList()) { i ->
                val page by produceState<Bitmap?>(null, r, i, widthPx) {
                    value = withContext(Dispatchers.IO) {
                        lock.withLock {
                            runCatching {
                                r.openPage(i).use { pg ->
                                    val h = (widthPx.toFloat() * pg.height / pg.width).toInt().coerceAtLeast(1)
                                    Bitmap.createBitmap(widthPx, h, Bitmap.Config.ARGB_8888).also { b ->
                                        b.eraseColor(android.graphics.Color.WHITE)
                                        pg.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    }
                                }
                            }.getOrNull()
                        }
                    }
                }
                val b = page
                if (b != null) Image(b.asImageBitmap(), "ページ ${i + 1}", Modifier.fillMaxWidth().aspectRatio(b.width.toFloat() / b.height))
                else Box(Modifier.fillMaxWidth().aspectRatio(0.707f).background(p.raised))
            }
        }
    }
}

// ---- タブに置いたアプリ・ショートカット（カテゴリー以外のタブ） ----

/**
 * カテゴリー以外のタブの上に並べる、置いたアプリとショートカット。押すと開く、長押し→ドラッグで並べ替え、離すとメニュー。
 * 何も置いていなければ出さない（タブの長押し →「アプリ・ショートカットを置く」）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TabAppsStrip(model: HomeModel, tabId: String) {
    val p = LocalPalette.current
    val actions = LocalActions.current
    val keys = model.opts(tabId).apps
    val apps = keys.mapNotNull { k -> model.apps.firstOrNull { it.key == k } }
    val place = model.placeOf(tabId)
    val scs = model.shortcuts.filter { it.catId == place }
    if (apps.isEmpty() && scs.isEmpty()) return
    val reorder = rememberReorderState()
    var menu by remember { mutableStateOf<String?>(null) }
    Column {
        FlowRow(
            Modifier.fillMaxWidth().zIndex(if (reorder.dragging != null) 10f else 0f).padding(horizontal = 6.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            apps.forEach { app ->
                Box(Modifier.zIndex(if (reorder.dragging == app.key) 10f else 0f)) {
                    StripCell(app.label, Modifier.reorderable(reorder, app.key, "strip:$tabId", p.accent, onMenu = { menu = app.key }) { from, to -> model.moveTabAppTo(tabId, from, to) },
                        onClick = { actions.start(Apps.launchIntent(app)) }) { AppIcon(app, 36.dp) }
                    DropdownMenu(expanded = menu == app.key, onDismissRequest = { menu = null }) {
                        DropdownMenuItem(text = { Text("このタブから外す") }, onClick = { menu = null; model.removeTabApp(tabId, app.key) })
                        DropdownMenuItem(text = { Text("アプリの情報") }, onClick = { menu = null; actions.start(Apps.infoIntent(app.packageName)) })
                    }
                }
            }
            scs.forEach { sc ->
                val key = "sc:" + sc.id
                Box(Modifier.zIndex(if (reorder.dragging == key) 10f else 0f)) {
                    StripCell(sc.label, Modifier.reorderable(reorder, key, "stripsc:$tabId", p.accent, onMenu = { menu = key }) { from, to -> model.moveShortcutTo(from.removePrefix("sc:"), to.removePrefix("sc:")) },
                        onClick = { model.launchShortcut(sc) }) {
                        Box {
                            ShortcutIcon(sc, 36.dp)
                            Box(Modifier.align(Alignment.BottomEnd).size(8.dp).background(p.accent))
                        }
                    }
                    DropdownMenu(expanded = menu == key, onDismissRequest = { menu = null }) {
                        DropdownMenuItem(text = { Text("このタブから外す") }, onClick = { menu = null; model.removeShortcut(sc.id) })
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StripCell(label: String, modifier: Modifier, onClick: () -> Unit, icon: @Composable () -> Unit) {
    val p = LocalPalette.current
    Column(
        modifier.width(60.dp).combinedClickable(onClick = onClick, onLongClick = {}, onClickLabel = "開く").padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        icon()
        Text(label, color = p.fg, fontSize = 10.sp, lineHeight = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 2.dp))
    }
}

/** タブに置くアプリを選ぶ（チェックで出し入れ）。ショートカットはアプリを選んでから。 */
@Composable
fun TabAppsDialog(model: HomeModel, tabId: String, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    var chosen by remember { mutableStateOf(model.opts(tabId).apps) }
    var q by remember { mutableStateOf("") }
    var shortcutsFor by remember { mutableStateOf<AppEntry?>(null) }
    val list = model.apps.filter { q.isBlank() || it.label.contains(q, ignoreCase = true) }
        .sortedBy { it.key !in chosen }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("アプリ・ショートカットを置く") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Note("チェックしたアプリを、このタブの上に並べます。「SC」でそのアプリのショートカット（LINE のトークルームなど）を選べます。")
                Field(q, { q = it }, "名前で絞り込み")
                Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                    list.forEachIndexed { zi, app ->
                        val on = app.key in chosen
                        RowButton(zebra = zi, onClick = { chosen = if (on) chosen - app.key else chosen + app.key }) {
                            Checkbox(checked = on, onCheckedChange = null)
                            AppIcon(app, 28.dp)
                            Text(app.label, color = p.fg, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text("SC", color = p.accent, fontSize = 11.sp, fontFamily = Hud.mono,
                                modifier = Modifier.combinedClickable(onClickLabel = "ショートカットを選ぶ") { shortcutsFor = app }.padding(8.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { model.setTabApps(tabId, chosen); onDismiss() }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("やめる") } },
    )
    shortcutsFor?.let { app -> AppShortcutsDialog(model, app, model.placeOf(tabId)) { shortcutsFor = null } }
}
