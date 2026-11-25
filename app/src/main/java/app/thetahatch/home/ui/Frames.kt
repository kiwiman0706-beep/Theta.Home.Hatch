package app.thetahatch.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 区画の枠。作り（[Palette.style]）ごとに見た目を変える：
 * - hud：細い線と四隅のかぎ括弧、「[ CODE ] 見出し ////」
 * - pop：角丸の枠に色の帯の見出し
 * - term：見出しが反転した帯（緑の地に黒い文字）
 * - window：オレンジのタイトルバーに「_ □ ×」の窓
 * - lcd：太い線の箱、見出しは箱の上
 * - soft・braun：線の無い浮き出た角丸の台、見出しは小さく
 * header は見出しの行に付ける（押すとたたむなど）。band は pop の見出しの色。
 */
@Composable
internal fun SectionFrame(
    code: String,
    title: String,
    header: Modifier = Modifier,
    trailing: String = "",
    band: androidx.compose.ui.graphics.Color? = null,
    showBody: Boolean = true,
    /** 見出しの印（作りに合わせた色で。テーマの絵があればその絵柄）。 */
    glyph: Glyph? = null,
    body: @Composable () -> Unit,
) {
    val p = LocalPalette.current
    when (p.style) {
        "term" -> Column(Modifier.fillMaxWidth().border(1.dp, p.line)) {
            Row(header.fillMaxWidth().background(p.fg).padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                glyph?.let { GlyphIcon(it, p.bg, 15.dp) }
                Text("$code  ${title}", color = p.bg, fontSize = 13.sp, fontFamily = Hud.mono, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(trailing, color = p.bg, fontSize = 13.sp, fontFamily = Hud.mono)
            }
            if (showBody) Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { body() }
        }
        "window" -> {
            val shape = RoundedCornerShape(p.radius)
            Column(Modifier.fillMaxWidth().popShadow(p, shape).clip(shape).background(p.panel).border(lineWidth, p.line, shape)) {
                WindowBar(title, header, glyph) { Text(trailing, color = p.fg, fontSize = 12.sp) }
                if (showBody) Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { body() }
            }
        }
        "lcd" -> Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(header.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                glyph?.let { GlyphIcon(it, p.fg, 15.dp) }
                Text(title.uppercase(), color = p.fg, fontSize = 13.sp, fontFamily = Hud.mono, modifier = Modifier.weight(1f))
                Text(trailing, color = p.fg, fontSize = 13.sp, fontFamily = Hud.mono)
            }
            if (showBody) Column(Modifier.fillMaxWidth().border(2.dp, p.line).background(p.panel).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { body() }
        }
        "soft", "braun" -> {
            val shape = RoundedCornerShape(p.radius)
            Column(Modifier.fillMaxWidth().popShadow(p, shape).clip(shape).background(p.cardBg).padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(header.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (p.style == "braun" && glyph == null) Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(p.accent))
                    glyph?.let { GlyphIcon(it, p.accent, 16.dp) }
                    // DUSK：見本の部品帳のように、太い英字の見出し
                    if (p.shadow == "glow") Text(code.uppercase(), color = p.fg, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text(if (p.style == "braun") code else title, color = p.faint, fontSize = 12.sp, letterSpacing = if (p.style == "braun") 1.5.sp else 0.sp,
                        fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Text(trailing, color = p.faint, fontSize = 12.sp)
                }
                if (showBody) body()
            }
        }
        "gazee" -> GazeeCard(title, header, trailing, showBody, glyph, body)
        "note" -> NoteCard(title, header, trailing, band?.takeIf { it != p.raised && it != p.panel } ?: noteBand(code), showBody, glyph, body)
        "pop" -> {
            val shape = RoundedCornerShape(p.radius + 2.dp)
            val b = band ?: p.raised
            val on = if (b == p.accent) p.onAccent else p.fg
            Column(Modifier.fillMaxWidth().popShadow(p, shape).clip(shape).background(p.panel).border(lineWidth, p.line, shape)) {
                Row(header.fillMaxWidth().background(b).padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    glyph?.let { GlyphIcon(it, on, 15.dp) }
                    Text(code, color = on, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text(title, color = on, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text(trailing, color = on, fontSize = 12.sp)
                }
                if (showBody) {
                    Box(Modifier.fillMaxWidth().height(lineWidth).background(p.line))
                    Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { body() }
                }
            }
        }
        else -> Column(Modifier.fillMaxWidth().border(1.dp, p.line).corners(p.accent, 8.dp).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(header, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                glyph?.let { GlyphIcon(it, p.accent, 14.dp) }
                Text("[ $code ]", color = p.accent, fontSize = 11.sp, fontFamily = Hud.mono, letterSpacing = 2.sp)
                Text(title, color = p.muted, fontSize = 11.sp)
                Stripes(Modifier.weight(1f).height(5.dp), color = p.line)
                Text(trailing, color = p.accent, fontSize = 11.sp, fontFamily = Hud.mono)
            }
            if (showBody) body()
        }
    }
}

/** WINDOW の作りのタイトルバー：オレンジの帯に見出し、右に「_ □ ×」。 */
@Composable
internal fun WindowBar(title: String, modifier: Modifier = Modifier, glyph: Glyph? = null, trailing: @Composable RowScope.() -> Unit = {}) {
    val p = LocalPalette.current
    Column {
        Row(modifier.fillMaxWidth().background(p.accent).padding(horizontal = 10.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            glyph?.let { GlyphIcon(it, p.onAccent, 15.dp) }
            Text(title, color = p.onAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            trailing()
            listOf("–", "□", "×").forEach { g ->
                Box(Modifier.size(18.dp).clip(RoundedCornerShape(3.dp)).background(p.panel).border(1.5.dp, p.line, RoundedCornerShape(3.dp)), contentAlignment = Alignment.Center) {
                    Text(g, color = p.fg, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(lineWidth).background(p.line))
    }
}

/** NOTE の見出しの札の色：区画ごとに青・緑・桃をかわりばんこに。 */
@Composable
internal fun noteBand(code: String): androidx.compose.ui.graphics.Color {
    val p = LocalPalette.current
    return listOf(p.accent, p.widget, p.launch)[Math.floorMod(code.hashCode(), 3)]
}

/**
 * NOTE（手帳）のカード：左上にフォルダの耳のような色の札（見出し）、その下に紺の太い線の白いカード。
 * 札とカードの線はつながって見えるよう、カードを線の太さだけ上へずらす。
 */
@Composable
internal fun NoteCard(
    title: String,
    header: Modifier = Modifier,
    trailing: String = "",
    band: androidx.compose.ui.graphics.Color,
    showBody: Boolean = true,
    glyph: Glyph? = null,
    body: @Composable () -> Unit,
) {
    val p = LocalPalette.current
    val w = lineWidth
    val tab = RoundedCornerShape(topStart = p.radius, topEnd = p.radius + 6.dp)
    val card = RoundedCornerShape(topStart = 0.dp, topEnd = p.radius, bottomStart = p.radius, bottomEnd = p.radius)
    Column(Modifier.fillMaxWidth()) {
        Row(header.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Row(
                Modifier.clip(tab).background(band).border(w, p.line, tab).padding(start = 12.dp, end = 18.dp, top = 5.dp, bottom = 5.dp + w),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                glyph?.let { GlyphIcon(it, p.fg, 16.dp) }
                Text(title, color = p.fg, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(Modifier.weight(1f))
            if (trailing.isNotEmpty()) Text(trailing, color = p.muted, fontSize = 12.sp, modifier = Modifier.padding(end = 6.dp, bottom = 4.dp))
        }
        if (showBody) Column(
            Modifier.fillMaxWidth().offset(y = -w).clip(card).background(p.panel).border(w, p.line, card).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) { body() }
        else Box(Modifier.fillMaxWidth().offset(y = -w).height(w).background(p.line))
    }
}

/**
 * GAZEE（90 年代のデスクトップ）の窓：左上に黄色い札の見出し、その下に灰色の立体的な窓。
 */
@Composable
internal fun GazeeCard(
    title: String,
    header: Modifier = Modifier,
    trailing: String = "",
    showBody: Boolean = true,
    glyph: Glyph? = null,
    body: @Composable () -> Unit,
) {
    val p = LocalPalette.current
    val tab = RoundedCornerShape(topStart = p.radius, topEnd = p.radius)
    Column(Modifier.fillMaxWidth()) {
        Row(header.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Row(
                Modifier.clip(tab).background(p.accent).border(1.dp, p.line, tab).padding(horizontal = 10.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                glyph?.let { GlyphIcon(it, p.onAccent, 15.dp) }
                Text(title, color = p.onAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(Modifier.weight(1f))
            if (trailing.isNotEmpty()) Text(trailing, color = p.fg, fontSize = 12.sp, modifier = Modifier.padding(end = 6.dp, bottom = 2.dp))
        }
        if (showBody) Column(
            Modifier.fillMaxWidth().offset(y = (-1).dp).background(p.panel).bevel(p).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) { body() }
    }
}
