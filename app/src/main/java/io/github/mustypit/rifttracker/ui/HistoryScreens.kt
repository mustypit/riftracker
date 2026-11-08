package io.github.mustypit.rifttracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import io.github.mustypit.rifttracker.game.Battlefields
import io.github.mustypit.rifttracker.game.Game
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.border
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
    /** (game index, side) whose battlefield is being picked. */
    var editing by remember { mutableStateOf<Pair<Int, Side>?>(null) }

    Box(Modifier.fillMaxSize()) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            HeaderBlock(onBack = { vm.back() }, color = C.outcome(r), ink = C.Black) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(when (r) { Outcome.WIN -> "VICTORY"; Outcome.LOSS -> "DEFEAT"; Outcome.DRAW -> "DRAW" }, style = Caps, color = C.Black, modifier = Modifier.weight(1f))
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Match options", tint = C.Black) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Delete match") }, onClick = { menu = false; confirmDelete = true })
                        }
                    }
                }
                Text(MatchRules.scoreLine(m), fontWeight = FontWeight.Black, fontSize = 56.sp, color = C.Black)
                Text("${m.myLegend} vs ${m.opponentLabel}", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = C.Black)
                val time = Instant.ofEpochMilli(m.startedAt).atZone(Zone.id).format(dateTimeFmt)
                Text(listOfNotNull(m.format.label, time, if (m.logged) "logged" else "\u23F1 " + formatDuration(m.durationMs(m.startedAt))).joinToString(" · "), color = C.Black.copy(alpha = 0.7f), fontWeight = FontWeight.Bold)
            }
        }
        m.games.forEachIndexed { i, g ->
            item(key = "g$i") {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.Card).padding(16.dp)) {
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
                    // Battlefields: shown when set; tap to set or change (also after the match).
                    if (Battlefields.all.isNotEmpty() || g.battlefields.isNotEmpty()) {
                        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GameBattlefield("You", g.battlefieldOf(Side.ME), Modifier.weight(1f)) { editing = i to Side.ME }
                            GameBattlefield(m.oppName.ifBlank { "Them" }, g.battlefieldOf(Side.OPP), Modifier.weight(1f)) { editing = i to Side.OPP }
                        }
                    }
                    if (g.events.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        ScoreChart(g, m.oppName.ifBlank { m.oppLegend })
                    }
                }
            }
        }
        item {
            Box(Modifier.padding(16.dp)) { BigButton("REMATCH", C.Solid, C.OnSolid) { vm.rematch(m) } }
        }
    }
    editing?.let { (i, side) ->
        BattlefieldPicker(
            title = if (side == Side.ME) "Your battlefield" else "Their battlefield",
            recentNames = all.flatMap { mm -> mm.games.reversed().map { it.battlefieldOf(side) } },
            selected = m.games.getOrNull(i)?.battlefieldOf(side),
            onPick = { vm.setBattlefield(m.id, i, side, it); editing = null },
            onClose = { editing = null },
        )
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

/** One player's battlefield in a game card: art and name, or an "add" placeholder. */
@Composable
private fun GameBattlefield(who: String, name: String?, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick).semantics { contentDescription = "$who battlefield: ${name ?: "not set"}" }) {
        if (name != null) BattlefieldThumb(name, Modifier.fillMaxWidth().aspectRatio(2.4f))
        else Box(Modifier.fillMaxWidth().aspectRatio(2.4f).clip(RoundedCornerShape(10.dp)).background(C.Line), contentAlignment = Alignment.Center) {
            Text("+ battlefield", color = C.Muted, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        Text(who.uppercase(), style = Caps.copy(fontSize = 10.sp), color = C.Muted, modifier = Modifier.padding(top = 4.dp))
        Text(name ?: "Not set", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (name == null) C.Muted else C.Ink)
    }
}

private fun kindIcon(kind: PointKind) = when (kind) {
    PointKind.CONQUER -> RiftIcons.Conquer
    PointKind.HOLD -> RiftIcons.Hold
    PointKind.ABILITY -> RiftIcons.Ability
    PointKind.CORRECTION -> RiftIcons.Minus
}

/**
 * The game, point by point: time on the left, your points in the left lane, the running score in
 * the middle on a line coloured by who's leading, their points in the right lane. A lead that
 * changes hands gets a yellow outline. Then each player's points by kind and a few facts.
 */
@Composable
private fun ScoreChart(g: Game, oppLabel: String) {
    val rows = GameRules.chart(g)
    Column(Modifier.fillMaxWidth().semantics { contentDescription = "Score chart" }) {
        rows.forEach { r ->
            val lead = r.leader?.let(C::side) ?: C.Line
            Row(Modifier.fillMaxWidth().height(38.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(formatDuration(r.event.atMs), color = C.Muted, fontSize = 11.sp, modifier = Modifier.width(40.dp))
                ChartLane(r.event.side == Side.ME, r.event.kind, C.Teal, r.leader == Side.ME, Modifier.weight(1f))
                // Centre: the leader's line with the score on it.
                Box(Modifier.width(66.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    Box(Modifier.width(4.dp).fillMaxHeight().background(lead))
                    Text(
                        "${r.me}–${r.opp}", fontWeight = FontWeight.Black, fontSize = 12.sp, color = C.Ink,
                        modifier = Modifier.clip(RoundedCornerShape(9.dp)).background(C.Card)
                            .border(if (r.leadChange) 2.dp else 1.dp, if (r.leadChange) C.Yellow else C.Line, RoundedCornerShape(9.dp))
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                    )
                }
                ChartLane(r.event.side == Side.OPP, r.event.kind, C.Coral, r.leader == Side.OPP, Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(12.dp))
        BreakdownRow("YOU", GameRules.breakdown(g, Side.ME), C.Teal)
        BreakdownRow(oppLabel.uppercase(), GameRules.breakdown(g, Side.OPP), C.Coral)
        val changes = rows.filter { it.leadChange }
        val run = GameRules.longestRun(g)
        Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Lead changes", color = C.Muted, fontSize = 12.sp)
                Text("${changes.size}", fontWeight = FontWeight.Black, fontSize = 22.sp, color = C.Ink)
                changes.lastOrNull()?.let { Text("last ${formatDuration(it.event.atMs)} · ${it.me}–${it.opp}", color = C.Muted, fontSize = 12.sp) }
            }
            Column(Modifier.weight(1f)) {
                Text("Longest run", color = C.Muted, fontSize = 12.sp)
                Text(run?.second?.toString() ?: "–", fontWeight = FontWeight.Black, fontSize = 22.sp, color = run?.first?.let(C::side) ?: C.Ink)
                run?.let { Text(if (it.first == Side.ME) "you, unanswered" else "$oppLabel, unanswered", color = C.Muted, fontSize = 12.sp) }
            }
        }
    }
}

/** One lane of the chart: tinted while that player leads; an icon on the rows where they scored. */
@Composable
private fun ChartLane(scored: Boolean, kind: PointKind, color: Color, leading: Boolean, modifier: Modifier) {
    Box(modifier.fillMaxHeight().background(if (leading) color.copy(alpha = 0.14f) else Color.Transparent), contentAlignment = Alignment.Center) {
        if (scored) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
                Icon(kindIcon(kind), null, tint = Color.White, modifier = Modifier.size(15.dp))
            }
        }
    }
}

@Composable
private fun BreakdownRow(who: String, counts: Map<PointKind, Int>, color: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(who, style = Caps, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(96.dp))
        listOf(PointKind.CONQUER, PointKind.HOLD, PointKind.ABILITY).forEach { k ->
            val n = counts[k] ?: 0
            Box(Modifier.size(22.dp).clip(CircleShape).background(if (n > 0) color else C.Line), contentAlignment = Alignment.Center) {
                Icon(kindIcon(k), null, tint = Color.White, modifier = Modifier.size(12.dp))
            }
            Text("$n", fontWeight = FontWeight.Black, fontSize = 15.sp, color = if (n > 0) C.Ink else C.Muted, modifier = Modifier.padding(start = 5.dp, end = 14.dp))
        }
    }
}

// ---------------------------------------------------------------- Legends

enum class LegendTab(val label: String) { Legends("Yours"), Opponents("Against"), Rivals("Rivals"), Battlefields("Fields") }

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
            LegendTab.Battlefields -> {
                // Counted per game: each game of a best-of-3 can use a different battlefield.
                val mine = Stats.byBattlefield(matches, Side.ME)
                val theirs = Stats.byBattlefield(matches, Side.OPP)
                if (mine.isEmpty() && theirs.isEmpty()) item { EmptyState("No battlefields yet", "Pick battlefields on the “who goes first” card, or add them to a game from its match.", Modifier.fillMaxWidth()) }
                if (mine.isNotEmpty()) item { SectionLabel("Your battlefields · games") }
                items(mine, key = { "bm" + it.legend }) { s -> BattlefieldStatRow(s.legend, s.record) }
                if (theirs.isNotEmpty()) item { SectionLabel("Against these battlefields · games") }
                items(theirs, key = { "bo" + it.legend }) { s -> BattlefieldStatRow(s.legend, s.record) }
            }
        }
    }
}

/** A battlefield's art strip with its name, game record and win rate. */
@Composable
private fun BattlefieldStatRow(name: String, r: Record) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.Card)) {
        BattlefieldThumb(name, Modifier.fillMaxWidth().aspectRatio(4.2f), corner = 0)
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(r.line + if (r.total == 1) " · 1 game" else " · ${r.total} games", color = C.Muted, fontSize = 13.sp)
            }
            val rate = r.winRate
            if (r.total >= Stats.MIN_MATCHES_FOR_RATE && rate != null) {
                Text(
                    Stats.percent(rate), fontWeight = FontWeight.Black, fontSize = 18.sp, color = C.Black,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(if (rate >= 0.5) C.Teal else C.Coral).padding(horizontal = 10.dp, vertical = 4.dp),
                )
            } else Text(Stats.rateOrHint(r).replace("match", "game"), color = C.Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Name, record and win rate (or how many more matches until there is one). */
@Composable
private fun StatRow(title: String, r: Record, sub: String? = null, avatar: String? = null, onClick: (() -> Unit)? = null) {
    val rate = r.winRate
    val enough = r.total >= Stats.MIN_MATCHES_FOR_RATE
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.Card)
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
                Stats.percent(rate), fontWeight = FontWeight.Black, fontSize = 20.sp, color = C.Black,
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
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(C.Card).padding(14.dp)) {
        Text(value, fontWeight = FontWeight.Black, fontSize = 24.sp)
        Text(label, color = C.Muted, fontSize = 12.sp, maxLines = 2)
    }
}
