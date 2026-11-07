package io.github.mustypit.rifttracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mustypit.rifttracker.game.GameRules
import io.github.mustypit.rifttracker.game.MatchRules
import io.github.mustypit.rifttracker.game.Outcome
import io.github.mustypit.rifttracker.game.PointKind
import io.github.mustypit.rifttracker.game.Side
import io.github.mustypit.rifttracker.game.formatDuration
import io.github.mustypit.rifttracker.stats.Period
import io.github.mustypit.rifttracker.stats.Record
import io.github.mustypit.rifttracker.stats.Stats
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayFmt = DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.ENGLISH)
private val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
private val dateTimeFmt = DateTimeFormatter.ofPattern("EEE, MMM d · HH:mm", Locale.ENGLISH)

/** The time zone screens group and print dates in (tests pin it). */
object Zone { var id: ZoneId = ZoneId.systemDefault() }

@Composable
private fun PeriodChips(vm: MainViewModel) {
    val period by vm.period.collectAsState()
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(Period.entries) { p -> Chip(p.label, p == period) { vm.period.value = p } }
    }
}

@Composable
fun MatchesScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val all by vm.history.collectAsState()
    val period by vm.period.collectAsState()
    val matches = remember(all, period) { Stats.inPeriod(all, period, AppGraph.clock()) }
    val record = Stats.record(matches)
    val months = remember(matches) { Stats.group(matches, Zone.id) }

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            HeaderBlock {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${matches.size}", color = Color.White, fontWeight = FontWeight.Black, fontSize = 56.sp)
                    Text(if (matches.size == 1) " match" else " matches", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.padding(bottom = 12.dp).weight(1f))
                    IconButton(onClick = { vm.push(Screen.LogMatch) }) { Icon(Icons.Default.Add, "Log a match", tint = C.Yellow) }
                }
                val latest = matches.firstOrNull()?.myLegend
                Text(
                    listOfNotNull("${Stats.percent(record.winRate)} win rate".takeIf { matches.isNotEmpty() }, record.line.takeIf { matches.isNotEmpty() }, latest?.let { "latest: $it" }).joinToString(" · ").ifEmpty { "Nothing recorded yet" },
                    color = Color.White.copy(alpha = 0.8f), fontWeight = FontWeight.Bold,
                )
            }
        }
        item { PeriodChips(vm) }
        if (matches.isEmpty()) item { EmptyState("No matches yet", "Finished matches from the Play tab show up here. You can also log one you already played with +.", Modifier.fillMaxWidth()) }
        months.forEach { month ->
            item(key = "m${month.month}") {
                Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(month.month.format(monthFmt).uppercase(), style = Caps, color = C.Ink, modifier = Modifier.weight(1f))
                    Text(month.record.line, style = Caps, color = C.Muted)
                }
            }
            month.sessions.forEach { s ->
                item(key = "s${s.matches.first().id}") {
                    Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(s.day.format(dayFmt), fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        Text("${s.matches.size} · ${s.record.line}", color = C.Muted, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
                items(s.matches, key = { it.id }) { m -> MatchRow(m, onClick = { vm.push(Screen.MatchDetail(m.id)) }) }
            }
        }
    }
}

@Composable
fun MatchDetailScreen(vm: MainViewModel, id: Long) {
    val all by vm.history.collectAsState()
    val m = all.firstOrNull { it.id == id } ?: return
    val r = MatchRules.result(m)
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            HeaderBlock(onBack = { vm.back() }, color = C.outcome(r), ink = C.Ink) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(when (r) { Outcome.WIN -> "VICTORY"; Outcome.LOSS -> "DEFEAT"; Outcome.DRAW -> "DRAW" }, style = Caps, color = C.Ink, modifier = Modifier.weight(1f))
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Match options", tint = C.Ink) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Delete match") }, onClick = { menu = false; confirmDelete = true })
                        }
                    }
                }
                Text(MatchRules.scoreLine(m), fontWeight = FontWeight.Black, fontSize = 56.sp, color = C.Ink)
                Text("${m.myLegend} vs ${m.opponentLabel}", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = C.Ink)
                val time = Instant.ofEpochMilli(m.startedAt).atZone(Zone.id).format(dateTimeFmt)
                Text(listOfNotNull(m.format.label, time, if (m.logged) "logged" else "\u23F1 " + formatDuration(m.durationMs(m.startedAt))).joinToString(" · "), color = C.Ink.copy(alpha = 0.7f), fontWeight = FontWeight.Bold)
            }
        }
        m.games.forEachIndexed { i, g ->
            item(key = "g$i") {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White).padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(g.outcome?.let(C::outcome) ?: C.Line))
                        Text("GAME ${i + 1}", style = Caps, modifier = Modifier.padding(start = 8.dp).weight(1f))
                        if (!m.logged) Text("${g.score(Side.ME)}–${g.score(Side.OPP)}", fontWeight = FontWeight.Black, fontSize = 18.sp)
                    }
                    val facts = listOfNotNull(
                        when (g.outcome) { Outcome.WIN -> "Won"; Outcome.LOSS -> "Lost"; Outcome.DRAW -> "Draw"; null -> null },
                        g.wentFirst?.let { if (it == Side.ME) "you went first" else "${m.oppName.ifBlank { "they" }} went first" },
                        if (m.logged) null else "\u23F1 " + formatDuration(g.durationMs(g.startedAt)),
                    )
                    Text(facts.joinToString(" · "), color = C.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
                    if (g.events.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        GameRules.timeline(g).forEach { (e, me, opp) -> TimelineRow(e.side, e.kind, e.atMs, me, opp, m.oppName.ifBlank { m.oppLegend }) }
                    }
                }
            }
        }
        item {
            Box(Modifier.padding(16.dp)) { BigButton("REMATCH", C.Ink, Color.White) { vm.rematch(m) } }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this match?") },
            text = { Text("It's removed from your history and stats.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deleteMatch(m.id) }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun TimelineRow(side: Side, kind: PointKind, atMs: Long, me: Int, opp: Int, oppLabel: String) {
    val what = when (kind) { PointKind.CONQUER -> "conquer"; PointKind.HOLD -> "hold"; PointKind.ABILITY -> "ability"; PointKind.CORRECTION -> "−1 correction" }
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(formatDuration(atMs), color = C.Muted, fontSize = 12.sp, modifier = Modifier.width(44.dp))
        Box(Modifier.width(4.dp).height(18.dp).clip(RoundedCornerShape(2.dp)).background(C.side(side)))
        Text("${if (side == Side.ME) "You" else oppLabel} · $what", fontSize = 14.sp, modifier = Modifier.padding(start = 10.dp).weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("$me–$opp", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
    }
}

// ---------------------------------------------------------------- Legends

enum class LegendTab(val label: String) { Legends("Your legends"), Opponents("Against"), Rivals("Rivals") }

@Composable
fun LegendsScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val all by vm.history.collectAsState()
    val period by vm.period.collectAsState()
    val matches = remember(all, period) { Stats.inPeriod(all, period, AppGraph.clock()) }
    var tab by rememberSaveable { mutableStateOf(LegendTab.Legends) }
    val mine = remember(matches) { Stats.byMyLegend(matches) }

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            HeaderBlock {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${mine.size}", color = Color.White, fontWeight = FontWeight.Black, fontSize = 56.sp)
                    Text(if (mine.size == 1) " legend played" else " legends played", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.padding(bottom = 12.dp))
                }
                mine.firstOrNull()?.let { Text("Most played: ${it.legend} (${it.record.total})", color = Color.White.copy(alpha = 0.8f), fontWeight = FontWeight.Bold) }
            }
        }
        item {
            Segmented(LegendTab.entries, tab, { it.label }, { tab = it }, Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp))
        }
        item { PeriodChips(vm) }
        when (tab) {
            LegendTab.Legends -> {
                if (mine.isEmpty()) item { EmptyState("No legends yet", "Play or log a match to see how each legend does.", Modifier.fillMaxWidth()) }
                items(mine, key = { "l" + it.legend }) { s -> StatRow(s.legend, s.record, avatar = s.legend, onClick = { vm.push(Screen.LegendDetail(s.legend)) }) }
            }
            LegendTab.Opponents -> {
                val opp = Stats.byOppLegend(matches)
                if (opp.isEmpty()) item { EmptyState("Nothing yet", "Your record against each legend shows up here.", Modifier.fillMaxWidth()) }
                items(opp, key = { "o" + it.legend }) { s -> StatRow("vs ${s.legend}", s.record, avatar = s.legend) }
            }
            LegendTab.Rivals -> {
                val rivals = Stats.rivals(matches)
                if (rivals.isEmpty()) item { EmptyState("No rivals yet", "Add your opponent's name when you start a match to track your record against them.", Modifier.fillMaxWidth()) }
                items(rivals, key = { "r" + it.name }) { s -> StatRow(s.name, s.record, sub = s.legends.joinToString(", ")) }
            }
        }
    }
}

/** Name, record and win rate (or how many more matches until there is one). */
@Composable
private fun StatRow(title: String, r: Record, sub: String? = null, avatar: String? = null, onClick: (() -> Unit)? = null) {
    val rate = r.winRate
    val enough = r.total >= Stats.MIN_MATCHES_FOR_RATE
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (avatar != null) LegendAvatar(avatar, 44.dp, Modifier.padding(end = 12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(r.line, sub).joinToString(" · "), color = C.Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (enough && rate != null) {
            Text(
                Stats.percent(rate), fontWeight = FontWeight.Black, fontSize = 20.sp, color = C.Ink,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(if (rate >= 0.5) C.Teal else C.Coral).padding(horizontal = 10.dp, vertical = 4.dp),
            )
        } else {
            Text(Stats.rateOrHint(r), color = C.Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun LegendDetailScreen(vm: MainViewModel, legend: String) {
    val all by vm.history.collectAsState()
    val period by vm.period.collectAsState()
    val matches = remember(all, period) { Stats.inPeriod(all, period, AppGraph.clock()) }
    val d = remember(matches, legend) { Stats.legendDetail(matches, legend) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            HeaderBlock(onBack = { vm.back() }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LegendAvatar(d.legend, 56.dp)
                    Column(Modifier.padding(start = 14.dp)) {
                        Text(period.label.uppercase(), style = Caps, color = C.Yellow)
                        Text(d.legend, color = Color.White, fontWeight = FontWeight.Black, fontSize = 34.sp)
                    }
                }
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 8.dp)) {
                    Text(if (d.record.total >= Stats.MIN_MATCHES_FOR_RATE) Stats.percent(d.record.winRate) else "–", color = Color.White, fontWeight = FontWeight.Black, fontSize = 52.sp, modifier = Modifier.weight(1f))
                    Count(d.record.wins, "won", C.Teal)
                    Count(d.record.losses, "lost", C.Coral)
                    Count(d.record.draws, "drawn", Color.White)
                }
                Text(
                    if (d.record.total >= Stats.MIN_MATCHES_FOR_RATE) "win rate, a draw counts a third" else Stats.rateOrHint(d.record) + " for a win rate",
                    color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp,
                )
            }
        }
        item {
            Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Fact("${d.record.total}", "matches", Modifier.weight(1f))
                Fact("${d.games}", "games", Modifier.weight(1f))
                Fact("${d.bestRun}", "best run", Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Fact(Stats.percent(d.goingFirst.winRate), "going first · ${d.goingFirst.total} games", Modifier.weight(1f))
                Fact(Stats.percent(d.goingSecond.winRate), "going second · ${d.goingSecond.total} games", Modifier.weight(1f))
            }
        }
        if (d.against.isNotEmpty()) {
            item { SectionLabel("Against") }
            items(d.against, key = { "a" + it.legend }) { s -> StatRow("vs ${s.legend}", s.record, avatar = s.legend) }
        }
        if (d.latest.isNotEmpty()) {
            item { SectionLabel("Latest") }
            items(d.latest, key = { "m" + it.id }) { m -> MatchRow(m, onClick = { vm.push(Screen.MatchDetail(m.id)) }, showMyLegend = false) }
        }
    }
}

@Composable
private fun Count(n: Int, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(start = 14.dp, bottom = 8.dp)) {
        Text("$n", color = color, fontWeight = FontWeight.Black, fontSize = 24.sp)
        Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
    }
}

@Composable
private fun Fact(value: String, label: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(Color.White).padding(14.dp)) {
        Text(value, fontWeight = FontWeight.Black, fontSize = 24.sp)
        Text(label, color = C.Muted, fontSize = 12.sp, maxLines = 2)
    }
}
