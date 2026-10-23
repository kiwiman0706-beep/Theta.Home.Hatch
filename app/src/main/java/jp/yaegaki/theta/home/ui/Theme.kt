package jp.yaegaki.theta.home.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** 色は少なく：地・面・文字 3 段階・線・アクセント 1 色。タブの群（フォルダ／ウィジェット／起動）だけ別の色の線で見分ける。 */
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
    bg = Color(0xFF0D0E10),
    panel = Color(0xFF15171A),
    raised = Color(0xFF1D2024),
    fg = Color(0xFFE8E9EB),
    muted = Color(0xFFA2A7AF),
    faint = Color(0xFF7D828A),
    line = Color(0xFF2C2F35),
    accent = Color(0xFFF2A65A),
    onAccent = Color(0xFF121316),
    widget = Color(0xFF7FB2FF),
    launch = Color(0xFF8E939C),
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
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
