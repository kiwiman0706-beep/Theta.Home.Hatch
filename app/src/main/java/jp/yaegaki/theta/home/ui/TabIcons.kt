package jp.yaegaki.theta.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.yaegaki.theta.home.HomeModel

/** タブの印に選べる記号（画面の操作に使う記号は除く）。 */
val TAB_GLYPHS = listOf(
    Glyph.FOLDER, Glyph.CALENDAR, Glyph.MAIL, Glyph.CHAT, Glyph.PERSON, Glyph.LINK, Glyph.CLIP, Glyph.CAMERA,
    Glyph.HATCH, Glyph.GLOBE, Glyph.FILE, Glyph.GRID, Glyph.STAR, Glyph.HOME, Glyph.WORK, Glyph.IMAGE,
    Glyph.MUSIC, Glyph.HEART, Glyph.CLOCK, Glyph.CART, Glyph.GAME, Glyph.BOOK, Glyph.CAR, Glyph.PENCIL, Glyph.MIC, Glyph.SEARCH,
)

/** タブの印を描く。icon は HomeModel.tabIcon の値（"g:<記号>"・"app:<アプリ>"）。 */
@Composable
fun TabIconView(model: HomeModel, icon: String, color: Color, size: Dp = 18.dp) {
    when {
        icon.startsWith("g:") -> runCatching { Glyph.valueOf(icon.removePrefix("g:")) }.getOrNull()?.let { GlyphIcon(it, color, size) }
        icon.startsWith("app:") -> model.apps.firstOrNull { it.key == icon.removePrefix("app:") }?.let { AppIcon(it, size + 4.dp) }
    }
}

/** タブの印を選ぶ：中身から自動・記号・アプリのアイコン・出さない。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TabIconDialog(model: HomeModel, id: String, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    val now = model.opts(id).icon
    var apps by remember { mutableStateOf(false) }
    var q by remember { mutableStateOf("") }
    fun pick(icon: String) {
        model.setTabIcon(id, icon)
        onDismiss()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("タブの印") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    PillButton("中身から自動", { pick("") }, primary = now.isEmpty())
                    PillButton("出さない", { pick("none") }, primary = now == "none")
                }
                SectionTitle("SYMBOL / 記号")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TAB_GLYPHS.forEach { g ->
                        val on = now == "g:" + g.name
                        Box(
                            Modifier.size(44.dp).background(if (on) p.accent else Color.Transparent).border(1.dp, if (on) p.accent else p.line)
                                .clickable(onClickLabel = g.name) { pick("g:" + g.name) },
                            contentAlignment = Alignment.Center,
                        ) { GlyphIcon(g, if (on) p.onAccent else p.fg, 22.dp) }
                    }
                }
                SectionTitle("APP / アプリのアイコン")
                if (!apps) PillButton("アプリから選ぶ", { apps = true })
                else {
                    Field(q, { q = it }, "名前で絞り込み")
                    Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                        model.apps.filter { q.isBlank() || it.label.contains(q, ignoreCase = true) }.forEachIndexed { zi, app ->
                            RowButton(zebra = zi, onClick = { pick("app:" + app.key) }) {
                                AppIcon(app, 28.dp)
                                Text(app.label, color = p.fg, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } },
    )
}

/** ピン留めの 1 つの名前。 */
fun dockLabel(item: Any): String = when (item) {
    is jp.yaegaki.theta.home.data.AppEntry -> item.label
    is jp.yaegaki.theta.home.data.PinnedShortcut -> item.label
    is jp.yaegaki.theta.home.DockAction -> item.label
    else -> ""
}

/** ピン留めの 1 つを開く。 */
fun dockLaunch(model: HomeModel, actions: Actions, item: Any) {
    when (item) {
        is jp.yaegaki.theta.home.data.AppEntry -> actions.start(jp.yaegaki.theta.home.data.Apps.launchIntent(item))
        is jp.yaegaki.theta.home.data.PinnedShortcut -> model.launchShortcut(item)
        is jp.yaegaki.theta.home.DockAction -> model.runDockAction(item, actions.start)
    }
}

/** ピン留めの 1 つのアイコン。機能はそのアプリのアイコンに、右下のマイクの印（アプリが無ければマイク、入力はペンだけ）。 */
@Composable
fun DockIcon(model: HomeModel, item: Any, size: Dp) {
    val p = LocalPalette.current
    when (item) {
        is jp.yaegaki.theta.home.data.AppEntry -> AppIcon(item, size)
        is jp.yaegaki.theta.home.data.PinnedShortcut -> ShortcutIcon(item, size)
        is jp.yaegaki.theta.home.DockAction -> Box(Modifier.size(size)) {
            val app = item.pkg?.let { pkg -> model.apps.firstOrNull { it.packageName == pkg } }
            if (app != null) AppIcon(app, size) else GlyphIcon(if (item.id == "quick") Glyph.PENCIL else Glyph.MIC, p.accent, size)
            if (app != null) Box(
                Modifier.align(Alignment.BottomEnd).size(size * 0.5f).background(p.accent),
                contentAlignment = Alignment.Center,
            ) { GlyphIcon(Glyph.MIC, p.onAccent, size * 0.4f) }
        }
    }
}
