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
private fun MatchFields(format: Format, onFormat: (Format) -> Unit, my: String, name: String, onName: (String) -> Unit, opp: String, onPick: (Pick) -> Unit) {
    SectionLabel("Format")
    Segmented(Format.entries, format, { it.label }, onFormat, Modifier.padding(horizontal = 20.dp))
    SectionLabel("You")
    LegendSlot("Your legend", my, onClick = { onPick(Pick.MINE) })
    SectionLabel("Opponent")
    RiftTextField("Their name (optional)", name, onName, Modifier.padding(horizontal = 20.dp))
    Spacer(Modifier.height(10.dp))
    LegendSlot("Their legend", opp, onClick = { onPick(Pick.THEIRS) })
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
    var name by rememberSaveable { mutableStateOf(start.oppName) }
    var opp by rememberSaveable { mutableStateOf(start.oppLegend) }
    var pick by rememberSaveable { mutableStateOf<Pick?>(null) }
    val ready = my.isNotBlank() && opp.isNotBlank()

    Box(modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding()) {
        HeaderBlock {
            Text("NEW MATCH", style = Caps, color = C.Yellow)
            Text("Who's playing?", color = Color.White, fontWeight = FontWeight.Black, fontSize = 30.sp)
        }
        MatchFields(format, { format = it }, my, name, { name = it }, opp, { pick = it })
        Spacer(Modifier.height(20.dp))
        Box(Modifier.padding(horizontal = 20.dp)) {
            BigButton(if (ready) "START" else "PICK BOTH LEGENDS", if (ready) C.Ink else C.Line, if (ready) Color.White else C.Muted) {
                if (ready) vm.startMatch(MatchSetup(format, my, name, opp))
            }
        }
        Text(
            "Log a match you already played",
            color = C.Muted, fontWeight = FontWeight.Bold, fontSize = 14.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally).clickable { vm.push(Screen.LogMatch) }.padding(16.dp),
        )
    }
    PickerOverlay(vm, pick, my, opp, onPicked = { p, l -> if (p == Pick.MINE) my = l else opp = l }, onClose = { pick = null })
    }
}

/** Enter a past match: the same fields plus each game's result and who went first. */
@Composable
fun LogMatchScreen(vm: MainViewModel) {
    var format by rememberSaveable { mutableStateOf(Format.BO3) }
    var my by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var opp by rememberSaveable { mutableStateOf("") }
    val results = remember { mutableStateListOf<Outcome?>(null, null, null) }
    val firsts = remember { mutableStateListOf<Side?>(null, null, null) }
    val chosen = results.take(format.maxGames).takeWhile { it != null }.filterNotNull()
    val valid = my.isNotBlank() && opp.isNotBlank() && MatchRules.validLog(format, chosen)
    var pick by rememberSaveable { mutableStateOf<Pick?>(null) }

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding()) {
        HeaderBlock(onBack = { vm.back() }) {
            Text("LOG A MATCH", style = Caps, color = C.Yellow)
            Text("Already played", color = Color.White, fontWeight = FontWeight.Black, fontSize = 30.sp)
        }
        MatchFields(format, { format = it }, my, name, { name = it }, opp, { pick = it })
        SectionLabel("Games")
        // Show a game row once the games before it leave the match undecided.
        val rows = (1..format.maxGames).filter { n -> n == 1 || (chosen.size >= n - 1 && !MatchRules.validLog(format, chosen.take(n - 1))) }
        rows.forEach { n ->
            val i = n - 1
            Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).padding(12.dp)) {
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
                    Text("Went first:", color = C.Muted, fontSize = 13.sp)
                    Chip("You", firsts[i] == Side.ME) { firsts[i] = if (firsts[i] == Side.ME) null else Side.ME }
                    Chip("Them", firsts[i] == Side.OPP) { firsts[i] = if (firsts[i] == Side.OPP) null else Side.OPP }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Box(Modifier.padding(horizontal = 20.dp)) {
            BigButton("SAVE", if (valid) C.Ink else C.Line, if (valid) Color.White else C.Muted) {
                if (valid) vm.logMatch(MatchSetup(format, my, name, opp), chosen, firsts.take(chosen.size))
            }
        }
        Spacer(Modifier.height(24.dp))
    }
    PickerOverlay(vm, pick, my, opp, onPicked = { p, l -> if (p == Pick.MINE) my = l else opp = l }, onClose = { pick = null })
    }
}

@Composable
private fun ResultPick(label: String, color: Color, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(40.dp).clip(RoundedCornerShape(12.dp)).background(if (selected) color else Color(0xFFF0ECE6)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontWeight = FontWeight.ExtraBold, color = C.Ink) }
}
