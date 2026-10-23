package jp.yaegaki.theta.home.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
)

val GrayPalette = Palette(
    bg = Color(0xFFD6D6CE),
    panel = Color(0xFFE3E3DC),
    raised = Color(0xFFCACAC1),
    fg = Color(0xFF1E201C),
    muted = Color(0xFF474B43),
    faint = Color(0xFF5A5E55),
    line = Color(0xFF9A9E92),
    accent = Color(0xFF1E201C),
    onAccent = Color(0xFFE3E3DC),
    widget = Color(0xFF34588F),
    launch = Color(0xFF5C6058),
)

val LocalPalette = staticCompositionLocalOf { DarkPalette }

/** 見出し・時計は細長い Oswald、ラベル・数字・ファイル名は等幅の Share Tech Mono。日本語は端末の書体になる。 */
object Hud {
    val display = FontFamily(
        Font(R.font.oswald_light, FontWeight.Light),
        Font(R.font.oswald_regular, FontWeight.Normal),
        Font(R.font.oswald_semibold, FontWeight.SemiBold),
        Font(R.font.oswald_semibold, FontWeight.Bold),
    )
    val mono = FontFamily(Font(R.font.share_tech_mono, FontWeight.Normal))
}

@Composable
fun HomeTheme(gray: Boolean, content: @Composable () -> Unit) {
    val p = if (gray) GrayPalette else DarkPalette
    val scheme = if (gray) {
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
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, typography = type, shapes = Shapes(
            extraSmall = RoundedCornerShape(0.dp), small = RoundedCornerShape(0.dp), medium = RoundedCornerShape(0.dp),
            large = RoundedCornerShape(0.dp), extraLarge = RoundedCornerShape(0.dp),
        ), content = content)
    }
}
