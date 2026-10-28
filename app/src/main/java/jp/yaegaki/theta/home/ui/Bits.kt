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
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent

/** 押せる行。長押しも受ける。最低 48dp の高さ。zebra に並び順（0 始まり）を渡すと、奇数行に薄い地を敷く（縞模様）。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RowButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    label: String? = null,
    zebra: Int = -1,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(if (zebra % 2 == 1) LocalPalette.current.zebra else Color.Transparent)
            .defaultMinSize(minHeight = 48.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onClickLabel = label, role = Role.Button)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

/** 角を 1 つ落とした矩形のボタン。primary はオレンジで塗る。 */
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
            .popShadow(p, hudShape)
            .clip(hudShape)
            .background(if (p.pop && !primary) p.panel else bg)
            .border(lineWidth, if (primary && !p.pop) p.accent else p.line, hudShape)
            .combinedClickableCompat(enabled, onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = fg, fontSize = 13.sp, fontFamily = Hud.mono, letterSpacing = 0.5.sp)
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
            .background(if (highlight) p.accent else Color.Transparent)
            .border(1.dp, if (highlight) p.accent else p.line)
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (highlight) p.onAccent else p.muted, fontSize = 11.sp, fontFamily = Hud.mono, maxLines = 1)
    }
}

@Composable
fun Initial(text: String, size: Dp = 40.dp) {
    val p = LocalPalette.current
    Box(
        Modifier.size(size).background(p.raised).border(1.dp, p.line).corners(p.accent, 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = p.fg, fontSize = (size.value * 0.38f).sp)
    }
}

/** 見出し：オレンジの小さな四角＋等幅の文字。 */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(6.dp).background(p.accent))
        Text(text, color = p.muted, fontSize = 12.sp, letterSpacing = 1.5.sp, fontFamily = Hud.mono)
    }
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
    password: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    /** キーボードの「検索」キーで呼ぶ（null ならふつうの改行・完了）。 */
    onSearch: (() -> Unit)? = null,
) {
    val p = LocalPalette.current
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = singleLine,
        textStyle = TextStyle(color = p.fg, fontSize = 15.sp, fontFamily = Hud.mono),
        cursorBrush = SolidColor(p.accent),
        visualTransformation = if (password) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        modifier = modifier.onFocusChanged { onFocus?.invoke(it.isFocused) },
        keyboardOptions = if (onSearch != null) androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search) else androidx.compose.foundation.text.KeyboardOptions.Default,
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { onSearch?.invoke() }),
        decorationBox = { inner ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .clip(hudShape)
                    .background(if (p.pop) p.panel else p.raised)
                    .border(lineWidth, p.line, hudShape)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                leading?.invoke()
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(placeholder, color = p.faint, fontSize = 15.sp)
                    inner()
                }
                trailing?.invoke()
            }
        },
    )
}

/** タブの見出し：横書きの文字を 90 度倒して置く（上から下へ読む）。日本語も同じく倒す。 */
@Composable
fun VerticalLabel(text: String, color: Color, fontSize: Int = 13) {
    Text(
        text, color = color, fontSize = fontSize.sp, maxLines = 1, letterSpacing = 1.5.sp, fontFamily = Hud.mono,
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
}

/** 線で描く小さな記号（虫めがね・ピン・×・‹・＋、タブの印）。アイコン用ライブラリに頼らない。 */
enum class Glyph {
    SEARCH, PIN, CLOSE, BACK, PLUS, GEAR, LIST, GRID, FOLDER, MIC,
    CALENDAR, MAIL, CHAT, PERSON, LINK, CLIP, CAMERA, HATCH, GLOBE, FILE,
    STAR, HOME, MUSIC, IMAGE, WORK, HEART, CLOCK, CART, GAME, BOOK, CAR, PENCIL,
}

@Composable
fun GlyphIcon(g: Glyph, color: Color, size: Dp = 18.dp) {
    Canvas(Modifier.size(size)) { drawGlyph(g, color) }
}

private fun DrawScope.drawGlyph(g: Glyph, color: Color) {
    val w = size.width
    val s = Stroke(width = w * 0.09f, cap = StrokeCap.Square)
    when (g) {
        Glyph.SEARCH -> {
            drawCircle(color, radius = w * 0.3f, center = Offset(w * 0.44f, w * 0.44f), style = s)
            drawLine(color, Offset(w * 0.66f, w * 0.66f), Offset(w * 0.9f, w * 0.9f), s.width, StrokeCap.Square)
        }
        Glyph.CLOSE -> {
            drawLine(color, Offset(w * 0.22f, w * 0.22f), Offset(w * 0.78f, w * 0.78f), s.width, StrokeCap.Square)
            drawLine(color, Offset(w * 0.78f, w * 0.22f), Offset(w * 0.22f, w * 0.78f), s.width, StrokeCap.Square)
        }
        Glyph.BACK -> {
            drawLine(color, Offset(w * 0.62f, w * 0.18f), Offset(w * 0.3f, w * 0.5f), s.width, StrokeCap.Square)
            drawLine(color, Offset(w * 0.3f, w * 0.5f), Offset(w * 0.62f, w * 0.82f), s.width, StrokeCap.Square)
        }
        Glyph.PLUS -> {
            drawLine(color, Offset(w * 0.5f, w * 0.18f), Offset(w * 0.5f, w * 0.82f), s.width, StrokeCap.Square)
            drawLine(color, Offset(w * 0.18f, w * 0.5f), Offset(w * 0.82f, w * 0.5f), s.width, StrokeCap.Square)
        }
        Glyph.PIN -> {
            val path = Path().apply {
                moveTo(w * 0.36f, w * 0.12f); lineTo(w * 0.64f, w * 0.12f); lineTo(w * 0.6f, w * 0.42f)
                lineTo(w * 0.78f, w * 0.58f); lineTo(w * 0.22f, w * 0.58f); lineTo(w * 0.4f, w * 0.42f); close()
            }
            drawPath(path, color, style = s)
            drawLine(color, Offset(w * 0.5f, w * 0.58f), Offset(w * 0.5f, w * 0.9f), s.width, StrokeCap.Square)
        }
        Glyph.LIST -> for (i in 0 until 3) {
            val y = w * (0.26f + i * 0.24f)
            drawLine(color, Offset(w * 0.16f, y), Offset(w * 0.24f, y), s.width, StrokeCap.Square)
            drawLine(color, Offset(w * 0.38f, y), Offset(w * 0.84f, y), s.width, StrokeCap.Square)
        }
        Glyph.GRID -> for (i in 0 until 2) for (j in 0 until 2) {
            drawRect(color, Offset(w * (0.16f + i * 0.38f), w * (0.16f + j * 0.38f)), androidx.compose.ui.geometry.Size(w * 0.3f, w * 0.3f), style = s)
        }
        Glyph.FOLDER -> {
            val path = Path().apply {
                moveTo(w * 0.1f, w * 0.24f); lineTo(w * 0.4f, w * 0.24f); lineTo(w * 0.48f, w * 0.34f); lineTo(w * 0.9f, w * 0.34f)
                lineTo(w * 0.9f, w * 0.8f); lineTo(w * 0.1f, w * 0.8f); close()
            }
            drawPath(path, color, style = s)
        }
        Glyph.MIC -> {
            drawRoundRect(color, Offset(w * 0.38f, w * 0.12f), androidx.compose.ui.geometry.Size(w * 0.24f, w * 0.44f), androidx.compose.ui.geometry.CornerRadius(w * 0.12f), style = s)
            drawArc(color, 0f, 180f, false, Offset(w * 0.26f, w * 0.3f), androidx.compose.ui.geometry.Size(w * 0.48f, w * 0.4f), style = s)
            drawLine(color, Offset(w * 0.5f, w * 0.7f), Offset(w * 0.5f, w * 0.86f), s.width, StrokeCap.Square)
            drawLine(color, Offset(w * 0.36f, w * 0.88f), Offset(w * 0.64f, w * 0.88f), s.width, StrokeCap.Square)
        }
        Glyph.CALENDAR -> {
            drawRect(color, Offset(w * 0.14f, w * 0.2f), androidx.compose.ui.geometry.Size(w * 0.72f, w * 0.66f), style = s)
            drawLine(color, Offset(w * 0.14f, w * 0.38f), Offset(w * 0.86f, w * 0.38f), s.width, StrokeCap.Square)
            drawLine(color, Offset(w * 0.34f, w * 0.1f), Offset(w * 0.34f, w * 0.26f), s.width, StrokeCap.Square)
            drawLine(color, Offset(w * 0.66f, w * 0.1f), Offset(w * 0.66f, w * 0.26f), s.width, StrokeCap.Square)
            drawRect(color, Offset(w * 0.3f, w * 0.52f), androidx.compose.ui.geometry.Size(w * 0.14f, w * 0.14f))
        }
        Glyph.MAIL -> {
            drawRect(color, Offset(w * 0.12f, w * 0.24f), androidx.compose.ui.geometry.Size(w * 0.76f, w * 0.54f), style = s)
            drawLine(color, Offset(w * 0.12f, w * 0.24f), Offset(w * 0.5f, w * 0.54f), s.width, StrokeCap.Square)
            drawLine(color, Offset(w * 0.5f, w * 0.54f), Offset(w * 0.88f, w * 0.24f), s.width, StrokeCap.Square)
        }
        Glyph.CHAT -> {
            val path = Path().apply {
                moveTo(w * 0.12f, w * 0.18f); lineTo(w * 0.88f, w * 0.18f); lineTo(w * 0.88f, w * 0.66f)
                lineTo(w * 0.42f, w * 0.66f); lineTo(w * 0.24f, w * 0.84f); lineTo(w * 0.24f, w * 0.66f); lineTo(w * 0.12f, w * 0.66f); close()
            }
            drawPath(path, color, style = s)
        }
        Glyph.PERSON -> {
            drawCircle(color, radius = w * 0.16f, center = Offset(w * 0.5f, w * 0.32f), style = s)
            drawArc(color, 180f, 180f, false, Offset(w * 0.18f, w * 0.56f), androidx.compose.ui.geometry.Size(w * 0.64f, w * 0.56f), style = s)
        }
        Glyph.LINK -> {
            drawRoundRect(color, Offset(w * 0.08f, w * 0.36f), androidx.compose.ui.geometry.Size(w * 0.48f, w * 0.28f), androidx.compose.ui.geometry.CornerRadius(w * 0.14f), style = s)
            drawRoundRect(color, Offset(w * 0.44f, w * 0.36f), androidx.compose.ui.geometry.Size(w * 0.48f, w * 0.28f), androidx.compose.ui.geometry.CornerRadius(w * 0.14f), style = s)
        }
        Glyph.CLIP -> {
            drawRect(color, Offset(w * 0.2f, w * 0.18f), androidx.compose.ui.geometry.Size(w * 0.6f, w * 0.7f), style = s)
            drawRect(color, Offset(w * 0.36f, w * 0.1f), androidx.compose.ui.geometry.Size(w * 0.28f, w * 0.16f))
            drawLine(color, Offset(w * 0.32f, w * 0.48f), Offset(w * 0.68f, w * 0.48f), s.width, StrokeCap.Square)
            drawLine(color, Offset(w * 0.32f, w * 0.66f), Offset(w * 0.6f, w * 0.66f), s.width, StrokeCap.Square)
        }
        Glyph.CAMERA -> {
            drawRect(color, Offset(w * 0.1f, w * 0.3f), androidx.compose.ui.geometry.Size(w * 0.8f, w * 0.52f), style = s)
            drawLine(color, Offset(w * 0.34f, w * 0.18f), Offset(w * 0.6f, w * 0.18f), s.width, StrokeCap.Square)
            drawCircle(color, radius = w * 0.14f, center = Offset(w * 0.5f, w * 0.56f), style = s)
        }
        Glyph.HATCH -> {
            val path = Path().apply {
                for (i in 0 until 6) {
                    val a = Math.toRadians(-90.0 + i * 60)
                    val x = w * 0.5f + (w * 0.38f * Math.cos(a)).toFloat()
                    val y = w * 0.5f + (w * 0.38f * Math.sin(a)).toFloat()
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
            drawPath(path, color, style = s)
            drawLine(color, Offset(w * 0.5f, w * 0.34f), Offset(w * 0.5f, w * 0.66f), s.width, StrokeCap.Square)
            drawLine(color, Offset(w * 0.36f, w * 0.5f), Offset(w * 0.64f, w * 0.5f), s.width, StrokeCap.Square)
        }
        Glyph.GLOBE -> {
            drawCircle(color, radius = w * 0.38f, center = Offset(w / 2, w / 2), style = s)
            drawOval(color, Offset(w * 0.32f, w * 0.12f), androidx.compose.ui.geometry.Size(w * 0.36f, w * 0.76f), style = s)
            drawLine(color, Offset(w * 0.12f, w * 0.5f), Offset(w * 0.88f, w * 0.5f), s.width, StrokeCap.Square)
        }
        Glyph.FILE -> {
            val path = Path().apply {
                moveTo(w * 0.2f, w * 0.1f); lineTo(w * 0.6f, w * 0.1f); lineTo(w * 0.8f, w * 0.3f); lineTo(w * 0.8f, w * 0.9f); lineTo(w * 0.2f, w * 0.9f); close()
            }
            drawPath(path, color, style = s)
            drawLine(color, Offset(w * 0.6f, w * 0.1f), Offset(w * 0.6f, w * 0.3f), s.width, StrokeCap.Square)
            drawLine(color, Offset(w * 0.6f, w * 0.3f), Offset(w * 0.8f, w * 0.3f), s.width, StrokeCap.Square)
        }
        Glyph.STAR -> {
            val path = Path().apply {
                for (i in 0 until 10) {
                    val r = if (i % 2 == 0) 0.4f else 0.17f
                    val a = Math.toRadians(-90.0 + i * 36)
                    val x = w * 0.5f + (w * r * Math.cos(a)).toFloat()
                    val y = w * 0.54f + (w * r * Math.sin(a)).toFloat()
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
            drawPath(path, color, style = s)
        }
        Glyph.HOME -> {
            val path = Path().apply {
                moveTo(w * 0.12f, w * 0.48f); lineTo(w * 0.5f, w * 0.14f); lineTo(w * 0.88f, w * 0.48f)
                moveTo(w * 0.22f, w * 0.4f); lineTo(w * 0.22f, w * 0.86f); lineTo(w * 0.78f, w * 0.86f); lineTo(w * 0.78f, w * 0.4f)
            }
            drawPath(path, color, style = s)
            drawRect(color, Offset(w * 0.42f, w * 0.6f), androidx.compose.ui.geometry.Size(w * 0.16f, w * 0.26f), style = s)
        }
        Glyph.MUSIC -> {
            drawCircle(color, radius = w * 0.12f, center = Offset(w * 0.3f, w * 0.74f))
            drawCircle(color, radius = w * 0.12f, center = Offset(w * 0.72f, w * 0.64f))
            drawLine(color, Offset(w * 0.4f, w * 0.74f), Offset(w * 0.4f, w * 0.18f), s.width, StrokeCap.Square)
            drawLine(color, Offset(w * 0.82f, w * 0.64f), Offset(w * 0.82f, w * 0.1f), s.width, StrokeCap.Square)
            drawLine(color, Offset(w * 0.4f, w * 0.18f), Offset(w * 0.82f, w * 0.1f), s.width, StrokeCap.Square)
        }
        Glyph.IMAGE -> {
            drawRect(color, Offset(w * 0.1f, w * 0.18f), androidx.compose.ui.geometry.Size(w * 0.8f, w * 0.64f), style = s)
            drawCircle(color, radius = w * 0.08f, center = Offset(w * 0.34f, w * 0.38f))
            val path = Path().apply { moveTo(w * 0.14f, w * 0.78f); lineTo(w * 0.42f, w * 0.52f); lineTo(w * 0.6f, w * 0.68f); lineTo(w * 0.72f, w * 0.56f); lineTo(w * 0.88f, w * 0.72f) }
            drawPath(path, color, style = s)
        }
        Glyph.WORK -> {
            drawRect(color, Offset(w * 0.1f, w * 0.3f), androidx.compose.ui.geometry.Size(w * 0.8f, w * 0.54f), style = s)
            drawRect(color, Offset(w * 0.36f, w * 0.16f), androidx.compose.ui.geometry.Size(w * 0.28f, w * 0.14f), style = s)
            drawLine(color, Offset(w * 0.1f, w * 0.52f), Offset(w * 0.9f, w * 0.52f), s.width, StrokeCap.Square)
        }
        Glyph.HEART -> {
            val path = Path().apply {
                moveTo(w * 0.5f, w * 0.84f)
                cubicTo(w * 0.1f, w * 0.56f, w * 0.06f, w * 0.2f, w * 0.32f, w * 0.18f)
                cubicTo(w * 0.42f, w * 0.17f, w * 0.48f, w * 0.24f, w * 0.5f, w * 0.32f)
                cubicTo(w * 0.52f, w * 0.24f, w * 0.58f, w * 0.17f, w * 0.68f, w * 0.18f)
                cubicTo(w * 0.94f, w * 0.2f, w * 0.9f, w * 0.56f, w * 0.5f, w * 0.84f)
            }
            drawPath(path, color, style = s)
        }
        Glyph.CLOCK -> {
            drawCircle(color, radius = w * 0.38f, center = Offset(w / 2, w / 2), style = s)
            drawLine(color, Offset(w * 0.5f, w * 0.5f), Offset(w * 0.5f, w * 0.26f), s.width, StrokeCap.Square)
            drawLine(color, Offset(w * 0.5f, w * 0.5f), Offset(w * 0.68f, w * 0.6f), s.width, StrokeCap.Square)
        }
        Glyph.CART -> {
            val path = Path().apply { moveTo(w * 0.08f, w * 0.18f); lineTo(w * 0.24f, w * 0.18f); lineTo(w * 0.34f, w * 0.64f); lineTo(w * 0.82f, w * 0.64f); lineTo(w * 0.9f, w * 0.32f); lineTo(w * 0.28f, w * 0.32f) }
            drawPath(path, color, style = s)
            drawCircle(color, radius = w * 0.07f, center = Offset(w * 0.38f, w * 0.82f))
            drawCircle(color, radius = w * 0.07f, center = Offset(w * 0.76f, w * 0.82f))
        }
        Glyph.GAME -> {
            drawRoundRect(color, Offset(w * 0.08f, w * 0.3f), androidx.compose.ui.geometry.Size(w * 0.84f, w * 0.44f), androidx.compose.ui.geometry.CornerRadius(w * 0.2f), style = s)
            drawLine(color, Offset(w * 0.24f, w * 0.52f), Offset(w * 0.42f, w * 0.52f), s.width, StrokeCap.Square)
            drawLine(color, Offset(w * 0.33f, w * 0.43f), Offset(w * 0.33f, w * 0.61f), s.width, StrokeCap.Square)
            drawCircle(color, radius = w * 0.05f, center = Offset(w * 0.64f, w * 0.48f))
            drawCircle(color, radius = w * 0.05f, center = Offset(w * 0.74f, w * 0.58f))
        }
        Glyph.BOOK -> {
            val path = Path().apply {
                moveTo(w * 0.5f, w * 0.24f); lineTo(w * 0.1f, w * 0.18f); lineTo(w * 0.1f, w * 0.8f); lineTo(w * 0.5f, w * 0.86f)
                lineTo(w * 0.9f, w * 0.8f); lineTo(w * 0.9f, w * 0.18f); lineTo(w * 0.5f, w * 0.24f); lineTo(w * 0.5f, w * 0.86f)
            }
            drawPath(path, color, style = s)
        }
        Glyph.CAR -> {
            val path = Path().apply { moveTo(w * 0.1f, w * 0.66f); lineTo(w * 0.1f, w * 0.48f); lineTo(w * 0.26f, w * 0.26f); lineTo(w * 0.74f, w * 0.26f); lineTo(w * 0.9f, w * 0.48f); lineTo(w * 0.9f, w * 0.66f); close() }
            drawPath(path, color, style = s)
            drawLine(color, Offset(w * 0.1f, w * 0.48f), Offset(w * 0.9f, w * 0.48f), s.width, StrokeCap.Square)
            drawCircle(color, radius = w * 0.08f, center = Offset(w * 0.28f, w * 0.74f))
            drawCircle(color, radius = w * 0.08f, center = Offset(w * 0.72f, w * 0.74f))
        }
        Glyph.PENCIL -> {
            val path = Path().apply { moveTo(w * 0.66f, w * 0.12f); lineTo(w * 0.88f, w * 0.34f); lineTo(w * 0.34f, w * 0.88f); lineTo(w * 0.12f, w * 0.88f); lineTo(w * 0.12f, w * 0.66f); close() }
            drawPath(path, color, style = s)
            drawLine(color, Offset(w * 0.56f, w * 0.22f), Offset(w * 0.78f, w * 0.44f), s.width, StrokeCap.Square)
        }
        Glyph.GEAR -> {
            drawCircle(color, radius = w * 0.16f, center = Offset(w / 2, w / 2), style = s)
            for (i in 0 until 8) {
                val a = Math.toRadians(i * 45.0)
                val c = Offset(w / 2, w / 2)
                val o1 = Offset(c.x + (w * 0.3f * Math.cos(a)).toFloat(), c.y + (w * 0.3f * Math.sin(a)).toFloat())
                val o2 = Offset(c.x + (w * 0.44f * Math.cos(a)).toFloat(), c.y + (w * 0.44f * Math.sin(a)).toFloat())
                drawLine(color, o1, o2, s.width, StrokeCap.Square)
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
            .border(1.dp, p.line)
            .corners(p.accent, 10.dp)
            .padding(14.dp)
            .widthIn(max = 600.dp),
    ) {
        Text(text, color = p.muted, fontSize = 12.sp, lineHeight = 19.sp)
    }
}

/** 左上だけ角を落とした形（計器のパネル風）。 */
val Chamfer = CutCornerShape(topStart = 8.dp)

/** 四隅にかぎ括弧（⌜ ⌝ ⌞ ⌟）を描く。 */
fun Modifier.corners(color: Color, length: Dp = 12.dp, width: Dp = 1.5.dp) = drawWithContent {
    drawContent()
    val l = length.toPx()
    val w = width.toPx()
    val r = size.width
    val b = size.height
    listOf(
        Offset(0f, 0f) to Offset(l, 0f), Offset(0f, 0f) to Offset(0f, l),
        Offset(r, 0f) to Offset(r - l, 0f), Offset(r, 0f) to Offset(r, l),
        Offset(0f, b) to Offset(l, b), Offset(0f, b) to Offset(0f, b - l),
        Offset(r, b) to Offset(r - l, b), Offset(r, b) to Offset(r, b - l),
    ).forEach { (a, c) -> drawLine(color, a, c, w) }
}

/** 斜線の帯（警告テープ風）。 */
@Composable
fun Stripes(modifier: Modifier, color: Color = LocalPalette.current.accent, gap: Dp = 7.dp) {
    Canvas(modifier.clipToBounds()) {
        val step = gap.toPx()
        val w = step * 0.45f
        var x = -size.height
        while (x < size.width) {
            drawLine(color, Offset(x, size.height), Offset(x + size.height, 0f), w)
            x += step
        }
    }
}

/** 小さな計器の読み：上に灰色の項目名、下に値。 */
@Composable
fun Readout(label: String, value: String, modifier: Modifier = Modifier, accent: Boolean = false) {
    val p = LocalPalette.current
    Column(modifier) {
        Text(label, color = p.faint, fontSize = 10.sp, fontFamily = Hud.mono, letterSpacing = 1.sp)
        Text(value, color = if (accent) p.accent else p.fg, fontSize = 14.sp, fontFamily = Hud.mono)
    }
}

/** 目盛り：細い縦線を等間隔に。 */
@Composable
fun Ticks(modifier: Modifier, color: Color = LocalPalette.current.line, every: Dp = 6.dp) {
    Canvas(modifier) {
        val step = every.toPx()
        var x = 0f
        var i = 0
        while (x <= size.width) {
            val h = if (i % 5 == 0) size.height else size.height * 0.5f
            drawLine(color, Offset(x, size.height), Offset(x, size.height - h), 1f)
            x += step
            i++
        }
    }
}
