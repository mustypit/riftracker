package io.github.mustypit.rifttracker.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.StrokeCap

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
            strokeLineCap = StrokeCap.Round,
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
            strokeLineCap = StrokeCap.Round,
        )
    }

    /** A stack of three cards seen from the side: the Decks tab. */
    val Deck = icon("deck") {
        addPath(addPathNodes("M6 4.5h12A1.5 1.5 0 0 1 19.5 6v7A1.5 1.5 0 0 1 18 14.5H6A1.5 1.5 0 0 1 4.5 13V6A1.5 1.5 0 0 1 6 4.5z"), fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2.2f)
        addPath(addPathNodes("M5 17.5h14M6.5 20.5h11"), fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2.2f, strokeLineCap = StrokeCap.Round)
    }

    // Deck view buttons, drawn on the same construction as Piltover Archive's (24 grid, 1.6 stroke,
    // round ends): a list in a rounded square, a #, and three cascading cards for "stacked".
    private fun ImageVector.Builder.line(d: String) = addPath(
        addPathNodes(d), stroke = SolidColor(Color.Black), strokeLineWidth = 1.6f,
        strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
    )

    val ViewList = icon("view_list") {
        line("M8.25 2.75h7.5a5.5 5.5 0 0 1 5.5 5.5v7.5a5.5 5.5 0 0 1-5.5 5.5h-7.5a5.5 5.5 0 0 1-5.5-5.5v-7.5a5.5 5.5 0 0 1 5.5-5.5z")
        line("M7.5 8.25h9M7.5 12h9M7.5 15.75h9")
    }

    val ViewGrid = icon("view_grid") { line("M8 3.5v17M16 3.5v17M3.5 8h17M3.5 16h17") }

    val ViewStacked = icon("view_stacked") {
        line("M5.35 11.25h4.8a2.6 2.6 0 0 1 2.6 2.6v4.8a2.6 2.6 0 0 1-2.6 2.6h-4.8a2.6 2.6 0 0 1-2.6-2.6v-4.8a2.6 2.6 0 0 1 2.6-2.6z")
        line("M7 11.25V9.85a2.6 2.6 0 0 1 2.6-2.6h4.55a2.6 2.6 0 0 1 2.6 2.6v4.55a2.6 2.6 0 0 1-2.6 2.6H12.75")
        line("M11.25 7.25V5.85a2.6 2.6 0 0 1 2.6-2.6h4.55a2.6 2.6 0 0 1 2.6 2.6v4.55a2.6 2.6 0 0 1-2.6 2.6H16.75")
    }

    /** Three lines, shortest last: group and sort. */
    val Sort = icon("sort") { line("M4 7h16M4 12h11M4 17h6") }

    /** Cardmarket's mark (from Simple Icons, CC0; the brand is Cardmarket's), for links to their shop. */
    val Cardmarket = icon("cardmarket") { fill("M14.837.772c-.45 0-.6.255-.645.366-.044.11-.113.4.213.711l1.54 1.478a.124.124 0 0 1 .002.18L10.021 9.47a.934.934 0 0 0-.274.673.936.936 0 0 0 .289.669l3.977 3.82a.955.955 0 0 0 .664.267v.001c.259 0 .5-.1.68-.281l5.815-5.853a.142.142 0 0 1 .103-.042c.023 0 .065.005.1.04l1.54 1.478c.198.19.383.23.504.23.277 0 .577-.217.577-.691L24 1.726a.951.951 0 0 0-.95-.95zm-8.06.793-2.351.461s-.365.064-.606.428c-.192.286-.124.616-.124.616l3.082 14.66V1.566ZM2.843 4.907v.001L.52 5.752s-.308.106-.445.452c-.15.385-.03.634-.03.634L6.04 23.224h.86C6.559 21.8 2.843 4.907 2.843 4.907ZM23.31 12.63a.59.59 0 0 0-.417.175l-6.716 6.787a.976.976 0 0 0-.287.706c.004.267.11.515.303.7l1.084 1.046-7.668-.006.005-7.574 2.473 2.494a.592.592 0 0 0 .835.004.59.59 0 0 0 .004-.835l-3.2-3.227c-.246-.25-.562-.33-.843-.214-.282.116-.45.396-.45.747l-.006 8.794c0 .266.103.515.291.703a.986.986 0 0 0 .702.291l8.92.007v-.002c.354 0 .633-.168.747-.45.114-.283.03-.599-.224-.845l-1.708-1.647 6.578-6.648a.591.591 0 0 0-.005-.835.589.589 0 0 0-.418-.17z") }
    /** Cardmarket's brand blue. */
    val CardmarketBlue = Color(0xFF012169)
}
