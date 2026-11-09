package io.github.mustypit.rifttracker.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.mustypit.rifttracker.game.Outcome
import io.github.mustypit.rifttracker.game.Side

/** One set of role colours for light or dark mode. */
data class Palette(
    /** Screen background. */
    val paper: Color,
    /** Cards, text fields, the tab bar, dialogs. */
    val card: Color,
    /** Main text and icons on paper/card. */
    val ink: Color,
    val muted: Color,
    val line: Color,
    /** Segmented-control background, empty placeholders. */
    val track: Color,
    /** Unselected result buttons and similar soft fills. */
    val soft: Color,
    /** Strong fill for primary buttons and selected segments/chips, and the text on it. */
    val solid: Color,
    val onSolid: Color,
    /** Header blocks at the top of screens, and their text. */
    val header: Color,
    val onHeader: Color,
    val drawGrey: Color,
)

private val LightPalette = Palette(
    paper = Color(0xFFF6F4F0), card = Color.White, ink = Color(0xFF111111), muted = Color(0xFF6B6B6B),
    line = Color(0xFFE4E0DA), track = Color(0xFFE6E1DA), soft = Color(0xFFF0ECE6),
    solid = Color(0xFF111111), onSolid = Color.White, header = Color(0xFF111111), onHeader = Color.White,
    drawGrey = Color(0xFFBDB8B0),
)

/** Warm near-black, matching the light theme's paper tone; the player colours stay the same. */
private val DarkPalette = Palette(
    paper = Color(0xFF121110), card = Color(0xFF1E1C1A), ink = Color(0xFFF2EEE8), muted = Color(0xFFA8A29A),
    line = Color(0xFF34302C), track = Color(0xFF2A2724), soft = Color(0xFF282522),
    solid = Color(0xFFF2EEE8), onSolid = Color(0xFF111111), header = Color(0xFF000000), onHeader = Color.White,
    drawGrey = Color(0xFF77726C),
)

/**
 * "Bold blocks": two loud player colours, black bars, yellow highlights, heavy type.
 * Role colours follow the current palette (set by [RiftTheme]); the fixed colours never change.
 */
object C {
    // Fixed, same in both modes.
    val Green = Color(0xFF3FAE63)
    val GreenInk = Color(0xFF06210F)
    val Red = Color(0xFFE2483A)
    val RedInk = Color(0xFF2A0805)
    val Yellow = Color(0xFFFFE14A)
    /** Always black: the counter's bars and dark text on coloured fills. */
    val Black = Color(0xFF111111)

    // Role colours.
    var palette: Palette = LightPalette
        private set
    val Paper get() = palette.paper
    val Card get() = palette.card
    val Ink get() = palette.ink
    val Muted get() = palette.muted
    val Line get() = palette.line
    val Track get() = palette.track
    val Soft get() = palette.soft
    val Solid get() = palette.solid
    val OnSolid get() = palette.onSolid
    val Header get() = palette.header
    val OnHeader get() = palette.onHeader
    val DrawGrey get() = palette.drawGrey

    fun use(dark: Boolean) { palette = if (dark) DarkPalette else LightPalette }

    fun side(s: Side) = if (s == Side.ME) Green else Red
    fun sideInk(s: Side) = if (s == Side.ME) GreenInk else RedInk
    fun outcome(o: Outcome) = when (o) { Outcome.WIN -> Green; Outcome.LOSS -> Red; Outcome.DRAW -> DrawGrey }
}

private fun scheme(p: Palette, dark: Boolean) = (if (dark) darkColorScheme() else lightColorScheme()).copy(
    primary = p.solid, onPrimary = p.onSolid,
    secondary = C.Green, onSecondary = C.GreenInk,
    tertiary = C.Red,
    background = p.paper, onBackground = p.ink,
    surface = p.paper, onSurface = p.ink,
    surfaceVariant = p.track, onSurfaceVariant = p.muted,
    surfaceContainer = p.card, surfaceContainerHigh = p.card, surfaceContainerLow = p.card,
    surfaceContainerHighest = p.track,
    outline = p.muted, outlineVariant = p.line,
    error = Color(0xFFE5534B),
)

private val base = Typography()
private val type = Typography(
    displayLarge = base.displayLarge.copy(fontWeight = FontWeight.Black),
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Black),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Black),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Bold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp),
)

/** Small all-caps label used across screens. */
val Caps = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp)

/** Follows the phone's light/dark setting. */
@Composable
fun RiftTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    C.use(dark)
    MaterialTheme(colorScheme = scheme(C.palette, dark), typography = type, content = content)
}
