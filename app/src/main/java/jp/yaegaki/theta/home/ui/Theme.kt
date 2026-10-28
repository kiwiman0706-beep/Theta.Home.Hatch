package jp.yaegaki.theta.home.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.draw.drawBehind
import jp.yaegaki.theta.home.R
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * 計器盤（HUD）風：黒地にオレンジ 1 色、細い線、四角い角。文字は白と灰の 3 段階。
 * タブの群（フォルダ／ウィジェット／起動）はオレンジ・白・灰の線で見分ける。
 */
@Immutable
data class Palette(
    val bg: Color,
    val panel: Color,
    val raised: Color,
    val fg: Color,
    val muted: Color,
    val faint: Color,
    val line: Color,
    val accent: Color,
    val onAccent: Color,
    val widget: Color,
    val launch: Color,
    /** リストの縞模様。地の色に薄く重ねる。 */
    val zebra: Color,
    /** POP（カラフル）：角を少し丸め、線を太く、ボタンに影、書体はゴシック。色を 3 つ使い分ける。 */
    val pop: Boolean = false,
    /** POP の 2 色目（イエロー）・3 色目（ミント）。 */
    val accent2: Color = accent,
    val accent3: Color = accent,
    /** 背景の模様："" なし・"scan"（走査線、ALERT）・"grid"（方眼、SCI-FI）。 */
    val backdrop: String = "",
    /** ドロワーの見出しの上の縞（警告の帯）の太さ。 */
    val hazard: androidx.compose.ui.unit.Dp = 4.dp,
    /**
     * 作りの種類。"hud"（計器盤）・"pop"・"term"（緑の端末、見出しは反転した帯）・"braun"（家電、めくり時計・丸いつまみ）・
     * "soft"（やわらかい浮き出し）・"window"（昔のパソコンの窓、かわいい）・"lcd"（液晶ゲーム機、太い線）。
     */
    val style: String = "hud",
    /** 角の丸み（pop の作り）。 */
    val radius: androidx.compose.ui.unit.Dp = 8.dp,
    /** 書体："hud"（細長い見出し＋等幅）・"sans"（端末のゴシック）・"mono"（全部等幅）。 */
    val font: String = "hud",
    /** 影："none"・"hard"（右下にずらした線の影）・"soft"（ぼかした影）。 */
    val shadow: String = "none",
    /** アイコンの台の形："square"・"round"（角丸）・"circle"。 */
    val iconShape: String = "square",
) {
    fun group(g: Int) = when (g) {
        0 -> accent
        1 -> widget
        else -> launch
    }
}

val DarkPalette = Palette(
    bg = Color(0xFF0A0A0B),
    panel = Color(0xFF111113),
    raised = Color(0xFF19191C),
    fg = Color(0xFFEDEDED),
    muted = Color(0xFFA3A3A3),
    faint = Color(0xFF858585),
    line = Color(0xFF333336),
    accent = Color(0xFFFF6B1A),
    onAccent = Color(0xFF0A0A0B),
    widget = Color(0xFFEDEDED),
    launch = Color(0xFF7A7A7E),
    zebra = Color(0x0FFFFFFF),
)

/** 明るい地の計器盤：紙のような地に墨色の文字、オレンジは文字にも使えるよう濃いめ。 */
val LightPalette = Palette(
    bg = Color(0xFFF4F3EE),
    panel = Color(0xFFFFFFFF),
    raised = Color(0xFFE9E8E2),
    fg = Color(0xFF121212),
    muted = Color(0xFF474747),
    faint = Color(0xFF5E5E5E),
    line = Color(0xFFBDBCB4),
    accent = Color(0xFFB8400C),
    onAccent = Color(0xFFFFFFFF),
    widget = Color(0xFF121212),
    launch = Color(0xFF6A6A6A),
    zebra = Color(0x0D000000),
)

/**
 * POP：クリームの地に墨色の太い線、コーラル・イエロー・ミントの 3 色。
 * フォルダはコーラル、ウィジェットはミント、起動はイエロー。
 */
val PopPalette = Palette(
    bg = Color(0xFFFAF6EC),
    panel = Color(0xFFFFFFFF),
    raised = Color(0xFFF2EDE0),
    fg = Color(0xFF2B2B2B),
    muted = Color(0xFF4F4F4F),
    faint = Color(0xFF6B6B6B),
    line = Color(0xFF2B2B2B),
    accent = Color(0xFFEF6F51),
    onAccent = Color(0xFFFFFFFF),
    widget = Color(0xFF7FCFB5),
    launch = Color(0xFFF2BE2E),
    zebra = Color(0x26F5C842),
    pop = true,
    accent2 = Color(0xFFF5C842),
    accent3 = Color(0xFFA8E2CF),
    style = "pop",
    font = "sans",
    shadow = "hard",
    iconShape = "round",
)

/**
 * ALERT：特務機関の警報画面ふう。黒地に橙の文字、警告の赤と、正常の緑。見出しの上に太い警告の帯、背景に走査線。
 * フォルダは橙、ウィジェット・予定などは赤、起動は緑。
 */
val AlertPalette = Palette(
    bg = Color(0xFF050404),
    panel = Color(0xFF0D0908),
    raised = Color(0xFF1A110C),
    fg = Color(0xFFFFD9B0),
    muted = Color(0xFFD6955C),
    faint = Color(0xFF9C6A42),
    line = Color(0xFF5C3418),
    accent = Color(0xFFFF7A00),
    onAccent = Color(0xFF050404),
    widget = Color(0xFFFF2A2A),
    launch = Color(0xFF39E07A),
    zebra = Color(0x14FF7A00),
    accent2 = Color(0xFFFF2A2A),
    accent3 = Color(0xFF39E07A),
    backdrop = "scan",
    hazard = 10.dp,
)

/**
 * SCI-FI：宇宙船の操作卓ふう。黒に近い青緑の地に方眼、黄・水色・緑の 3 色。
 * フォルダは黄、ウィジェット・予定などは水色、起動は緑。
 */
val SciFiPalette = Palette(
    bg = Color(0xFF060C0E),
    panel = Color(0xFF0B1519),
    raised = Color(0xFF112128),
    fg = Color(0xFFDDF6FF),
    muted = Color(0xFF8FB8C4),
    faint = Color(0xFF5F8794),
    line = Color(0xFF1F3C46),
    accent = Color(0xFFF2E21B),
    onAccent = Color(0xFF060C0E),
    widget = Color(0xFF3FD0E8),
    launch = Color(0xFF7BE38B),
    zebra = Color(0x123FD0E8),
    accent2 = Color(0xFF3FD0E8),
    accent3 = Color(0xFF7BE38B),
    backdrop = "grid",
    hazard = 6.dp,
)

/** TERMINAL：緑の端末。黒地に蛍光の緑、見出しは反転した帯、文字は全部等幅、背景に流れる文字の列。 */
val TermPalette = Palette(
    bg = Color(0xFF030805),
    panel = Color(0xFF06100A),
    raised = Color(0xFF0B1E12),
    fg = Color(0xFF8CFFB0),
    muted = Color(0xFF4FC97A),
    faint = Color(0xFF358A55),
    line = Color(0xFF1D5531),
    accent = Color(0xFF3DFF7F),
    onAccent = Color(0xFF031006),
    widget = Color(0xFFC9FFD9),
    launch = Color(0xFF2E9E58),
    zebra = Color(0x143DFF7F),
    accent2 = Color(0xFFC9FFD9),
    accent3 = Color(0xFF2E9E58),
    backdrop = "rain",
    hazard = 0.dp,
    style = "term",
    font = "mono",
)

/** BRAUN：白い家電。生成りの地、灰色の文字、オレンジ 1 色。めくり時計、丸いつまみ、浮き出た角丸の台。 */
val BraunPalette = Palette(
    bg = Color(0xFFE9E7E1),
    panel = Color(0xFFF6F5F1),
    raised = Color(0xFFDDDAD2),
    fg = Color(0xFF2A2A2A),
    muted = Color(0xFF575757),
    faint = Color(0xFF6E6B64),
    line = Color(0xFFBFBBB1),
    accent = Color(0xFFE8711A),
    onAccent = Color(0xFFFFFFFF),
    widget = Color(0xFF5E7A68),
    launch = Color(0xFF3A3A3A),
    zebra = Color(0x0A000000),
    pop = true,
    accent2 = Color(0xFF5E7A68),
    accent3 = Color(0xFFC9C5BA),
    style = "braun",
    radius = 14.dp,
    font = "sans",
    shadow = "soft",
    iconShape = "circle",
)

/** SOFT：やわらかい浮き出し。薄い灰色の地から台が浮き出る、線は使わない。色はオレンジ・青・緑・赤の淡い色。 */
val SoftPalette = Palette(
    bg = Color(0xFFECEDF0),
    panel = Color(0xFFF3F4F7),
    raised = Color(0xFFE3E5EA),
    fg = Color(0xFF262A33),
    muted = Color(0xFF545B68),
    faint = Color(0xFF6B7280),
    line = Color(0xFFD9DCE2),
    accent = Color(0xFFF0A23A),
    onAccent = Color(0xFF262A33),
    widget = Color(0xFF5FA8DC),
    launch = Color(0xFF7FC08E),
    zebra = Color(0x08000000),
    pop = true,
    accent2 = Color(0xFFE8606F),
    accent3 = Color(0xFF7FC08E),
    style = "soft",
    radius = 18.dp,
    font = "sans",
    shadow = "soft",
    iconShape = "round",
)

/** WINDOW：昔のパソコンの窓（かわいい）。水色の地に白い方眼、クリームの窓にオレンジのタイトルバー、こげ茶の太い線。 */
val WindowPalette = Palette(
    bg = Color(0xFF52C3D6),
    panel = Color(0xFFFFF8EA),
    raised = Color(0xFFFBEBCF),
    fg = Color(0xFF3B2C27),
    muted = Color(0xFF5E4C44),
    faint = Color(0xFF7D6A60),
    line = Color(0xFF3B2C27),
    accent = Color(0xFFF5A33A),
    onAccent = Color(0xFF3B2C27),
    widget = Color(0xFF7C8DF2),
    launch = Color(0xFFF28DB2),
    zebra = Color(0x14F5A33A),
    pop = true,
    accent2 = Color(0xFF7C8DF2),
    accent3 = Color(0xFFF28DB2),
    backdrop = "lines",
    style = "window",
    radius = 6.dp,
    font = "sans",
    shadow = "hard",
    iconShape = "round",
)

/** LCD：液晶のゲーム機。くすんだ黄緑の地に濃い緑の太い線と文字、全部等幅。 */
val LcdPalette = Palette(
    bg = Color(0xFF8E9B69),
    panel = Color(0xFF99A673),
    raised = Color(0xFFA6B27F),
    fg = Color(0xFF1C2610),
    muted = Color(0xFF2F3D1C),
    faint = Color(0xFF45542C),
    line = Color(0xFF1C2610),
    accent = Color(0xFF1C2610),
    onAccent = Color(0xFFA6B27F),
    widget = Color(0xFF3C5568),
    launch = Color(0xFF6B4A33),
    zebra = Color(0x101C2610),
    accent2 = Color(0xFF3C5568),
    accent3 = Color(0xFF6B4A33),
    backdrop = "pixel",
    hazard = 0.dp,
    style = "lcd",
    font = "mono",
)

/** テーマの名前から配色を決める。"system" は端末のダーク・ライトに合わせる。 */
fun paletteFor(mode: String, systemDark: Boolean): Palette = when (mode) {
    "term" -> TermPalette
    "braun" -> BraunPalette
    "soft" -> SoftPalette
    "window" -> WindowPalette
    "lcd" -> LcdPalette
    "pop" -> PopPalette
    "alert" -> AlertPalette
    "scifi" -> SciFiPalette
    "light" -> LightPalette
    "dark" -> DarkPalette
    else -> if (systemDark) DarkPalette else LightPalette
}

/** 背景の模様（ALERT の走査線・SCI-FI の方眼）。ほかのテーマでは何もしない。 */
fun androidx.compose.ui.Modifier.backdrop(p: Palette): androidx.compose.ui.Modifier = when (p.backdrop) {
    "scan" -> drawBehind {
        val step = 4.dp.toPx()
        var y = 0f
        while (y < size.height) {
            drawLine(p.accent.copy(alpha = 0.05f), androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), 1f)
            y += step
        }
    }
    "grid" -> drawBehind {
        val step = 28.dp.toPx()
        var x = 0f
        while (x < size.width) {
            drawLine(p.widget.copy(alpha = 0.07f), androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Offset(x, size.height), 1f)
            x += step
        }
        var y = 0f
        while (y < size.height) {
            drawLine(p.widget.copy(alpha = 0.07f), androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), 1f)
            y += step
        }
    }
    // TERMINAL：縦に流れる文字の列（止まった絵。薄く）
    "rain" -> drawBehind {
        val step = 18.dp.toPx()
        val dot = 3.dp.toPx()
        var x = step / 2
        var col = 0
        while (x < size.width) {
            // 列ごとに長さと位置をずらした、点の並び
            val seed = (col * 7919) % 97
            val start = (seed / 97f) * size.height
            val len = size.height * (0.15f + (seed % 13) / 40f)
            var y = start
            var k = 0
            while (y < start + len && y < size.height) {
                val a = 0.10f * (1f - (y - start) / len)
                drawRect(p.accent.copy(alpha = a), androidx.compose.ui.geometry.Offset(x, y), androidx.compose.ui.geometry.Size(dot, dot * (1 + (k + seed) % 3)))
                y += dot * 4
                k++
            }
            x += step
            col++
        }
    }
    // WINDOW：白い細い方眼
    "lines" -> drawBehind {
        val step = 36.dp.toPx()
        var x = 0f
        while (x < size.width) {
            drawLine(Color.White.copy(alpha = 0.22f), androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Offset(x, size.height), 1.5f)
            x += step
        }
        var y = 0f
        while (y < size.height) {
            drawLine(Color.White.copy(alpha = 0.22f), androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), 1.5f)
            y += step
        }
    }
    // LCD：液晶の画素の格子
    "pixel" -> drawBehind {
        val step = 3.dp.toPx()
        var y = 0f
        while (y < size.height) {
            drawLine(p.fg.copy(alpha = 0.035f), androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), 1f)
            y += step
        }
    }
    else -> this
}

val LocalPalette = staticCompositionLocalOf { DarkPalette }

/** ボタン・入力欄の形：HUD は左上を落とした角、POP は少し丸めた角。 */
val hudShape: androidx.compose.ui.graphics.Shape
    @Composable @androidx.compose.runtime.ReadOnlyComposable get() =
        LocalPalette.current.let { p ->
            when {
                p.pop -> RoundedCornerShape(p.radius)
                p.style == "term" || p.style == "lcd" -> androidx.compose.ui.graphics.RectangleShape
                else -> Chamfer
            }
        }

/** 線の太さ：POP は太め。 */
val lineWidth: androidx.compose.ui.unit.Dp
    @Composable @androidx.compose.runtime.ReadOnlyComposable get() = LocalPalette.current.let { p ->
        when {
            p.style == "lcd" || p.style == "window" -> 2.dp
            p.pop -> 1.5.dp
            else -> 1.dp
        }
    }

/**
 * 台の影。"hard"（POP・WINDOW）は右下にずらした線の色の影、"soft"（SOFT・BRAUN）は右下に暗く・左上に明るくぼかした浮き出し。
 * 影の無い作りでは何もしない。
 */
fun androidx.compose.ui.Modifier.popShadow(p: Palette, shape: androidx.compose.ui.graphics.Shape): androidx.compose.ui.Modifier = when (p.shadow) {
    "hard" -> this.drawBehind {
        val o = 3.dp.toPx()
        translate(o, o) {
            drawOutline(shape.createOutline(size, layoutDirection, this), p.fg)
        }
    }
    "soft" -> this.drawBehind {
        val outline = shape.createOutline(size, layoutDirection, this)
        // 重ねるほど薄く、遠くへ：ぼかした影の代わり
        for (i in 1..5) {
            val o = i * 1.6.dp.toPx()
            translate(o, o) { drawOutline(outline, Color.Black.copy(alpha = 0.035f)) }
            translate(-o * 0.6f, -o * 0.6f) { drawOutline(outline, Color.White.copy(alpha = 0.12f)) }
        }
    }
    else -> this
}

/** 見出し・時計は細長い Oswald、ラベル・数字・ファイル名は等幅の Share Tech Mono。日本語は端末の書体になる。 */
object Hud {
    private val oswald = FontFamily(
        Font(R.font.oswald_light, FontWeight.Light),
        Font(R.font.oswald_regular, FontWeight.Normal),
        Font(R.font.oswald_semibold, FontWeight.SemiBold),
        Font(R.font.oswald_semibold, FontWeight.Bold),
    )
    private val tech = FontFamily(Font(R.font.share_tech_mono, FontWeight.Normal))

    /** POP などでは見出しも小さなラベルも端末のゴシック、TERMINAL・LCD では見出しも等幅。 */
    val display: FontFamily
        @Composable @androidx.compose.runtime.ReadOnlyComposable get() = when (LocalPalette.current.font) {
            "sans" -> FontFamily.SansSerif
            "mono" -> tech
            else -> oswald
        }
    val mono: FontFamily
        @Composable @androidx.compose.runtime.ReadOnlyComposable get() = if (LocalPalette.current.font == "sans") FontFamily.SansSerif else tech
}

@Composable
fun HomeTheme(dark: Boolean, pop: Boolean = false, content: @Composable () -> Unit) {
    val p = when {
        pop -> PopPalette
        dark -> DarkPalette
        else -> LightPalette
    }
    CompositionLocalProvider(LocalPalette provides p) { HomeThemeInner(p, dark && !pop, content) }
}

/** 設定のテーマ名（dark・light・pop・alert・scifi・term・braun・soft・window・lcd・system）で包む。 */
@Composable
fun HomeTheme(mode: String, content: @Composable () -> Unit) {
    val p = paletteFor(mode, androidx.compose.foundation.isSystemInDarkTheme())
    CompositionLocalProvider(LocalPalette provides p) { HomeThemeInner(p, p.isDark, content) }
}

/** 地が暗い配色か（ステータスバーの文字色・Material の配色に使う）。 */
val Palette.isDark: Boolean get() = bg.luminance() < 0.5f

@Composable
private fun HomeThemeInner(p: Palette, dark: Boolean, content: @Composable () -> Unit) {
    val scheme = if (!dark) {
        lightColorScheme(
            primary = p.accent, onPrimary = p.onAccent, background = p.bg, surface = p.panel,
            onSurface = p.fg, onBackground = p.fg, surfaceVariant = p.raised, onSurfaceVariant = p.muted,
            outline = p.line, surfaceContainerHigh = p.panel, surfaceContainer = p.panel,
        )
    } else {
        darkColorScheme(
            primary = p.accent, onPrimary = p.onAccent, background = p.bg, surface = p.panel,
            onSurface = p.fg, onBackground = p.fg, surfaceVariant = p.raised, onSurfaceVariant = p.muted,
            outline = p.line, surfaceContainerHigh = p.panel, surfaceContainer = p.panel,
        )
    }
    val base = Typography()
    val type = Typography(
        bodyLarge = base.bodyLarge.copy(fontFamily = Hud.mono),
        bodyMedium = base.bodyMedium.copy(fontFamily = Hud.mono),
        bodySmall = base.bodySmall.copy(fontFamily = Hud.mono),
        labelLarge = base.labelLarge.copy(fontFamily = Hud.mono),
        labelMedium = base.labelMedium.copy(fontFamily = Hud.mono),
        labelSmall = base.labelSmall.copy(fontFamily = Hud.mono),
        titleLarge = base.titleLarge.copy(fontFamily = Hud.display),
        titleMedium = base.titleMedium.copy(fontFamily = Hud.display),
        headlineSmall = base.headlineSmall.copy(fontFamily = Hud.display),
    )
    val corner = if (p.pop) p.radius else 0.dp
    MaterialTheme(colorScheme = scheme, typography = type, shapes = Shapes(
        extraSmall = RoundedCornerShape(corner), small = RoundedCornerShape(corner), medium = RoundedCornerShape(corner),
        large = RoundedCornerShape(corner), extraLarge = RoundedCornerShape(corner),
    ), content = content)
}
