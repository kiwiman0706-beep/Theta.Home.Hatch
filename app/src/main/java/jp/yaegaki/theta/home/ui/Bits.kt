package jp.yaegaki.theta.home.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path

/** 押せる行。長押しも受ける。最低 48dp の高さ。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RowButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    label: String? = null,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onClickLabel = label, role = Role.Button)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

/** 枠線だけの小さなボタン。primary はアクセント色で塗る。 */
@Composable
fun PillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false, enabled: Boolean = true) {
    val p = LocalPalette.current
    val bg = if (primary) p.accent else Color.Transparent
    val fg = when {
        !enabled -> p.faint
        primary -> p.onAccent
        else -> p.fg
    }
    Box(
        modifier
            .defaultMinSize(minHeight = 40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .border(1.dp, if (primary) p.accent else p.line, RoundedCornerShape(20.dp))
            .combinedClickableCompat(enabled, onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = fg, fontSize = 13.sp, fontWeight = if (primary) FontWeight.Bold else FontWeight.Normal)
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(enabled: Boolean, onClick: () -> Unit) =
    this.combinedClickable(enabled = enabled, role = Role.Button, onClick = onClick)

/** ファイルの種類（PDF・JPG・DIR など）を示す小さな札。 */
@Composable
fun Badge(text: String, highlight: Boolean = false) {
    val p = LocalPalette.current
    Box(
        Modifier
            .width(46.dp)
            .border(1.dp, if (highlight) p.accent else p.line, RoundedCornerShape(6.dp))
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (highlight) p.accent else p.muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
fun Initial(text: String, size: Dp = 40.dp) {
    val p = LocalPalette.current
    Box(
        Modifier.size(size).clip(CircleShape).background(p.raised).border(1.dp, p.line, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = p.fg, fontSize = (size.value * 0.38f).sp)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, color = LocalPalette.current.muted, fontSize = 12.sp, letterSpacing = 1.5.sp, modifier = modifier)
}

@Composable
fun TwoLines(title: String, sub: String?, modifier: Modifier = Modifier, bold: Boolean = false) {
    val p = LocalPalette.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, color = p.fg, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
        if (!sub.isNullOrEmpty()) Text(sub, color = p.muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun Note(text: String, modifier: Modifier = Modifier) {
    Text(text, color = LocalPalette.current.faint, fontSize = 12.sp, lineHeight = 19.sp, modifier = modifier.padding(horizontal = 10.dp, vertical = 6.dp))
}

/** 角丸の入力欄。 */
@Composable
fun Field(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    onFocus: ((Boolean) -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    val p = LocalPalette.current
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = singleLine,
        textStyle = TextStyle(color = p.fg, fontSize = 15.sp),
        cursorBrush = SolidColor(p.accent),
        modifier = modifier.onFocusChanged { onFocus?.invoke(it.isFocused) },
        decorationBox = { inner ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .clip(RoundedCornerShape(if (singleLine) 24.dp else 14.dp))
                    .background(p.raised)
                    .border(1.dp, p.line, RoundedCornerShape(if (singleLine) 24.dp else 14.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                leading?.invoke()
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(placeholder, color = p.faint, fontSize = 15.sp)
                    inner()
                }
            }
        },
    )
}

/** 縦書きのタブ見出し。日本語は 1 文字ずつ縦に、英字は 90 度回して並べる。 */
@Composable
fun VerticalLabel(text: String, color: Color, fontSize: Int = 13) {
    val ascii = text.all { it.code < 128 }
    if (ascii) {
        Text(
            text, color = color, fontSize = fontSize.sp, maxLines = 1, letterSpacing = 1.sp,
            modifier = Modifier
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = constraints.maxHeight, minHeight = 0))
                    layout(placeable.height, placeable.width) {
                        placeable.placeWithLayer(
                            x = -(placeable.width - placeable.height) / 2,
                            y = (placeable.width - placeable.height) / 2,
                        ) { rotationZ = 90f }
                    }
                },
        )
    } else {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            text.forEach { ch ->
                Text(ch.toString(), color = color, fontSize = fontSize.sp, lineHeight = (fontSize + 3).sp)
            }
        }
    }
}

/** 線で描く小さな記号（虫めがね・ピン・×・‹・＋）。アイコン用ライブラリに頼らない。 */
enum class Glyph { SEARCH, PIN, CLOSE, BACK, PLUS, GEAR }

@Composable
fun GlyphIcon(g: Glyph, color: Color, size: Dp = 18.dp) {
    Canvas(Modifier.size(size)) { drawGlyph(g, color) }
}

private fun DrawScope.drawGlyph(g: Glyph, color: Color) {
    val w = size.width
    val s = Stroke(width = w * 0.11f, cap = StrokeCap.Round)
    when (g) {
        Glyph.SEARCH -> {
            drawCircle(color, radius = w * 0.3f, center = Offset(w * 0.44f, w * 0.44f), style = s)
            drawLine(color, Offset(w * 0.66f, w * 0.66f), Offset(w * 0.9f, w * 0.9f), s.width, StrokeCap.Round)
        }
        Glyph.CLOSE -> {
            drawLine(color, Offset(w * 0.22f, w * 0.22f), Offset(w * 0.78f, w * 0.78f), s.width, StrokeCap.Round)
            drawLine(color, Offset(w * 0.78f, w * 0.22f), Offset(w * 0.22f, w * 0.78f), s.width, StrokeCap.Round)
        }
        Glyph.BACK -> {
            drawLine(color, Offset(w * 0.62f, w * 0.18f), Offset(w * 0.3f, w * 0.5f), s.width, StrokeCap.Round)
            drawLine(color, Offset(w * 0.3f, w * 0.5f), Offset(w * 0.62f, w * 0.82f), s.width, StrokeCap.Round)
        }
        Glyph.PLUS -> {
            drawLine(color, Offset(w * 0.5f, w * 0.18f), Offset(w * 0.5f, w * 0.82f), s.width, StrokeCap.Round)
            drawLine(color, Offset(w * 0.18f, w * 0.5f), Offset(w * 0.82f, w * 0.5f), s.width, StrokeCap.Round)
        }
        Glyph.PIN -> {
            val path = Path().apply {
                moveTo(w * 0.36f, w * 0.12f); lineTo(w * 0.64f, w * 0.12f); lineTo(w * 0.6f, w * 0.42f)
                lineTo(w * 0.78f, w * 0.58f); lineTo(w * 0.22f, w * 0.58f); lineTo(w * 0.4f, w * 0.42f); close()
            }
            drawPath(path, color, style = s)
            drawLine(color, Offset(w * 0.5f, w * 0.58f), Offset(w * 0.5f, w * 0.9f), s.width, StrokeCap.Round)
        }
        Glyph.GEAR -> {
            drawCircle(color, radius = w * 0.16f, center = Offset(w / 2, w / 2), style = s)
            for (i in 0 until 8) {
                val a = Math.toRadians(i * 45.0)
                val c = Offset(w / 2, w / 2)
                val o1 = Offset(c.x + (w * 0.3f * Math.cos(a)).toFloat(), c.y + (w * 0.3f * Math.sin(a)).toFloat())
                val o2 = Offset(c.x + (w * 0.44f * Math.cos(a)).toFloat(), c.y + (w * 0.44f * Math.sin(a)).toFloat())
                drawLine(color, o1, o2, s.width, StrokeCap.Round)
            }
        }
    }
}

/** 記号だけのボタン。押せる範囲は 44dp。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GlyphButton(g: Glyph, label: String, onClick: () -> Unit, tint: Color? = null, filled: Boolean = false) {
    val p = LocalPalette.current
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (filled) p.accent else Color.Transparent)
            .combinedClickable(onClick = onClick, onClickLabel = label, role = Role.Button)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(g, tint ?: if (filled) p.onAccent else p.muted)
    }
}

@Composable
fun Gap(h: Dp) = Spacer(Modifier.height(h))

@Composable
fun DashedBox(text: String, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Box(
        modifier
            .fillMaxWidth()
            .border(1.dp, p.line, RoundedCornerShape(12.dp))
            .padding(14.dp)
            .widthIn(max = 600.dp),
    ) {
        Text(text, color = p.muted, fontSize = 12.sp, lineHeight = 19.sp)
    }
}
