package io.github.mustypit.rifttracker.ui

import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import io.github.mustypit.rifttracker.game.Battlefields
import io.github.mustypit.rifttracker.game.Legends
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mustypit.rifttracker.game.Format
import io.github.mustypit.rifttracker.game.Game
import io.github.mustypit.rifttracker.game.GameRules
import io.github.mustypit.rifttracker.game.Match
import io.github.mustypit.rifttracker.game.MatchRules
import io.github.mustypit.rifttracker.game.Outcome
import io.github.mustypit.rifttracker.game.PointKind
import io.github.mustypit.rifttracker.game.Side
import io.github.mustypit.rifttracker.game.formatDuration
import kotlinx.coroutines.delay

private fun Match.nameOf(side: Side) = if (side == Side.ME) "You" else oppName.ifBlank { "Opponent" }

/**
 * The table view: phone flat between the players, the opponent's half upside down.
 * Before each game it asks who goes first; after each game it shows the result.
 */
@Composable
fun CounterScreen(vm: MainViewModel, match: Match) {
    val coin by vm.coin.collectAsState()
    val game = match.games.lastOrNull()
    val current = MatchRules.current(match)
    val decided = MatchRules.isDecided(match)

    // The phone lies on the table for a whole game: keep the screen on.
    // Full screen while counting: hide the status and navigation bars (a swipe from the edge shows
    // them for a moment). Everything comes back when the counter closes.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        val window = generateSequence(view.context) { (it as? android.content.ContextWrapper)?.baseContext }
            .filterIsInstance<android.app.Activity>().firstOrNull()?.window
        val bars = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, view) }
        bars?.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        bars?.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        onDispose {
            view.keepScreenOn = false
            bars?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
    }

    val darkness by vm.settings.darkness.collectAsState()
    var panels by remember { mutableStateOf(setOf<Side>()) }
    Box(Modifier.fillMaxSize().background(C.Black)) {
        Column(Modifier.fillMaxSize()) {
            for (side in listOf(Side.OPP, Side.ME)) {
                if (side == Side.ME) MiddleBar(vm, match, game, coin)
                PlayerHalf(
                    match, game, side, enabled = current != null, onPoint = { vm.addPoint(side, it) },
                    modifier = Modifier.weight(1f).let { if (side == Side.OPP) it.rotate(180f) else it },
                    darkness = darkness,
                    panelOpen = side in panels,
                    onPanelChange = { open -> panels = if (open) panels + side else panels - side },
                    onXp = { vm.adjustXp(side, it) },
                    onRemovePoint = { vm.removePointAt(side, it) },
                )
            }
        }
        // Battlefields for the next game, picked on the card before it starts (optional).
        val pending by vm.pendingBattlefields.collectAsState()
        var picking by remember { mutableStateOf<Side?>(null) }
        val battlefieldSlots: @Composable () -> Unit = {
            if (Battlefields.all.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                BattlefieldSlot("Your battlefield", pending[Side.ME], onClick = { picking = Side.ME })
                Spacer(Modifier.height(8.dp))
                BattlefieldSlot("${match.nameOf(Side.OPP)}'s battlefield", pending[Side.OPP], onClick = { picking = Side.OPP })
            }
        }
        when {
            current != null -> {}
            game != null && game.isOver -> GameOverCard(match, game, decided, onUndo = vm::undo, onNext = { vm.finishMatch() }, onNextGame = { vm.chooseFirst(it) }, battlefieldSlots)
            else -> FirstChooser(match, onChoose = vm::chooseFirst, battlefieldSlots)
        }
        picking?.let { side ->
            val history by vm.history.collectAsState()
            BattlefieldPicker(
                title = if (side == Side.ME) "Your battlefield" else "Their battlefield",
                recentNames = history.flatMap { m -> m.games.reversed().map { it.battlefieldOf(side) } },
                selected = pending[side],
                onPick = { vm.pickPendingBattlefield(side, it); picking = null },
                onClose = { picking = null },
            )
        }
    }
}

/** Dark or white text, whichever reads better on [c]. */
private fun inkOn(c: Color) = if (c.luminance() > 0.4f) C.Black else Color.White

/** Two colours for a player: the legend's domains, or the seat colour when they're unknown. */
private fun playerColors(legendName: String, side: Side): Pair<Color, Color> {
    val d = Legends.byName(legendName)?.domains.orEmpty()
    return if (d.size >= 2) Color(d[0].argb) to Color(d[1].argb) else C.side(side) to C.side(side)
}

/**
 * One player's half (design "B"): the legend's art with a dark veil that's strongest behind the
 * number, white digits, and round point buttons filled with the legend's domain colour.
 * Without art: a dark background washed with the domain colours.
 */
@Composable
private fun PlayerHalf(
    match: Match, game: Game?, side: Side, enabled: Boolean, onPoint: (PointKind) -> Unit, modifier: Modifier,
    darkness: Float, panelOpen: Boolean, onPanelChange: (Boolean) -> Unit, onXp: (Int) -> Unit, onRemovePoint: (Int) -> Unit,
) {
    val score = game?.score(side) ?: 0
    val legendName = if (side == Side.ME) match.myLegend else match.oppLegend
    val context = LocalContext.current
    val art = remember(legendName) { Legends.byName(legendName)?.let { LegendArt.get(context, it.id) } }
    val (c1, c2) = playerColors(legendName, side)
    Box(modifier.fillMaxWidth().background(Color(0xFF0C0C0C)).clipToBounds()) {
        if (art != null) {
            Image(art, null, contentScale = ContentScale.Crop, alignment = BiasAlignment(0f, -0.4f), modifier = Modifier.matchParentSize())
        } else {
            Box(Modifier.matchParentSize().background(Brush.linearGradient(listOf(c1.copy(alpha = 0.6f), c2.copy(alpha = 0.6f)))))
        }
        // The veil: darkest behind the score, lighter towards the edges so the art shows (Settings).
        Box(Modifier.matchParentSize().legendVeil(darkness))
        // Top (the inner edge, by the middle bar) leaves room for the panel handle; the bottom (the
        // outer edge) keeps the buttons clear of the phone's home bar, or the camera notch on the
        // opponent's side.
        Column(Modifier.fillMaxSize().padding(top = 36.dp, bottom = OUTER_EDGE_MARGIN), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
            Spacer(Modifier.height(2.dp))
            // Tapping the score takes one point off (the middle bar then offers to undo it).
            Text(
                "$score",
                style = TextStyle(fontSize = 140.sp, fontWeight = FontWeight.Black, color = Color.White, letterSpacing = (-5).sp, shadow = Shadow(Color.Black.copy(alpha = 0.5f), blurRadius = 28f)),
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .clickable(enabled = enabled && score > 0, onClickLabel = "take one point off") { onPoint(PointKind.CORRECTION) }
                    .padding(horizontal = 18.dp)
                    .semantics { contentDescription = "${match.nameOf(side)} score $score" },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                PointButton(RiftIcons.Conquer, "${match.nameOf(side)} CONQUER", c1, enabled) { onPoint(PointKind.CONQUER) }
                PointButton(RiftIcons.Hold, "${match.nameOf(side)} HOLD", c1, enabled) { onPoint(PointKind.HOLD) }
                PointButton(RiftIcons.Ability, "${match.nameOf(side)} ABILITY", c1, enabled) { onPoint(PointKind.ABILITY) }
            }
        }
        SideTrack(
            game?.let { GameRules.scoredKinds(it, side) }.orEmpty(), c1 to c2,
            // Between the panel handle (inner edge) and the buttons, so it never runs into them.
            Modifier.align(Alignment.TopEnd).padding(top = 46.dp, bottom = OUTER_EDGE_MARGIN + 96.dp, end = 8.dp),
            name = match.nameOf(side), enabled = enabled, onRemove = onRemovePoint,
        )
        // XP at a glance when the panel is closed.
        val xp = game?.xpOf(side) ?: 0
        if (xp > 0 && !panelOpen) {
            Text(
                "XP $xp", style = Caps, color = Color.White,
                modifier = Modifier.align(Alignment.TopStart).padding(start = 14.dp, top = 42.dp).clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 10.dp, vertical = 5.dp).semantics { contentDescription = "${match.nameOf(side)} XP $xp" },
            )
        }
        PullDownPanel(
            open = panelOpen, onOpenChange = onPanelChange, color = c1, name = match.nameOf(side),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            CounterRow("XP", xp, "${match.nameOf(side)} XP", enabled = game != null, onChange = onXp)
        }
    }
}

/** Space kept free at a half's outer edge: the home bar (bottom) or camera notch (top). */
private val OUTER_EDGE_MARGIN = 58.dp

/**
 * The hidden panel of a half, Lotus-style: a handle at the half's inner edge, right by the middle
 * bar. Drag from the centre of the screen towards your edge (or tap the handle) to open it; it
 * slides out from the centre. Drag back towards the centre, or tap the handle, to close.
 * Holds extra counters (XP for now). Directions are in the half's own frame, so the opponent's
 * upside-down half works the same way for them.
 */
@Composable
private fun PullDownPanel(open: Boolean, onOpenChange: (Boolean) -> Unit, color: Color, name: String, modifier: Modifier, content: @Composable () -> Unit) {
    val drag = Modifier.draggable(
        orientation = Orientation.Vertical,
        state = rememberDraggableState { },
        onDragStopped = { velocity -> if (velocity > 50f) onOpenChange(true) else if (velocity < -50f) onOpenChange(false) },
    )
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        AnimatedVisibility(open, enter = expandVertically(expandFrom = Alignment.Top), exit = shrinkVertically(shrinkTowards = Alignment.Top)) {
            Column(
                Modifier.fillMaxWidth().then(drag).background(Color(0xF2141414))
                    .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 6.dp),
            ) { content() }
        }
        // The handle: a wide strip so it's easy to grab, with a pill showing where to pull.
        Box(
            Modifier.fillMaxWidth().height(34.dp).then(drag)
                .clickable(onClickLabel = if (open) "close" else "open") { onOpenChange(!open) }
                .semantics { contentDescription = if (open) "Hide $name extras" else "Show $name extras" },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(width = 64.dp, height = 6.dp).clip(RoundedCornerShape(3.dp))
                    .background(if (open) color else Color.White.copy(alpha = 0.65f)),
            )
        }
    }
}

/** A labelled counter with − and + buttons. */
@Composable
private fun CounterRow(label: String, value: Int, description: String, enabled: Boolean, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = Caps.copy(fontSize = 15.sp), color = Color.White, modifier = Modifier.weight(1f))
        RoundStep("−", "$description minus", enabled && value > 0) { onChange(-1) }
        Text(
            "$value", color = Color.White, fontWeight = FontWeight.Black, fontSize = 34.sp, textAlign = TextAlign.Center,
            modifier = Modifier.width(72.dp).semantics { contentDescription = "$description $value" },
        )
        RoundStep("+", "$description plus", enabled) { onChange(+1) }
    }
}

@Composable
private fun RoundStep(text: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(alpha = if (enabled) 0.16f else 0.06f))
            .clickable(enabled = enabled, onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Text(text, color = Color.White.copy(alpha = if (enabled) 1f else 0.4f), fontWeight = FontWeight.Black, fontSize = 24.sp) }
}

/** Round icon button tinted with the player's colour. */
@Composable
private fun PointButton(icon: ImageVector, description: String, color: Color, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(78.dp).clip(CircleShape).background(color.copy(alpha = if (enabled) 0.9f else 0.35f))
            .border(1.5.dp, Color.White.copy(alpha = 0.25f), CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(38.dp)) }
}

/**
 * The progress track down the outer edge (design "C"): a slim glowing bar that fills smoothly from
 * the bottom in the legend's two domain colours, with each point's icon floating beside it at its
 * place on the bar. Tapping an icon takes that point off (the middle bar offers to undo it).
 */
@Composable
private fun SideTrack(kinds: List<PointKind>, colors: Pair<Color, Color>, modifier: Modifier, name: String, enabled: Boolean, onRemove: (Int) -> Unit) {
    val n = GameRules.VICTORY_SCORE
    val fill by animateFloatAsState((kinds.size.toFloat() / n).coerceIn(0f, 1f), animationSpec = tween(450), label = "track fill")
    val (c1, c2) = colors
    BoxWithConstraints(
        modifier.width(50.dp).fillMaxHeight()
            .semantics { contentDescription = "${kinds.size} of $n points" },
    ) {
        val h = maxHeight
        // The bar: a slim grey groove for the part still to score, and the scored part as a wider
        // gradient (legend's domain colours) that spills over the groove's edges. No separate glow.
        Box(Modifier.align(Alignment.BottomEnd).padding(end = 7.dp).size(width = 8.dp, height = h).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.16f)))
        if (fill > 0f) {
            Box(
                Modifier.align(Alignment.BottomEnd).padding(end = 4.dp).size(width = 14.dp, height = h * fill)
                    .clip(RoundedCornerShape(7.dp)).background(Brush.verticalGradient(listOf(c2, c1))),
            )
        }
        // The points: an icon beside the bar at each point's place, oldest at the bottom.
        kinds.forEachIndexed { i, kind ->
            val icon = when (kind) { PointKind.CONQUER -> RiftIcons.Conquer; PointKind.HOLD -> RiftIcons.Hold; else -> RiftIcons.Ability }
            val y = h * (1f - (i + 0.5f) / n) - 13.dp
            Box(
                Modifier.offset(y = y).align(Alignment.TopStart).padding(start = 2.dp).size(26.dp).clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f)).border(1.dp, c1.copy(alpha = 0.7f), CircleShape)
                    .clickable(enabled = enabled, onClickLabel = "remove this point") { onRemove(i) }
                    .semantics { contentDescription = "$name point ${i + 1}" },
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(14.dp)) }
        }
    }
}

@Composable
private fun MiddleBar(vm: MainViewModel, match: Match, game: Game?, coin: Side?) {
    var menu by remember { mutableStateOf(false) }
    var confirmAbandon by remember { mutableStateOf(false) }
    // Ticks once a second while a game runs.
    var now by remember { mutableLongStateOf(AppGraph.clock()) }
    val running = game != null && !game.isOver
    LaunchedEffect(running, game?.startedAt) {
        now = AppGraph.clock()
        while (running) { delay(1000); now = AppGraph.clock() }
    }
    Row(Modifier.fillMaxWidth().height(52.dp).background(C.Black).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = vm::undo, enabled = game != null) { Icon(Icons.AutoMirrored.Filled.Undo, "Undo", tint = Color.White) }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            // After a tap on a score took a point off, the pill offers to undo it for a few seconds.
            val tick = maxOf(now, AppGraph.clock())
            val removed by vm.lastRemoved.collectAsState()
            val removedSide = removed?.takeIf { tick - it.second.at < 4000 }?.first
            val correctionSide = GameRules.recentCorrection(game, tick)?.side ?: removedSide
            val correction = correctionSide
            val label = when {
                correctionSide != null -> "−1 ${match.nameOf(correctionSide).uppercase()} · TAP TO UNDO"
                coin != null -> "COIN: ${if (coin == Side.ME) "YOU GO" else match.nameOf(Side.OPP).uppercase() + " GOES"} FIRST"
                match.format == Format.BO3 -> "GAME ${match.games.size.coerceAtLeast(1)} · ${MatchRules.scoreLine(match)}"
                else -> "FIRST TO ${GameRules.VICTORY_SCORE}"
            }
            Text(
                label, style = Caps, color = C.Black,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(if (correction != null) Color.White else C.Yellow)
                    .clickable(enabled = correction != null) { vm.undo() }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        Text(
            formatDuration(game?.durationMs(maxOf(now, AppGraph.clock())) ?: 0), // also refreshed by any redraw
            color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp,
            modifier = Modifier.semantics { contentDescription = "Game time" },
        )
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Match options", tint = Color.White) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("End game as a draw") }, enabled = running, onClick = { menu = false; vm.drawGame() })
                DropdownMenuItem(text = { Text("Abandon match") }, onClick = { menu = false; confirmAbandon = true })
                DropdownMenuItem(text = { Text("Settings") }, onClick = { menu = false; vm.push(Screen.Settings) })
            }
        }
    }
    if (confirmAbandon) {
        AlertDialog(
            onDismissRequest = { confirmAbandon = false },
            title = { Text("Abandon this match?") },
            text = { Text("It won't be saved to your history.") },
            confirmButton = { TextButton(onClick = { confirmAbandon = false; vm.abandonMatch() }) { Text("Abandon") } },
            dismissButton = { TextButton(onClick = { confirmAbandon = false }) { Text("Keep playing") } },
        )
    }
}

/** White card over the table, between games. */
@Composable
private fun Overlay(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).clickable(enabled = true, onClick = {}), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(24.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(C.Card).padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { content() }
    }
}

@Composable
fun BigButton(text: String, color: Color, ink: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(16.dp)).background(color).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = ink, fontWeight = FontWeight.Black, fontSize = 15.sp, letterSpacing = 0.6.sp, textAlign = TextAlign.Center) }
}

@Composable
private fun FirstChooser(match: Match, onChoose: (Side?) -> Unit, battlefieldSlots: @Composable () -> Unit) {
    val suggested = MatchRules.suggestedFirst(match)
    Overlay {
        Text(if (match.games.isEmpty()) "WHO GOES FIRST?" else "GAME ${match.games.size + 1}: WHO GOES FIRST?", style = Caps.copy(fontSize = 14.sp), color = C.Ink)
        if (suggested != null) {
            Text(
                "${match.nameOf(suggested)} lost the last game, so ${if (suggested == Side.ME) "you pick" else "they pick"}.",
                color = C.Muted, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp),
            )
        }
        battlefieldSlots()
        Spacer(Modifier.height(16.dp))
        BigButton("YOU", playerColors(match.myLegend, Side.ME).first, inkOn(playerColors(match.myLegend, Side.ME).first)) { onChoose(Side.ME) }
        Spacer(Modifier.height(10.dp))
        BigButton(match.nameOf(Side.OPP).uppercase(), playerColors(match.oppLegend, Side.OPP).first, inkOn(playerColors(match.oppLegend, Side.OPP).first)) { onChoose(Side.OPP) }
        Spacer(Modifier.height(10.dp))
        BigButton("FLIP A COIN", C.Solid, C.OnSolid) { onChoose(null) }
    }
}

@Composable
private fun GameOverCard(match: Match, game: Game, decided: Boolean, onUndo: () -> Unit, onNext: () -> Unit, onNextGame: (Side?) -> Unit, battlefieldSlots: @Composable () -> Unit) {
    val n = match.games.size
    val title = when (game.outcome) {
        Outcome.WIN -> "YOU WIN" + if (match.format == Format.BO3) " GAME $n" else ""
        Outcome.LOSS -> "${match.nameOf(Side.OPP).uppercase()} WINS" + if (match.format == Format.BO3) " GAME $n" else ""
        else -> "DRAW" + if (match.format == Format.BO3) " IN GAME $n" else ""
    }
    Overlay {
        Text(title, fontWeight = FontWeight.Black, fontSize = 24.sp, color = C.Ink, textAlign = TextAlign.Center)
        Text(
            "${game.score(Side.ME)}–${game.score(Side.OPP)} · ${formatDuration(game.durationMs(game.endedAt ?: 0))}",
            color = C.Muted, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp),
        )
        if (match.format == Format.BO3) {
            val r = MatchRules.result(match)
            val line = if (decided) "Match ${if (r == Outcome.WIN) "won" else if (r == Outcome.LOSS) "lost" else "drawn"} ${MatchRules.scoreLine(match)}" else "Match ${MatchRules.scoreLine(match)}"
            Text(line, fontWeight = FontWeight.ExtraBold, color = C.Ink, modifier = Modifier.padding(top = 10.dp))
        }
        Spacer(Modifier.height(18.dp))
        if (decided) BigButton("SAVE MATCH", C.Solid, C.OnSolid, onClick = onNext)
        else Text("Next game: who goes first?", style = Caps, color = C.Muted)
        if (!decided) {
            val suggested = MatchRules.suggestedFirst(match)
            battlefieldSlots()
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BigButton("YOU" + if (suggested == Side.ME) " ★" else "", playerColors(match.myLegend, Side.ME).first, inkOn(playerColors(match.myLegend, Side.ME).first), Modifier.weight(1f)) { onNextGame(Side.ME) }
                BigButton(match.nameOf(Side.OPP).uppercase() + if (suggested == Side.OPP) " ★" else "", playerColors(match.oppLegend, Side.OPP).first, inkOn(playerColors(match.oppLegend, Side.OPP).first), Modifier.weight(1f)) { onNextGame(Side.OPP) }
            }
            if (suggested != null) Text("★ lost the last game", color = C.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        }
        TextButton(onClick = onUndo, modifier = Modifier.padding(top = 6.dp)) { Text("Undo last point", color = C.Muted) }
    }
}
