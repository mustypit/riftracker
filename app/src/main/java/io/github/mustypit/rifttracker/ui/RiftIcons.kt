package io.github.mustypit.rifttracker.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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

    /** A plain solid shield: keeping a battlefield. */
    val Hold = icon("hold") {
        fill("M12 1.8l8.2 3.1v6.3c0 5.2-3.4 9.3-8.2 11-4.8-1.7-8.2-5.8-8.2-11V4.9z")
    }

    /** A four-point spark with a small companion. */
    val Ability = icon("ability") {
        fill("M10.5 2l2.2 7 7 2.2-7 2.2-2.2 7-2.2-7-7-2.2 7-2.2z")
        fill("M19 1.8l.9 2.4 2.4.9-2.4.9-.9 2.4-.9-2.4-2.4-.9 2.4-.9z")
    }

    val Minus = icon("minus") { fill("M6 10.7h12a1.3 1.3 0 0 1 0 2.6H6a1.3 1.3 0 0 1 0-2.6z") }

    /** Two crossed swords, kept minimal: two lines in an X, each with a short crossguard. The Play tab. */
    val CrossedSwords = icon("crossed_swords") {
        fun line(d: String) = addPath(
            addPathNodes(d), stroke = SolidColor(Color.Black), strokeLineWidth = 2.4f,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
        line("M4.5 4.5L19 19")    // blade + handle, top-left to bottom-right
        line("M19.5 4.5L5 19")    // the other one
        line("M13.5 18.5L18.5 13.5") // its crossguard
        line("M5.5 13.5L10.5 18.5")  // and this one's
    }

    /** Two cards, the front one outlined: the Cards tab. Kept as simple as the other tab icons. */
    val Cards = icon("cards") {
        addPath(
            addPathNodes("M9 3.5h9A2 2 0 0 1 20 5.5v12A2 2 0 0 1 18 19.5H9A2 2 0 0 1 7 17.5v-12A2 2 0 0 1 9 3.5z"), fill = null,
            stroke = SolidColor(Color.Black), strokeLineWidth = 2.2f,
        )
        addPath(
            addPathNodes("M4.5 7v11.5A2.5 2.5 0 0 0 7 21h8"), fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2.2f,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
    }

    /** A stack of three cards seen from the side: the Decks tab. */
    val Deck = icon("deck") {
        addPath(addPathNodes("M6 4.5h12A1.5 1.5 0 0 1 19.5 6v7A1.5 1.5 0 0 1 18 14.5H6A1.5 1.5 0 0 1 4.5 13V6A1.5 1.5 0 0 1 6 4.5z"), fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2.2f)
        addPath(addPathNodes("M5 17.5h14M6.5 20.5h11"), fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2.2f, strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round)
    }
}
