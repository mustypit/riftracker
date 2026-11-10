package io.github.mustypit.rifttracker.ui

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mustypit.rifttracker.game.Format
import io.github.mustypit.rifttracker.game.MatchRules
import io.github.mustypit.rifttracker.game.Outcome
import io.github.mustypit.rifttracker.game.Side

enum class Pick { MINE, THEIRS }

/** The match fields both setup screens share. Legends come from the library via [onPick]. */
@Composable
private fun MatchFields(
    format: Format, onFormat: (Format) -> Unit, my: String, deck: io.github.mustypit.rifttracker.game.Deck?, hasDecks: Boolean,
    onDeck: () -> Unit, opp: String, onPick: (Pick) -> Unit,
) {
    SectionLabel("Format")
    Segmented(Format.entries, format, { it.label }, onFormat, Modifier.padding(horizontal = 20.dp))
    SectionLabel("You")
    // With saved decks, you pick a deck (which sets the legend); "Just a legend" is in the picker.
    if (hasDecks) DeckSlot(deck, my, onDeck) else LegendSlot("Your legend", my, onClick = { onPick(Pick.MINE) })
    SectionLabel("Opponent")
    LegendSlot("Their legend", opp, onClick = { onPick(Pick.THEIRS) })
}

/** The legend name the match stores for a deck: the legend library's name for its champion. */
fun legendNameOf(d: io.github.mustypit.rifttracker.game.Deck): String =
    io.github.mustypit.rifttracker.game.DeckRules.legendOf(d)?.let { io.github.mustypit.rifttracker.game.DeckRules.championTag(it) }
        ?.let { tag -> io.github.mustypit.rifttracker.game.Legends.byName(tag)?.name ?: tag }.orEmpty()

@Composable
private fun DeckSlot(deck: io.github.mustypit.rifttracker.game.Deck?, legend: String, onClick: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val art = legend.takeIf { it.isNotBlank() }?.let { l -> remember(l) { LegendArt.get(context, io.github.mustypit.rifttracker.game.Legends.idOf(l)) } }
    Box(
        Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(72.dp).clip(RoundedCornerShape(16.dp)).background(C.Card)
            .border(1.5.dp, if (deck == null && legend.isBlank()) C.Line else C.Ink, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick).semantics(mergeDescendants = true) { contentDescription = "Your deck" },
    ) {
        art?.let { androidx.compose.foundation.Image(it, null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, alignment = androidx.compose.ui.BiasAlignment(0f, -0.4f), modifier = Modifier.fillMaxHeight().fillMaxWidth(0.6f).align(Alignment.CenterEnd)) }
        Box(Modifier.matchParentSize().background(androidx.compose.ui.graphics.Brush.horizontalGradient(0.35f to C.Card, 1f to C.Card.copy(alpha = 0.1f))))
        Column(Modifier.align(Alignment.CenterStart).padding(horizontal = 14.dp)) {
            Text(if (deck != null) "YOUR DECK" else "YOUR DECK OR LEGEND", style = Caps.copy(fontSize = 11.sp), color = C.Muted)
            Text(deck?.name ?: legend.ifBlank { "Choose" }, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = C.Ink, maxLines = 1)
            if (deck != null) Text(legend, color = C.Muted, fontSize = 12.sp)
        }
    }
}

/** Choose a deck: recently played first, then the rest; "Just a legend" for playing without one. */
@Composable
private fun DeckPicker(vm: MainViewModel, onDeck: (io.github.mustypit.rifttracker.game.Deck) -> Unit, onJustLegend: () -> Unit, onClose: () -> Unit) {
    androidx.activity.compose.BackHandler(onBack = onClose)
    val decks by vm.decks.collectAsState()
    val history by vm.history.collectAsState()
    val recentIds = history.sortedByDescending { it.startedAt }.mapNotNull { it.deck?.id }.distinct().filter { id -> decks.any { it.id == id } }.take(3)
    val recent = recentIds.mapNotNull { id -> decks.firstOrNull { it.id == id } }
    val rest = decks.filter { it.id !in recentIds }
    Column(Modifier.fillMaxSize().background(C.Paper).verticalScroll(rememberScrollState())) {
        HeaderBlock(onBack = onClose) { Text("Your deck", color = Color.White, fontWeight = FontWeight.Black, fontSize = 26.sp) }
        @Composable fun row(d: io.github.mustypit.rifttracker.game.Deck) {
            val ms = io.github.mustypit.rifttracker.stats.Stats.deckMatches(history, d.id)
            val problem = io.github.mustypit.rifttracker.game.DeckRules.problems(d).firstOrNull()
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.Card)
                    .clickable { onDeck(d) }.semantics(mergeDescendants = true) { contentDescription = "Pick deck ${d.name}" }.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LegendAvatar(legendNameOf(d), 40.dp)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(d.name, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = C.Ink)
                    Text(if (ms.isEmpty()) legendNameOf(d) else "${legendNameOf(d)} · ${io.github.mustypit.rifttracker.stats.Stats.record(ms).line}", color = C.Muted, fontSize = 12.sp)
                }
                if (problem != null) Text(problem, color = C.Red, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
            }
        }
        if (recent.isNotEmpty()) { SectionLabel("Recent"); recent.forEach { row(it) } }
        if (rest.isNotEmpty()) { SectionLabel(if (recent.isEmpty()) "Decks" else "All decks"); rest.forEach { row(it) } }
        Text(
            "Just a legend", fontWeight = FontWeight.ExtraBold, color = C.Muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).border(1.5.dp, C.Line, RoundedCornerShape(14.dp)).clickable(onClick = onJustLegend).padding(16.dp),
        )
    }
}

/** The legend grid over a setup screen while choosing. */
@Composable
private fun PickerOverlay(vm: MainViewModel, pick: Pick?, my: String, opp: String, onPicked: (Pick, String) -> Unit, onClose: () -> Unit) {
    if (pick == null) return
    val history by vm.history.collectAsState()
    val recent = remember(history, pick) { history.map { if (pick == Pick.MINE) it.myLegend else it.oppLegend } }
    LegendPicker(
        title = if (pick == Pick.MINE) "Your legend" else "Their legend",
        recentNames = recent,
        selected = if (pick == Pick.MINE) my else opp,
        onPick = { onPicked(pick, it); onClose() },
        onClose = onClose,
    )
}

/** Play tab when no match is running. Fields are screens, not dialogs (text-field dialogs never idle in Robolectric). */
@Composable
fun NewMatchScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val start by vm.setup.collectAsState()
    var format by rememberSaveable { mutableStateOf(start.format) }
    var my by rememberSaveable { mutableStateOf(start.myLegend) }
    var deckId by rememberSaveable { mutableStateOf(start.deckId) }
    var opp by rememberSaveable { mutableStateOf(start.oppLegend) }
    var pick by rememberSaveable { mutableStateOf<Pick?>(null) }
    var pickingDeck by remember { mutableStateOf(false) }
    val decks by vm.decks.collectAsState()
    val deck = deckId?.let { id -> decks.firstOrNull { it.id == id } }
    val ready = my.isNotBlank() && opp.isNotBlank()

    Box(modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding()) {
        HeaderBlock {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("New match", color = Color.White, fontWeight = FontWeight.Black, fontSize = 30.sp)
                }
                androidx.compose.material3.IconButton(onClick = { vm.push(Screen.Settings) }) {
                    androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Default.Settings, "Settings", tint = Color.White)
                }
            }
        }
        MatchFields(format, { format = it }, my, deck, decks.isNotEmpty(), { pickingDeck = true }, opp, { pick = it })
        Spacer(Modifier.height(20.dp))
        Box(Modifier.padding(horizontal = 20.dp)) {
            BigButton(if (ready) "START" else "PICK BOTH LEGENDS", if (ready) C.Solid else C.Track, if (ready) C.OnSolid else C.Muted) {
                if (ready) vm.startMatch(MatchSetup(format, my, opp, deck?.id))
            }
        }
        Text(
            "Log a past match",
            color = C.Muted, fontWeight = FontWeight.Bold, fontSize = 14.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally).clickable { vm.push(Screen.LogMatch) }.padding(16.dp),
        )
    }
    PickerOverlay(vm, pick, my, opp, onPicked = { p, l -> if (p == Pick.MINE) { my = l; deckId = null } else opp = l }, onClose = { pick = null })
    if (pickingDeck) DeckPicker(vm, onDeck = { d -> deckId = d.id; my = legendNameOf(d); pickingDeck = false }, onJustLegend = { pickingDeck = false; pick = Pick.MINE }, onClose = { pickingDeck = false })
    }
}

/** Enter a past match: the same fields plus each game's result and who went first. */
@Composable
fun LogMatchScreen(vm: MainViewModel) {
    var format by rememberSaveable { mutableStateOf(Format.BO3) }
    var my by rememberSaveable { mutableStateOf("") }
    var deckId by rememberSaveable { mutableStateOf<Long?>(null) }
    var pickingDeck by remember { mutableStateOf(false) }
    val decks by vm.decks.collectAsState()
    val deck = deckId?.let { id -> decks.firstOrNull { it.id == id } }
    var opp by rememberSaveable { mutableStateOf("") }
    val results = remember { mutableStateListOf<Outcome?>(null, null, null) }
    val firsts = remember { mutableStateListOf<Side?>(null, null, null) }
    val chosen = results.take(format.maxGames).takeWhile { it != null }.filterNotNull()
    val valid = my.isNotBlank() && opp.isNotBlank() && MatchRules.validLog(format, chosen)
    var pick by rememberSaveable { mutableStateOf<Pick?>(null) }

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding()) {
        HeaderBlock(onBack = { vm.back() }) {
            Text("Log a match", color = Color.White, fontWeight = FontWeight.Black, fontSize = 30.sp)
        }
        MatchFields(format, { format = it }, my, deck, decks.isNotEmpty(), { pickingDeck = true }, opp, { pick = it })
        SectionLabel("Games")
        // Show a game row once the games before it leave the match undecided.
        val rows = (1..format.maxGames).filter { n -> n == 1 || (chosen.size >= n - 1 && !MatchRules.validLog(format, chosen.take(n - 1))) }
        rows.forEach { n ->
            val i = n - 1
            Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.Card).padding(12.dp)) {
                Text("GAME $n", style = Caps, color = C.Ink)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
                    listOf(Outcome.WIN to "Won", Outcome.LOSS to "Lost", Outcome.DRAW to "Draw").forEach { (o, label) ->
                        ResultPick(label, C.outcome(o), results[i] == o, Modifier.weight(1f)) {
                            results[i] = o
                            for (j in i + 1 until 3) if (MatchRules.validLog(format, results.take(i + 1).filterNotNull())) results[j] = null
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
                    Text("First:", color = C.Muted, fontSize = 13.sp)
                    Chip("You", firsts[i] == Side.ME) { firsts[i] = if (firsts[i] == Side.ME) null else Side.ME }
                    Chip("Them", firsts[i] == Side.OPP) { firsts[i] = if (firsts[i] == Side.OPP) null else Side.OPP }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Box(Modifier.padding(horizontal = 20.dp)) {
            BigButton("SAVE", if (valid) C.Solid else C.Track, if (valid) C.OnSolid else C.Muted) {
                if (valid) vm.logMatch(MatchSetup(format, my, opp, deck?.id), chosen, firsts.take(chosen.size))
            }
        }
        Spacer(Modifier.height(24.dp))
    }
    PickerOverlay(vm, pick, my, opp, onPicked = { p, l -> if (p == Pick.MINE) { my = l; deckId = null } else opp = l }, onClose = { pick = null })
    if (pickingDeck) DeckPicker(vm, onDeck = { d -> deckId = d.id; my = legendNameOf(d); pickingDeck = false }, onJustLegend = { pickingDeck = false; pick = Pick.MINE }, onClose = { pickingDeck = false })
    }
}

@Composable
private fun ResultPick(label: String, color: Color, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(40.dp).clip(RoundedCornerShape(12.dp)).background(if (selected) color else C.Soft).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontWeight = FontWeight.ExtraBold, color = if (selected) C.Black else C.Ink) }
}
