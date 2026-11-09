package io.github.mustypit.rifttracker.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

/** The app's motion, in one place. Durations are short: the app is used mid-game. */
object Motion {
    const val ROLL_MS = 260
    const val SCREEN_MS = 280
    const val COUNT_UP_MS = 700
    /** Per row of the score chart as it draws in, top to bottom. */
    const val CHART_ROW_MS = 45
}

/** Shrinks a little while pressed and springs back: pass the clickable's [source]. */
fun Modifier.squishWhenPressed(source: MutableInteractionSource, pressedScale: Float = 0.88f) = composed {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) pressedScale else 1f,
        spring(dampingRatio = 0.45f, stiffness = if (pressed) 1400f else 600f),
        label = "squish",
    )
    graphicsLayer { scaleX = scale; scaleY = scale }
}

/**
 * A number on a wheel: going up, the old number rolls down and out while the new one comes in from
 * the top; going down, the reverse. Clipped to its own box, like a window onto the wheel.
 */
@Composable
fun RollingNumber(value: Int, modifier: Modifier = Modifier, content: @Composable (Int) -> Unit) {
    AnimatedContent(
        targetState = value,
        modifier = modifier,
        contentAlignment = Alignment.Center,
        transitionSpec = { rollTransition(up = targetState > initialState) },
        label = "rolling number",
    ) { content(it) }
}

private fun rollTransition(up: Boolean): ContentTransform {
    val spec = tween<androidx.compose.ui.unit.IntOffset>(Motion.ROLL_MS, easing = FastOutSlowInEasing)
    val enter = slideInVertically(spec) { h -> if (up) -h else h } + fadeIn(tween(Motion.ROLL_MS / 2))
    val exit = slideOutVertically(spec) { h -> if (up) h else -h } + fadeOut(tween(Motion.ROLL_MS))
    return ContentTransform(enter, exit, sizeTransform = SizeTransform(clip = true))
}

/** Screens: deeper slides in from the right, back slides out to the right, tab switches crossfade. */
fun screenTransition(fromDepth: Int, toDepth: Int): ContentTransform {
    val slide = tween<androidx.compose.ui.unit.IntOffset>(Motion.SCREEN_MS, easing = FastOutSlowInEasing)
    return when {
        toDepth > fromDepth -> (slideInHorizontally(slide) { it } + fadeIn(tween(Motion.SCREEN_MS / 2))) togetherWith
            (slideOutHorizontally(slide) { -it / 4 } + fadeOut(tween(Motion.SCREEN_MS)))
        toDepth < fromDepth -> (slideInHorizontally(slide) { -it / 4 } + fadeIn(tween(Motion.SCREEN_MS))) togetherWith
            (slideOutHorizontally(slide) { it } + fadeOut(tween(Motion.SCREEN_MS / 2)))
        else -> fadeIn(tween(200)) togetherWith fadeOut(tween(150))
    }.apply { targetContentZIndex = toDepth.toFloat() }
}

/** A win rate that counts up from 0 when it first appears. */
@Composable
fun countUpPercent(rate: Double?): String {
    if (rate == null) return "–"
    val shown = remember { Animatable(0f) }
    LaunchedEffect(rate) { shown.animateTo(rate.toFloat(), tween(Motion.COUNT_UP_MS, easing = FastOutSlowInEasing)) }
    return "${Math.round(shown.value * 100)}%"
}

/** 0 → [rows] over time, for drawing a list in row by row. Row i shows `(value - i).coerceIn(0, 1)`. */
@Composable
fun rowReveal(rows: Int): Float {
    val p = remember { Animatable(0f) }
    LaunchedEffect(rows) { p.animateTo(rows.toFloat(), tween(rows * Motion.CHART_ROW_MS + 250, easing = LinearEasing)) }
    return p.value
}
