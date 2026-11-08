package io.github.mustypit.rifttracker.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Original point icons (24×24): flag = conquer, shield = hold, spark = ability. Tinted by Icon(). */
object RiftIcons {
    private fun icon(name: String, build: ImageVector.Builder.() -> Unit) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply(build).build()

    private fun ImageVector.Builder.fill(d: String) = addPath(addPathNodes(d), fill = SolidColor(Color.Black))

    /** A pennant planted in the ground: taking a battlefield. */
    val Conquer = icon("conquer") {
        fill("M5.35 2.5a1.15 1.15 0 0 1 1.15 1.15v16.7a1.15 1.15 0 0 1-2.3 0V3.65a1.15 1.15 0 0 1 1.15-1.15z")
        fill("M7.2 3.6c3.2-1.4 5.6 1.3 8.6 0.3 1.1-.4 2.6-.6 3.6-.4l-2.3 4.3 2.3 4.4c-1-.3-2.5 0-3.6.4-3 1-5.4-1.7-8.6-.3z")
        fill("M3.1 19.6h6.8a1.1 1.1 0 0 1 0 2.2H3.1a1.1 1.1 0 0 1 0-2.2z")
    }

    /** A shield with a highlight cut out: keeping a battlefield. */
    val Hold = icon("hold") {
        // Even-odd fill so the highlight band shows the button colour through it.
        addPath(
            addPathNodes(
                "M12 1.8l8.2 3.1v6.3c0 5.2-3.4 9.3-8.2 11-4.8-1.7-8.2-5.8-8.2-11V4.9z" +
                    "M12 5.1v1.9l4.1 1.55v2.75c0 2.7-1.6 5-4.1 6.2v2c3.6-1.3 6-4.5 6-8.2V7.1z",
            ),
            fill = SolidColor(Color.Black),
            pathFillType = androidx.compose.ui.graphics.PathFillType.EvenOdd,
            strokeLineJoin = StrokeJoin.Round,
        )
    }

    /** A four-point spark with a small companion. */
    val Ability = icon("ability") {
        fill("M10.5 2l2.2 7 7 2.2-7 2.2-2.2 7-2.2-7-7-2.2 7-2.2z")
        fill("M19 1.8l.9 2.4 2.4.9-2.4.9-.9 2.4-.9-2.4-2.4-.9 2.4-.9z")
    }

    val Minus = icon("minus") { fill("M6 10.7h12a1.3 1.3 0 0 1 0 2.6H6a1.3 1.3 0 0 1 0-2.6z") }
}
