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
)

val LocalPalette = staticCompositionLocalOf { DarkPalette }

/** ボタン・入力欄の形：HUD は左上を落とした角、POP は少し丸めた角。 */
val hudShape: androidx.compose.ui.graphics.Shape
    @Composable @androidx.compose.runtime.ReadOnlyComposable get() =
        if (LocalPalette.current.pop) RoundedCornerShape(8.dp) else Chamfer

/** 線の太さ：POP は太め。 */
val lineWidth: androidx.compose.ui.unit.Dp
    @Composable @androidx.compose.runtime.ReadOnlyComposable get() = if (LocalPalette.current.pop) 1.5.dp else 1.dp

/** POP のボタンの、右下にずらした影。HUD では何もしない。 */
fun androidx.compose.ui.Modifier.popShadow(p: Palette, shape: androidx.compose.ui.graphics.Shape): androidx.compose.ui.Modifier =
    if (!p.pop) this else this.drawBehind {
        val o = 3.dp.toPx()
        translate(o, o) {
            drawOutline(shape.createOutline(size, layoutDirection, this), p.fg)
        }
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

    /** POP では、見出しも小さなラベルも端末のゴシック（太め）にする。 */
    val display: FontFamily
        @Composable @androidx.compose.runtime.ReadOnlyComposable get() = if (LocalPalette.current.pop) FontFamily.SansSerif else oswald
    val mono: FontFamily
        @Composable @androidx.compose.runtime.ReadOnlyComposable get() = if (LocalPalette.current.pop) FontFamily.SansSerif else tech
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
    val corner = if (p.pop) 10.dp else 0.dp
    MaterialTheme(colorScheme = scheme, typography = type, shapes = Shapes(
        extraSmall = RoundedCornerShape(corner), small = RoundedCornerShape(corner), medium = RoundedCornerShape(corner),
        large = RoundedCornerShape(corner), extraLarge = RoundedCornerShape(corner),
    ), content = content)
}
