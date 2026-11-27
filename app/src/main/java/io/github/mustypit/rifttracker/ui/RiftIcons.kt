package io.github.mustypit.rifttracker.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.PathFillType
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
    /**
     * Two crossed swords (owner's pick "1"): solid blades with points, crossguards, grips and round
     * pommels, crossed high so the guards stay apart; the back sword is cut where the front one passes.
     * Generated as one even-odd shape (see design/icon/play_swords.py).
     */
    val CrossedSwords = icon("crossed_swords") {
        addPath(addPathNodes("M11.59 7.47L8.34 4.21L5.95 3.58L6.59 5.96L9.84 9.22ZM4.60 12.73L3.85 14.04L7.58 17.78L8.90 17.02L7.62 15.75L17.41 5.96L18.05 3.58L15.66 4.21L5.87 14.00ZM18.28 15.91L17.81 16.38L20.11 18.69L20.59 18.21L21.07 17.74L18.76 15.43L20.15 14.04L19.40 12.73L18.13 14.00L14.16 10.03L12.41 11.78L16.38 15.75L15.10 17.02L16.42 17.78ZM5.24 15.43L2.93 17.74L3.89 18.69L6.19 16.38ZM3.72 18.71L3.69 18.63L3.65 18.56L3.61 18.48L3.57 18.41L3.52 18.34L3.47 18.28L3.41 18.21L3.35 18.16L3.28 18.10L3.21 18.05L3.14 18.01L3.07 17.97L2.99 17.93L2.91 17.90L2.83 17.88L2.75 17.86L2.66 17.85L2.58 17.84L2.49 17.84L2.41 17.84L2.32 17.85L2.24 17.86L2.16 17.88L2.08 17.90L2.00 17.93L1.92 17.97L1.85 18.01L1.77 18.05L1.71 18.10L1.64 18.16L1.58 18.21L1.52 18.28L1.47 18.34L1.42 18.41L1.37 18.48L1.33 18.56L1.30 18.63L1.27 18.71L1.24 18.79L1.22 18.88L1.21 18.96L1.20 19.04L1.20 19.13L1.20 19.21L1.21 19.30L1.22 19.38L1.24 19.46L1.27 19.55L1.30 19.62L1.33 19.70L1.37 19.78L1.42 19.85L1.47 19.92L1.52 19.98L1.58 20.04L1.64 20.10L1.71 20.16L1.77 20.20L1.85 20.25L1.92 20.29L2.00 20.32L2.08 20.35L2.16 20.38L2.24 20.40L2.32 20.41L2.41 20.42L2.49 20.42L2.58 20.42L2.66 20.41L2.75 20.40L2.83 20.38L2.91 20.35L2.99 20.32L3.07 20.29L3.14 20.25L3.21 20.20L3.28 20.16L3.35 20.10L3.41 20.04L3.47 19.98L3.52 19.92L3.57 19.85L3.61 19.78L3.65 19.70L3.69 19.62L3.72 19.55L3.74 19.46L3.76 19.38L3.78 19.30L3.78 19.21L3.79 19.13L3.78 19.04L3.78 18.96L3.76 18.88L3.74 18.79ZM22.70 18.63L22.67 18.56L22.63 18.48L22.58 18.41L22.53 18.34L22.48 18.28L22.42 18.21L22.36 18.16L22.29 18.10L22.23 18.05L22.15 18.01L22.08 17.97L22.00 17.93L21.92 17.90L21.84 17.88L21.76 17.86L21.68 17.85L21.59 17.84L21.51 17.84L21.42 17.84L21.34 17.85L21.25 17.86L21.17 17.88L21.09 17.90L21.01 17.93L20.93 17.97L20.86 18.01L20.79 18.05L20.72 18.10L20.65 18.16L20.59 18.21L20.53 18.28L20.48 18.34L20.43 18.41L20.39 18.48L20.35 18.56L20.31 18.63L20.28 18.71L20.26 18.79L20.24 18.88L20.22 18.96L20.22 19.04L20.21 19.13L20.22 19.21L20.22 19.30L20.24 19.38L20.26 19.46L20.28 19.55L20.31 19.62L20.35 19.70L20.39 19.78L20.43 19.85L20.48 19.92L20.53 19.98L20.59 20.04L20.65 20.10L20.72 20.16L20.79 20.20L20.86 20.25L20.93 20.29L21.01 20.32L21.09 20.35L21.17 20.38L21.25 20.40L21.34 20.41L21.42 20.42L21.51 20.42L21.59 20.42L21.68 20.41L21.76 20.40L21.84 20.38L21.92 20.35L22.00 20.32L22.08 20.29L22.15 20.25L22.23 20.20L22.29 20.16L22.36 20.10L22.42 20.04L22.48 19.98L22.53 19.92L22.58 19.85L22.63 19.78L22.67 19.70L22.70 19.62L22.73 19.55L22.76 19.46L22.78 19.38L22.79 19.30L22.80 19.21L22.80 19.13L22.80 19.04L22.79 18.96L22.78 18.88L22.76 18.79L22.73 18.71Z"), fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd)
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
