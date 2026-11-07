package io.github.mustypit.rifttracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
    val confirm by vm.confirmConquer.collectAsState()
    val coin by vm.coin.collectAsState()
    val game = match.games.lastOrNull()
    val current = MatchRules.current(match)
    val decided = MatchRules.isDecided(match)

    // The phone lies on the table for a whole game: keep the screen on.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    Box(Modifier.fillMaxSize().background(C.Ink)) {
        Column(Modifier.fillMaxSize()) {
            PlayerHalf(match, game, Side.OPP, enabled = current != null, onPoint = { vm.addPoint(Side.OPP, it) }, modifier = Modifier.weight(1f).rotate(180f))
            MiddleBar(vm, match, game, coin)
            PlayerHalf(match, game, Side.ME, enabled = current != null, onPoint = { vm.addPoint(Side.ME, it) }, modifier = Modifier.weight(1f))
        }
        when {
            current != null -> {}
            game != null && game.isOver -> GameOverCard(match, game, decided, onUndo = vm::undo, onNext = { vm.finishMatch() }, onNextGame = { vm.chooseFirst(it) })
            else -> FirstChooser(match, onChoose = vm::chooseFirst)
        }
    }

    if (confirm != null) {
        AlertDialog(
            onDismissRequest = { vm.answerConquer(false) },
            title = { Text("Winning point by conquering?") },
            text = { Text("The final point from a conquer only counts if ${if (confirm == Side.ME) "you" else match.nameOf(Side.OPP)} also scored every other battlefield this turn.") },
            confirmButton = { TextButton(onClick = { vm.answerConquer(true) }) { Text("Count it") } },
            dismissButton = { TextButton(onClick = { vm.answerConquer(false) }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun PlayerHalf(match: Match, game: Game?, side: Side, enabled: Boolean, onPoint: (PointKind) -> Unit, modifier: Modifier) {
    val score = game?.score(side) ?: 0
    val ink = C.sideInk(side)
    val legend = if (side == Side.ME) match.myLegend else match.oppLegend
    Box(modifier.fillMaxWidth().background(C.side(side)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LegendAvatar(legend, 28.dp, Modifier.padding(end = 8.dp), corner = 8.dp)
                Text("${match.nameOf(side)} · $legend".uppercase(), style = Caps, color = ink.copy(alpha = 0.75f))
                if (game?.wentFirst == side) {
                    Spacer(Modifier.width(8.dp))
                    Text("1ST", style = Caps.copy(fontSize = 10.sp), color = C.Yellow, modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(C.Ink).padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }
            Text(
                "$score",
                fontSize = 128.sp, fontWeight = FontWeight.Black, color = ink, letterSpacing = (-4).sp,
                modifier = Modifier.semantics { contentDescription = "${match.nameOf(side)} score $score" },
            )
            Text("/ ${GameRules.VICTORY_SCORE}", style = Caps, color = ink.copy(alpha = 0.55f))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.padding(top = 8.dp)) {
                repeat(GameRules.VICTORY_SCORE) { i ->
                    Box(Modifier.size(width = 22.dp, height = 9.dp).clip(RoundedCornerShape(3.dp)).background(if (i < score) ink.copy(alpha = 0.8f) else Color.Black.copy(alpha = 0.16f)))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 14.dp)) {
                Box(
                    Modifier.size(42.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.15f))
                        .clickable(enabled = enabled) { onPoint(PointKind.CORRECTION) }
                        .semantics { contentDescription = "${match.nameOf(side)} minus one" },
                    contentAlignment = Alignment.Center,
                ) { Text("−", fontSize = 22.sp, fontWeight = FontWeight.Black, color = ink) }
                listOf(PointKind.CONQUER to "CONQUER", PointKind.HOLD to "HOLD", PointKind.ABILITY to "ABILITY").forEach { (kind, label) ->
                    Box(
                        Modifier.height(42.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = if (enabled) 0.9f else 0.5f))
                            .clickable(enabled = enabled) { onPoint(kind) }
                            .semantics { contentDescription = "${match.nameOf(side)} $label" }
                            .padding(horizontal = 13.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(label, fontSize = 12.sp, fontWeight = FontWeight.Black, color = C.Ink, letterSpacing = 0.5.sp) }
                }
            }
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
    Row(Modifier.fillMaxWidth().height(52.dp).background(C.Ink).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = vm::undo, enabled = game != null) { Icon(Icons.AutoMirrored.Filled.Undo, "Undo", tint = Color.White) }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            val label = when {
                coin != null -> "COIN: ${if (coin == Side.ME) "YOU GO" else match.nameOf(Side.OPP).uppercase() + " GOES"} FIRST"
                match.format == Format.BO3 -> "GAME ${match.games.size.coerceAtLeast(1)} · ${MatchRules.scoreLine(match)}"
                else -> "FIRST TO ${GameRules.VICTORY_SCORE}"
            }
            Text(label, style = Caps, color = C.Ink, modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(C.Yellow).padding(horizontal = 10.dp, vertical = 4.dp))
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
            Modifier.padding(24.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color.White).padding(22.dp),
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
private fun FirstChooser(match: Match, onChoose: (Side?) -> Unit) {
    val suggested = MatchRules.suggestedFirst(match)
    Overlay {
        Text(if (match.games.isEmpty()) "WHO GOES FIRST?" else "GAME ${match.games.size + 1}: WHO GOES FIRST?", style = Caps.copy(fontSize = 14.sp), color = C.Ink)
        if (suggested != null) {
            Text(
                "${match.nameOf(suggested)} lost the last game, so ${if (suggested == Side.ME) "you pick" else "they pick"}.",
                color = C.Muted, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        BigButton("YOU", C.Teal, C.TealInk) { onChoose(Side.ME) }
        Spacer(Modifier.height(10.dp))
        BigButton(match.nameOf(Side.OPP).uppercase(), C.Coral, C.CoralInk) { onChoose(Side.OPP) }
        Spacer(Modifier.height(10.dp))
        BigButton("FLIP A COIN", C.Ink, Color.White) { onChoose(null) }
    }
}

@Composable
private fun GameOverCard(match: Match, game: Game, decided: Boolean, onUndo: () -> Unit, onNext: () -> Unit, onNextGame: (Side?) -> Unit) {
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
        if (decided) BigButton("SAVE MATCH", C.Ink, Color.White, onClick = onNext)
        else Text("Next game: who goes first?", style = Caps, color = C.Muted)
        if (!decided) {
            val suggested = MatchRules.suggestedFirst(match)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BigButton("YOU" + if (suggested == Side.ME) " ★" else "", C.Teal, C.TealInk, Modifier.weight(1f)) { onNextGame(Side.ME) }
                BigButton(match.nameOf(Side.OPP).uppercase() + if (suggested == Side.OPP) " ★" else "", C.Coral, C.CoralInk, Modifier.weight(1f)) { onNextGame(Side.OPP) }
            }
            if (suggested != null) Text("★ lost the last game", color = C.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        }
        TextButton(onClick = onUndo, modifier = Modifier.padding(top = 6.dp)) { Text("Undo last point", color = C.Muted) }
    }
}
