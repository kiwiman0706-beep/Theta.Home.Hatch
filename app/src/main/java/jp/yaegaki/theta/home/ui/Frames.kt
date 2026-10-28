package jp.yaegaki.theta.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
    body: @Composable () -> Unit,
) {
    val p = LocalPalette.current
    when (p.style) {
        "term" -> Column(Modifier.fillMaxWidth().border(1.dp, p.line)) {
            Row(header.fillMaxWidth().background(p.fg).padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("$code  ${title}", color = p.bg, fontSize = 13.sp, fontFamily = Hud.mono, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(trailing, color = p.bg, fontSize = 13.sp, fontFamily = Hud.mono)
            }
            if (showBody) Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { body() }
        }
        "window" -> {
            val shape = RoundedCornerShape(p.radius)
            Column(Modifier.fillMaxWidth().popShadow(p, shape).clip(shape).background(p.panel).border(lineWidth, p.line, shape)) {
                WindowBar(title, header) { Text(trailing, color = p.fg, fontSize = 12.sp) }
                if (showBody) Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { body() }
            }
        }
        "lcd" -> Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(header.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title.uppercase(), color = p.fg, fontSize = 13.sp, fontFamily = Hud.mono, modifier = Modifier.weight(1f))
                Text(trailing, color = p.fg, fontSize = 13.sp, fontFamily = Hud.mono)
            }
            if (showBody) Column(Modifier.fillMaxWidth().border(2.dp, p.line).background(p.panel).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { body() }
        }
        "soft", "braun" -> {
            val shape = RoundedCornerShape(p.radius)
            Column(Modifier.fillMaxWidth().popShadow(p, shape).clip(shape).background(p.panel).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(header.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (p.style == "braun") Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(p.accent))
                    Text(if (p.style == "braun") code else title, color = p.faint, fontSize = 12.sp, letterSpacing = if (p.style == "braun") 1.5.sp else 0.sp,
                        fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Text(trailing, color = p.faint, fontSize = 12.sp)
                }
                if (showBody) body()
            }
        }
        "pop" -> {
            val shape = RoundedCornerShape(p.radius + 2.dp)
            val b = band ?: p.raised
            val on = if (b == p.accent) p.onAccent else p.fg
            Column(Modifier.fillMaxWidth().popShadow(p, shape).clip(shape).background(p.panel).border(lineWidth, p.line, shape)) {
                Row(header.fillMaxWidth().background(b).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(code, color = on, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text(title, color = on, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text(trailing, color = on, fontSize = 12.sp)
                }
                if (showBody) {
                    Box(Modifier.fillMaxWidth().height(lineWidth).background(p.line))
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { body() }
                }
            }
        }
        else -> Column(Modifier.fillMaxWidth().border(1.dp, p.line).corners(p.accent, 8.dp).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(header, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
internal fun WindowBar(title: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    val p = LocalPalette.current
    Column {
        Row(modifier.fillMaxWidth().background(p.accent).padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
