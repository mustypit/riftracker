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

/** Graphite: neutral dark grey, no tint (the owner's pick; the first warm dark felt yellowish). */
private val DarkPalette = Palette(
    paper = Color(0xFF121314), card = Color(0xFF1D1E20), ink = Color(0xFFEEEFF1), muted = Color(0xFF9C9EA3),
    line = Color(0xFF2F3134), track = Color(0xFF26282B), soft = Color(0xFF242629),
    solid = Color(0xFFEEEFF1), onSolid = Color(0xFF111111), header = Color(0xFF000000), onHeader = Color.White,
    drawGrey = Color(0xFF6F7277),
)

/**
 * The highlight colour across the app (tab pill, middle-bar pill, filter tokens…), set in Settings with
 * a hue slider or a preset. The slider keeps saturation and lightness fixed so every hue stays bright.
 */
object Accents {
    /** Pastel, spread around the wheel; red and green are left out (they mean opponent and you). Each
     *  tested for black text (≥ 9:1) and on the black header (≥ 10:1). */
    val presets = listOf(
        "Butter" to Color(0xFFFCEA92), "Apricot" to Color(0xFFFCC392), "Blush" to Color(0xFFFAA8C3),
        "Orchid" to Color(0xFFEBADEB), "Lavender" to Color(0xFFCAAFF8), "Periwinkle" to Color(0xFFA1B2F7),
        "Sky" to Color(0xFF8ED3F6), "Pearl" to Color(0xFFE6E6E6),
    )
    val DEFAULT = presets.first().second

    /** The custom colour (behind "+"): any hue, at the same pastel saturation and lightness. */
    fun fromHue(hue: Float): Color = Color.hsl(hue.coerceIn(0f, 359.9f), 0.9f, 0.8f)

    /** Hue (0–360) of [c], for placing the slider's knob. */
    fun hueOf(c: Color): Float {
        val r = c.red; val g = c.green; val b = c.blue
        val max = maxOf(r, g, b); val min = minOf(r, g, b); val d = max - min
        if (d == 0f) return 0f
        val h = when (max) { r -> ((g - b) / d) % 6f; g -> (b - r) / d + 2f; else -> (r - g) / d + 4f } * 60f
        return if (h < 0) h + 360f else h
    }

    fun contrast(a: Color, b: Color): Float {
        val la = a.luminance(); val lb = b.luminance()
        return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
    }

    /** Black or white, whichever reads better on [c]. */
    fun inkOn(c: Color): Color = Color(0xFF111111).let { dark -> if (contrast(c, dark) >= contrast(c, Color.White)) dark else Color.White }
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
    /** The accent the owner picked (gold by default), and the text colour on it. */
    private var accentColor: Color = Accents.DEFAULT
    val Accent get() = accentColor
    val OnAccent get() = Accents.inkOn(accentColor)
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

    fun use(dark: Boolean, accent: Color = accentColor) {
        palette = if (dark) DarkPalette else LightPalette
        accentColor = accent
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
fun RiftTheme(dark: Boolean = isSystemInDarkTheme(), accent: Color = Accents.DEFAULT, content: @Composable () -> Unit) {
    C.use(dark, accent)
    MaterialTheme(colorScheme = scheme(C.palette, dark), typography = type, content = content)
}
