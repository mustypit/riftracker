package io.github.mustypit.rifttracker.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.mustypit.rifttracker.game.Outcome
import io.github.mustypit.rifttracker.game.Side

/** "Bold blocks": two loud player colours, black bars, yellow highlights, heavy type. */
object C {
    val Teal = Color(0xFF21C4A7)
    val TealInk = Color(0xFF03231D)
    val Coral = Color(0xFFFF6B4A)
    val CoralInk = Color(0xFF2A0C05)
    val Ink = Color(0xFF111111)
    val Yellow = Color(0xFFFFE14A)
    val Paper = Color(0xFFF6F4F0)
    val Card = Color.White
    val Muted = Color(0xFF6B6B6B)
    val Line = Color(0xFFE4E0DA)
    val DrawGrey = Color(0xFFBDB8B0)

    fun side(s: Side) = if (s == Side.ME) Teal else Coral
    fun sideInk(s: Side) = if (s == Side.ME) TealInk else CoralInk
    fun outcome(o: Outcome) = when (o) { Outcome.WIN -> Teal; Outcome.LOSS -> Coral; Outcome.DRAW -> DrawGrey }
}

private val scheme = lightColorScheme(
    primary = C.Ink, onPrimary = Color.White,
    secondary = C.Teal, onSecondary = C.TealInk,
    tertiary = C.Coral,
    background = C.Paper, onBackground = C.Ink,
    surface = C.Paper, onSurface = C.Ink,
    surfaceVariant = Color(0xFFECE8E2), onSurfaceVariant = C.Muted,
    surfaceContainer = C.Card, surfaceContainerHigh = C.Card, surfaceContainerLow = C.Card,
    surfaceContainerHighest = Color(0xFFECE8E2),
    outline = Color(0xFF8A857E), outlineVariant = C.Line,
    error = Color(0xFFC62828),
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

@Composable
fun RiftTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = scheme, typography = type, content = content)
