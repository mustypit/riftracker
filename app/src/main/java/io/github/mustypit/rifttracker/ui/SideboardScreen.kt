package io.github.mustypit.rifttracker.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mustypit.rifttracker.game.Board
import io.github.mustypit.rifttracker.game.Card
import io.github.mustypit.rifttracker.game.Deck
import io.github.mustypit.rifttracker.game.DeckRules
import io.github.mustypit.rifttracker.game.Match
import io.github.mustypit.rifttracker.game.Side
import io.github.mustypit.rifttracker.game.Swaps

/**
 * Sideboarding between games of a best of 3: take cards out of the main deck, bring cards in from
 * the sideboard. For this match only (the saved deck isn't touched); the main deck must keep its size.
 */
@Composable
fun SideboardScreen(deck: Deck, match: Match, swaps: Swaps, onChange: (Swaps) -> Unit, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    Column(Modifier.fillMaxSize().background(C.Paper).pointerInput(Unit) { detectTapGestures { } }) {
        Column(Modifier.fillMaxWidth().background(C.Header).statusBarsPadding().padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 14.dp)) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
            Text("Sideboard", color = Color.White, fontWeight = FontWeight.Black, fontSize = 28.sp, modifier = Modifier.padding(start = 12.dp))
            Text("Game ${match.games.size + 1} · vs ${match.oppName.ifBlank { match.oppLegend.ifBlank { "Opponent" } }}", color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp, modifier = Modifier.padding(start = 12.dp))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Out · main deck size · in
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.Card).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("OUT", style = Caps, color = C.Muted)
                    RollingNumber(swaps.outCount) { Text("−$it", fontWeight = FontWeight.Black, fontSize = 18.sp, color = C.Ink) }
                }
                val size = deck.mainSize - swaps.outCount + swaps.inCount
                Row(Modifier.weight(1f).semantics(mergeDescendants = true) { contentDescription = "Main $size of ${deck.mainSize}" }, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
                    RollingNumber(size) { Text("$it", fontWeight = FontWeight.Black, fontSize = 26.sp, color = if (swaps.balanced) C.Ink else C.Red) }
                    Text("/${deck.mainSize}", color = C.Muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 4.dp))
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("IN", style = Caps, color = C.Muted)
                    RollingNumber(swaps.inCount) { Text("+$it", fontWeight = FontWeight.Black, fontSize = 18.sp, color = C.Ink) }
                }
            }
            Text("SIDEBOARD", style = Caps, color = C.Muted, modifier = Modifier.padding(top = 10.dp))
            DeckRules.entries(deck, Board.SIDE).forEach { (c, n) ->
                SwapRow(c, n, swaps.brought[c.slug] ?: 0, "in", onMore = { onChange(swaps.bringIn(deck, c.slug)) }, onLess = { onChange(swaps.undoIn(c.slug)) })
            }
            Text("MAIN DECK", style = Caps, color = C.Muted, modifier = Modifier.padding(top = 10.dp))
            DeckRules.entries(deck).forEach { (c, n) ->
                SwapRow(c, n, swaps.taken[c.slug] ?: 0, "out", onMore = { onChange(swaps.takeOut(deck, c.slug)) }, onLess = { onChange(swaps.undoOut(c.slug)) })
            }
        }
        Column(Modifier.fillMaxWidth().background(C.Card).navigationBarsPadding().padding(16.dp)) {
            BigButton(
                if (swaps.balanced) "READY FOR GAME ${match.games.size + 1}" else "${swaps.inCount} IN · ${swaps.outCount} OUT",
                if (swaps.balanced) C.Solid else C.Track, if (swaps.balanced) C.OnSolid else C.Muted,
            ) { if (swaps.balanced) onClose() }
            if (!swaps.isEmpty()) Text("Back to the deck list", color = C.Muted, fontSize = 13.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally).clickable { onChange(Swaps()) }.padding(top = 10.dp))
        }
    }
}

/** A card with how many copies there are and how many are swapped ("in" from the side, "out" of the main deck). */
@Composable
private fun SwapRow(c: Card, n: Int, swapped: Int, verb: String, onMore: () -> Unit, onLess: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (swapped > 0) C.Accent.copy(alpha = 0.22f) else C.Card)
            .clickable(onClick = onMore).padding(start = 8.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.width(30.dp), contentAlignment = Alignment.Center) { c.energy?.let { EnergySymbol(it, 22.dp) } }
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(7.dp))) {
            CardImage(c, Modifier.fillMaxWidth().aspectRatio(744f / 1039f).offset(y = (-4).dp).graphicsLayer { scaleX = 2.1f; scaleY = 2.1f; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.12f) }, corner = 0.dp, showBanned = false)
        }
        Text(c.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = C.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text("$n", color = C.Muted, fontSize = 13.sp)
        Row(Modifier.clip(RoundedCornerShape(10.dp)).background(C.Track), verticalAlignment = Alignment.CenterVertically) {
            Text("−", color = if (swapped > 0) C.Ink else C.Line, fontWeight = FontWeight.Black, fontSize = 16.sp,
                modifier = Modifier.clickable(enabled = swapped > 0, onClick = onLess).semantics { contentDescription = "Less ${c.name} $verb" }.padding(horizontal = 10.dp, vertical = 4.dp))
            RollingNumber(swapped, Modifier.semantics(mergeDescendants = true) { contentDescription = "${c.name} $verb $swapped" }) {
                Text(if (it == 0) "0" else (if (verb == "in") "+$it" else "−$it"), color = C.Ink, fontWeight = FontWeight.Black, fontSize = 15.sp)
            }
            Text("+", color = if (swapped < n) C.Ink else C.Line, fontWeight = FontWeight.Black, fontSize = 16.sp,
                modifier = Modifier.clickable(enabled = swapped < n, onClick = onMore).semantics { contentDescription = "More ${c.name} $verb" }.padding(horizontal = 10.dp, vertical = 4.dp))
        }
    }
}
