package jp.yaegaki.theta.home.edge

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import jp.yaegaki.theta.home.data.AppEntry
import jp.yaegaki.theta.home.data.PinnedShortcut
import jp.yaegaki.theta.home.ui.AppIcon
import jp.yaegaki.theta.home.ui.CountBadge
import jp.yaegaki.theta.home.ui.Hud
import jp.yaegaki.theta.home.ui.LocalPalette
import jp.yaegaki.theta.home.ui.ShortcutIcon
import jp.yaegaki.theta.home.ui.Ticks
import jp.yaegaki.theta.home.ui.VerticalLabel
import jp.yaegaki.theta.home.ui.hudShape

/** エッジのタブ 1 つ。catId が "" のときはすべてのアプリ。 */
data class EdgeTab(val catId: String, val label: String)

/**
 * どのアプリの上でも、画面の端から引き出すタブとアプリ。ホームの右端のタブと同じ見た目で、
 * 端の側にタブの列、その内側にアプリのグリッド。外側（暗いところ）を押すと閉じる。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EdgePanel(
    tabs: List<EdgeTab>,
    selected: String,
    apps: List<AppEntry>,
    shortcuts: List<PinnedShortcut>,
    left: Boolean,
    counts: Map<String, Int>,
    onSelect: (String) -> Unit,
    onApp: (AppEntry) -> Unit,
    onShortcut: (PinnedShortcut) -> Unit,
    onHome: () -> Unit,
    onClose: () -> Unit,
) {
    val p = LocalPalette.current
    val none = remember { MutableInteractionSource() }
    BoxWithConstraints(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).clickable(none, null, onClick = onClose),
        contentAlignment = if (left) Alignment.CenterStart else Alignment.CenterEnd,
    ) {
        val width = min(maxWidth * 0.88f, 400.dp)
        Row(
            Modifier.width(width).fillMaxHeight().background(p.bg).clickable(none, null) {}
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            val rail = @Composable {
                Column(
                    Modifier.width(48.dp).fillMaxHeight().background(p.panel).verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    tabs.forEach { t ->
                        val on = t.catId == selected
                        Box(
                            Modifier.width(40.dp).height(if (t.label.length > 5) 108.dp else 84.dp)
                                .background(if (on) p.launch else Color.Transparent, hudShape)
                                .border(1.dp, if (on) p.launch else p.line, hudShape)
                                .clickable { onSelect(t.catId) },
                            contentAlignment = Alignment.Center,
                        ) { VerticalLabel(t.label, if (on) p.onAccent else p.fg, 12) }
                    }
                }
            }
            if (left) rail()
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tabs.firstOrNull { it.catId == selected }?.label ?: "ALL", color = p.accent, fontSize = 16.sp, fontFamily = Hud.display, letterSpacing = 1.5.sp, maxLines = 1)
                    Text("%02d".format(apps.size + shortcuts.size), color = p.faint, fontSize = 11.sp, fontFamily = Hud.mono)
                    Ticks(Modifier.weight(1f).height(5.dp))
                    Text(
                        "HOME", color = p.onAccent, fontSize = 12.sp, fontFamily = Hud.mono, letterSpacing = 1.sp,
                        modifier = Modifier.background(p.accent, hudShape).clickable(onClickLabel = "ホームを開く", onClick = onHome).padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                    Text("×", color = p.muted, fontSize = 18.sp, modifier = Modifier.clickable(onClickLabel = "閉じる", onClick = onClose).padding(horizontal = 8.dp, vertical = 2.dp))
                }
                LazyVerticalGrid(
                    GridCells.Adaptive(72.dp),
                    Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(apps, key = { it.key }) { app ->
                        Cell(app.label, onClick = { onApp(app) }) {
                            Box {
                                AppIcon(app, 44.dp)
                                CountBadge(counts[app.packageName] ?: 0, Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-4).dp))
                            }
                        }
                    }
                    items(shortcuts, key = { "sc:" + it.id }) { sc ->
                        Cell(sc.label, onClick = { onShortcut(sc) }) { ShortcutIcon(sc, 44.dp) }
                    }
                }
                if (apps.isEmpty() && shortcuts.isEmpty()) {
                    Text("このタブにはまだアプリがありません。", color = p.muted, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
                    Spacer(Modifier.weight(1f))
                }
            }
            if (!left) rail()
        }
    }
}

@Composable
private fun Cell(label: String, onClick: () -> Unit, icon: @Composable () -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier.clickable(onClickLabel = "開く", onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { icon() }
        Text(label, color = p.fg, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 2.dp))
    }
}
