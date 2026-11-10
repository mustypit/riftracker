package io.github.mustypit.rifttracker.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
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

/** Dark palettes to choose from (the first warm one felt yellowish). */
enum class DarkStyle(val label: String, val palette: Palette) {
    /** Neutral greys, no tint. */
    GRAPHITE("Graphite", Palette(
        paper = Color(0xFF121314), card = Color(0xFF1D1E20), ink = Color(0xFFEEEFF1), muted = Color(0xFF9C9EA3),
        line = Color(0xFF2F3134), track = Color(0xFF26282B), soft = Color(0xFF242629),
        solid = Color(0xFFEEEFF1), onSolid = Color(0xFF111111), header = Color(0xFF000000), onHeader = Color.White,
        drawGrey = Color(0xFF6F7277),
    )),
    /** True black background (saves battery on OLED screens), dark grey cards. */
    OLED("Black", Palette(
        paper = Color(0xFF000000), card = Color(0xFF141414), ink = Color(0xFFF2F2F2), muted = Color(0xFF9A9A9A),
        line = Color(0xFF2A2A2A), track = Color(0xFF1F1F1F), soft = Color(0xFF1A1A1A),
        solid = Color(0xFFF2F2F2), onSolid = Color(0xFF000000), header = Color(0xFF000000), onHeader = Color.White,
        drawGrey = Color(0xFF6B6B6B),
    )),
    /** Slightly cool blue-grey. */
    SLATE("Slate", Palette(
        paper = Color(0xFF0F1217), card = Color(0xFF1A1E25), ink = Color(0xFFE9EDF3), muted = Color(0xFF939CAA),
        line = Color(0xFF2B313B), track = Color(0xFF232832), soft = Color(0xFF20252E),
        solid = Color(0xFFE9EDF3), onSolid = Color(0xFF0F1217), header = Color(0xFF07090C), onHeader = Color.White,
        drawGrey = Color(0xFF6C7482),
    )),
}

/** The highlight colour across the app (tab pill, middle-bar pill, filter tokens…), picked in Settings. */
enum class Accent(val label: String, val color: Color) {
    YELLOW("Yellow", Color(0xFFFFE14A)),
    ORANGE("Orange", Color(0xFFFF9F43)),
    PINK("Pink", Color(0xFFFF6FAE)),
    VIOLET("Violet", Color(0xFFA98BFF)),
    BLUE("Blue", Color(0xFF5BC8F5)),
    ;
    /** Black or white, whichever reads better on the accent. */
    val ink: Color get() = if (color.luminance() > 0.3f) Color(0xFF111111) else Color.White
}

/**
 * "Bold blocks": two loud player colours, black bars, accent highlights (yellow by default), heavy type.
 * Role colours follow the current palette (set by [RiftTheme]); the fixed colours never change.
 */
object C {
    // Fixed, same in both modes.
    val Green = Color(0xFF3FAE63)
    val GreenInk = Color(0xFF06210F)
    val Red = Color(0xFFE2483A)
    val RedInk = Color(0xFF2A0805)
    /** The accent the owner picked (yellow by default), and the text colour on it. */
    var accent: io.github.mustypit.rifttracker.ui.Accent = io.github.mustypit.rifttracker.ui.Accent.YELLOW
        private set
    val Accent get() = accent.color
    val OnAccent get() = accent.ink
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

    /** The dark palette in use; tests switch it to render each variant. */
    var darkStyle: DarkStyle = DarkStyle.GRAPHITE

    fun use(dark: Boolean, accent: io.github.mustypit.rifttracker.ui.Accent = this.accent) {
        palette = if (dark) darkStyle.palette else LightPalette
        this.accent = accent
    }

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
fun RiftTheme(dark: Boolean = isSystemInDarkTheme(), accent: Accent = Accent.YELLOW, content: @Composable () -> Unit) {
    C.use(dark, accent)
    MaterialTheme(colorScheme = scheme(C.palette, dark), typography = type, content = content)
}
