package io.github.mustypit.rifttracker.ui

import android.content.ClipData
import java.util.Locale
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.material.icons.filled.Add
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboard
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mustypit.rifttracker.game.Board
import io.github.mustypit.rifttracker.game.Card
import io.github.mustypit.rifttracker.game.DeckCodeFormat
import io.github.mustypit.rifttracker.game.DeckCodes
import io.github.mustypit.rifttracker.game.Cards
import io.github.mustypit.rifttracker.game.Deck
import io.github.mustypit.rifttracker.game.Printing
import io.github.mustypit.rifttracker.game.Prices
import io.github.mustypit.rifttracker.game.DeckRules
import io.github.mustypit.rifttracker.game.Domain
import io.github.mustypit.rifttracker.game.Legends
import kotlin.math.roundToInt
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import io.github.mustypit.rifttracker.game.Match
import io.github.mustypit.rifttracker.game.Outcome
import io.github.mustypit.rifttracker.stats.Stats

/** The legend's art (cropped, from assets/legends), by the legend card's champion. */
@Composable
private fun legendArt(deck: Deck) = DeckRules.legendOf(deck)?.let { DeckRules.championTag(it) }?.let { tag ->
    val context = LocalContext.current
    remember(tag) { LegendArt.get(context, Legends.idOf(tag)) }
}

@Composable
private fun DomainIcons(domains: List<Domain>, size: Dp = 16.dp) = Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
    domains.forEach { RuneSymbol(it, size) }
}

/** Main n/40 · Runes n/12 · Fields n/3, each red until right. */
// The list of decks

@Composable
fun DecksScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val decks by vm.decks.collectAsState()
    val drafts by vm.deckDrafts.collectAsState()
    var pasting by remember { mutableStateOf(false) }
    val shown = decks.map { drafts[it.id] ?: it }
    val history by vm.history.collectAsState()
    Box(modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            item { HeaderBlock { Text("Decks", color = Color.White, fontWeight = FontWeight.Black, fontSize = 30.sp) } }
            items(shown, key = { it.id }) { d ->
                val ms = remember(history, d.id) { Stats.deckMatches(history, d.id) }
                DeckRow(d, ms) { vm.push(Screen.DeckDetail(d.id)) }
            }
            item {
                Text(
                    "+ New deck", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = C.Muted, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .border(1.5.dp, C.Line, RoundedCornerShape(16.dp)).clickable { vm.push(Screen.NewDeck) }.padding(18.dp),
                )
                Text(
                    "Import", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = C.Muted, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clickable { pasting = true }.padding(16.dp),
                )
            }
        }
        if (pasting) PasteOverlay(onClose = { pasting = false }) { deck -> pasting = false; vm.createDeck(deck, replaceTop = false) }
    }
}

@Composable
private fun DeckRow(d: Deck, matches: List<Match>, onClick: () -> Unit) {
    val art = legendArt(d)
    val problem = remember(d) { DeckRules.problems(d).firstOrNull() }
    Box(
        Modifier.padding(horizontal = 16.dp, vertical = 5.dp).fillMaxWidth().height(80.dp).clip(RoundedCornerShape(16.dp)).background(C.Card)
            .clickable(onClick = onClick).semantics(mergeDescendants = true) { contentDescription = "Deck ${d.name}" },
    ) {
        art?.let {
            Image(it, null, contentScale = ContentScale.Crop, alignment = BiasAlignment(0f, -0.4f), modifier = Modifier.fillMaxHeight().fillMaxWidth(0.62f).align(Alignment.CenterEnd))
        }
        Box(Modifier.matchParentSize().background(Brush.horizontalGradient(0.35f to C.Card, 1f to C.Card.copy(alpha = 0.15f))))
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(d.name, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = C.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 3.dp)) {
                    DomainIcons(DeckRules.domains(d), 14.dp)
                    Text("${d.mainSize} · ${d.runeCount} · ${d.battlefields.size}", color = C.Muted, fontSize = 12.sp)
                }
            }
            // Record and form when it's been played; an unfinished/illegal deck also shows its label.
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                if (matches.isNotEmpty()) {
                    val rec = Stats.record(matches)
                    Text(rec.line, fontWeight = FontWeight.Black, fontSize = 18.sp, color = Color.White, style = TextStyle(shadow = Shadow(Color.Black, blurRadius = 8f)))
                    if (problem == null) {
                        Text(Stats.rateOrHint(rec), fontSize = 11.sp, color = Color.White.copy(alpha = 0.85f))
                        FormDots(Stats.form(matches))
                    }
                }
                if (problem != null) Text(
                    problem, color = C.Red, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFF2A1513)).padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
    }
}

@Composable
private fun FormDots(form: List<Outcome>, modifier: Modifier = Modifier) = Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
    form.reversed().forEach { o -> Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(C.outcome(o))) }
}

/** The deck's results (Stats tab): record, going first/second, by opponent legend. */
@Composable
private fun DeckRecord(matches: List<Match>) {
    if (matches.isEmpty()) return
    val legend = matches.first().myLegend
    val detail = remember(matches) { Stats.legendDetail(matches, legend, latestCount = 3) }
    val rec = detail.record
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.Card).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(rec.line, fontWeight = FontWeight.Black, fontSize = 26.sp, color = C.Ink, modifier = Modifier.semantics { contentDescription = "Deck record ${rec.line}" })
            Text("  " + listOfNotNull(rec.winRate?.takeIf { rec.total >= Stats.MIN_MATCHES_FOR_RATE }?.let { Stats.percent(it) }, "${rec.total} ${if (rec.total == 1) "match" else "matches"}").joinToString(" · "), color = C.Muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 5.dp))
        }
        Row(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            if (rec.wins > 0) Box(Modifier.weight(rec.wins.toFloat()).fillMaxHeight().background(C.Green))
            if (rec.draws > 0) Box(Modifier.weight(rec.draws.toFloat()).fillMaxHeight().background(C.DrawGrey))
            if (rec.losses > 0) Box(Modifier.weight(rec.losses.toFloat()).fillMaxHeight().background(C.Red))
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(
            "Going first" to Stats.percent(detail.goingFirst.winRate),
            "Going second" to Stats.percent(detail.goingSecond.winRate),
        ).forEach { (label, v) ->
            Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(C.Card).padding(horizontal = 10.dp, vertical = 8.dp)) {
                Text(label, color = C.Muted, fontSize = 11.sp)
                Text(v, fontWeight = FontWeight.Black, fontSize = 16.sp, color = C.Ink)
            }
        }
    }
    if (detail.against.isNotEmpty()) {
        Text("BY OPPONENT", style = Caps, color = C.Muted, modifier = Modifier.padding(top = 4.dp))
        detail.against.take(4).forEach { a ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.Card).padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                LegendAvatar(a.legend, 30.dp)
                Text("vs ${a.legend}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = C.Ink, modifier = Modifier.weight(1f).padding(start = 10.dp))
                Text(a.record.line, fontWeight = FontWeight.Black, color = C.Ink)
            }
        }
    }
}

// New deck: legend, then the chosen champion

@Composable
fun NewDeckScreen(vm: MainViewModel) {
    var legendSlug by rememberSaveable { mutableStateOf<String?>(null) }
    var championSlug by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    val legend = legendSlug?.let(Cards::bySlug)
    val legends = remember(Cards.all, query) {
        DeckRules.legends.filter { l -> query.isBlank() || l.name.contains(query.trim(), true) || l.tags.any { it.contains(query.trim(), true) } }
    }
    Column(Modifier.fillMaxSize()) {
    HeaderBlock(onBack = { vm.back() }) {
        Text("New deck", color = Color.White, fontWeight = FontWeight.Black, fontSize = 30.sp)
        if (legend == null) RiftTextField("Search legends", query, { query = it }, Modifier.padding(top = 8.dp))
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3), modifier = Modifier.weight(1f).fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (legend == null) {
            item(span = { GridItemSpan(maxLineSpan) }) { Text("LEGEND", style = Caps, color = C.Muted, modifier = Modifier.padding(top = 8.dp)) }
            items(legends, key = { it.slug }) { l ->
                CardImage(l, Modifier.shadow(2.dp, RoundedCornerShape(7.dp)).clickable { legendSlug = l.slug; championSlug = DeckRules.champions(l).singleOrNull()?.slug }
                    .semantics { contentDescription = "Legend ${l.name}" })
            }
        } else {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.Card).padding(start = 10.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CardImage(legend, Modifier.width(46.dp), corner = 5.dp, showBanned = false)
                    Column(Modifier.weight(1f)) {
                        Text(legend.name, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = C.Ink)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            DomainIcons(legend.domainList, 14.dp)
                            Text(DeckRules.championTag(legend).orEmpty(), color = C.Muted, fontSize = 12.sp)
                        }
                    }
                    Text("Change", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, color = C.Ink,
                        modifier = Modifier.clip(RoundedCornerShape(9.dp)).border(1.5.dp, C.Line, RoundedCornerShape(9.dp)).clickable { legendSlug = null; championSlug = null }.padding(horizontal = 10.dp, vertical = 5.dp))
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) { Text("CHOSEN CHAMPION", style = Caps, color = C.Muted, modifier = Modifier.padding(top = 8.dp)) }
            items(DeckRules.champions(legend), key = { it.slug }) { c ->
                val on = c.slug == championSlug
                CardImage(
                    c, Modifier.then(if (on) Modifier.border(3.dp, C.Accent, RoundedCornerShape(9.dp)).padding(5.dp) else Modifier)
                        .clickable { championSlug = c.slug }.semantics { contentDescription = "Champion ${c.name}" + if (on) " on" else "" },
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                val champion = championSlug?.let(Cards::bySlug)
                Box(Modifier.padding(top = 12.dp)) {
                    BigButton("START BUILDING", if (champion != null) C.Solid else C.Track, if (champion != null) C.OnSolid else C.Muted) {
                        if (champion != null) vm.createDeck(DeckRules.newDeck(legend, champion))
                    }
                }
            }
        }
    }
}
}

// A deck

/** How the main deck is shown: rows or card pictures (Piltover Archive-style buttons). */
enum class DeckView { List, Grid }
enum class DeckGroup(val label: String) { Type("Type"), Cost("Cost"), None("None") }
enum class DeckSort(val label: String) { Energy("Energy"), Name("Name") }
private enum class DeckTab(val label: String) { Deck("Deck"), Stats("Stats"), Matches("Matches") }

/**
 * A deck (owner-approved Piltover Archive-style layout): header with domain and legality chips,
 * tabs Deck · Stats · Matches. The Deck tab: List / Grid (+ Stacked in Grid) and a group-and-sort
 * menu, then Legend · Main deck · Sideboard · Battlefields · Runes · Maybe · Stats. A round + adds cards.
 */
@Composable
fun DeckScreen(vm: MainViewModel, id: Long) {
    val drafts by vm.deckDrafts.collectAsState()
    val decks by vm.decks.collectAsState()
    val deck = drafts[id] ?: decks.firstOrNull { it.id == id } ?: return
    val view by vm.deckView.collectAsState()
    val group by vm.deckGroup.collectAsState()
    val sort by vm.deckSort.collectAsState()
    val stacked by vm.deckStacked.collectAsState()
    val history by vm.history.collectAsState()
    val matches = remember(history, id) { Stats.deckMatches(history, id) }
    var tab by rememberSaveable { mutableStateOf(DeckTab.Deck) }
    var renaming by remember { mutableStateOf(false) }
    var pickingField by remember { mutableStateOf<Int?>(null) }
    var moving by remember { mutableStateOf<Pair<String, Board>?>(null) }
    // The list row that's open for editing (board + section + slug), if any.
    var openRow by remember { mutableStateOf<String?>(null) }
    fun edit(f: (Deck) -> Deck) = vm.editDeck(id, f)

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 96.dp)) {
            DeckHeader(deck, onBack = { vm.back() }, onRename = { renaming = true }, onDuplicate = { vm.duplicateDeck(id) }, onArts = { vm.push(Screen.DeckArts(id)) }, onDelete = { vm.deleteDeck(id) })
            DeckTabs(tab, total = 1 + deck.mainSize + deck.runeCount + deck.battlefields.size + deck.sideSize, matchCount = matches.size) { tab = it }
            AnimatedContent(tab, contentAlignment = Alignment.TopStart, transitionSpec = { tabSlide() }, label = "deck tab") { t ->
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (t) {
                    DeckTab.Deck -> {
                        DeckToolbar(view, stacked, group, sort, vm.deckView::value::set, vm.deckStacked::value::set, vm.deckGroup::value::set, vm.deckSort::value::set)
                        if (deck.main.size <= 1) EmptyState("Add cards", "Tap +.", Modifier.fillMaxWidth())
                        val open = { c: Card -> vm.push(Screen.CardDetail(c.slug)) }
                        @Composable fun cards(list: List<Pair<Card, Int>>, board: Board, section: String) {
                            val chosen = section == CHAMPION
                            // Maybe cards are faded, in every view.
                            if (view == DeckView.Grid) Column(
                                Modifier.graphicsLayer { alpha = if (board == Board.MAYBE) 0.7f else 1f },
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) { CardGrid(list, stacked, chosen, open, deck::printingOf) }
                            else list.forEach { (c, n) ->
                                val k = "${board.name}/$section/${c.slug}"
                                key(k) {
                                    CardRow(
                                        deck, c, n, board, chosen, expanded = openRow == k, onToggle = { openRow = if (openRow == k) null else k },
                                        onOpen = { open(c) }, onAdd = { edit { DeckRules.add(it, board, c) } }, onRemove = { edit { DeckRules.remove(it, board, c) } },
                                        onMove = { moving = c.slug to board },
                                    )
                                }
                            }
                        }
                        DeckRules.legendOf(deck)?.let { legend ->
                            SectionHead("LEGEND")
                            if (view == DeckView.Grid) CardGrid(listOf(legend to 1), stacked = true, chosen = false, onOpen = open, art = deck::printingOf)
                            else LegendRow(legend, deck.printingOf(legend)) { open(legend) }
                        }
                        SectionHead("MAIN DECK", deck.mainSize, DeckRules.MAIN_SIZE, description = "Main ${deck.mainSize} of ${DeckRules.MAIN_SIZE}")
                        Crossfade(Triple(view, stacked, group to sort), animationSpec = tween(220), label = "deck view") { (_, _, gs) ->
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                sections(deck, gs.first, gs.second).forEach { (name, list) ->
                                    SubHead(name, list.sumOf { it.second })
                                    cards(list, Board.MAIN, name)
                                }
                            }
                        }
                        val side = DeckRules.entries(deck, Board.SIDE)
                        if (side.isNotEmpty()) {
                            SectionHead("SIDEBOARD", deck.sideSize, DeckRules.SIDEBOARD, exact = false, description = "Side ${deck.sideSize} of ${DeckRules.SIDEBOARD}")
                            cards(side, Board.SIDE, "side")
                        }
                        val fields = deck.battlefields.distinctBy { it.lowercase() }.size
                        SectionHead("BATTLEFIELDS", fields, DeckRules.BATTLEFIELDS, description = "Fields $fields of ${DeckRules.BATTLEFIELDS}")
                        FieldSlots(deck) { pickingField = it }
                        SectionHead("RUNES", deck.runeCount, DeckRules.RUNES, description = "Runes ${deck.runeCount} of ${DeckRules.RUNES}")
                        RuneSplit(deck, ::edit)
                        val maybe = DeckRules.entries(deck, Board.MAYBE)
                        if (maybe.isNotEmpty()) {
                            SectionHead("MAYBEBOARD", deck.maybeSize)
                            cards(maybe, Board.MAYBE, "maybe")
                        }
                        SectionHead("STATS")
                        DeckStats(deck, ::edit)
                    }
                    DeckTab.Stats -> {
                        DeckValue(deck)
                        if (matches.isEmpty()) EmptyState("No matches yet", "Pick this deck when you start a match.", Modifier.fillMaxWidth()) else DeckRecord(matches)
                    }
                    DeckTab.Matches -> if (matches.isEmpty()) EmptyState("No matches yet", "Pick this deck when you start a match.", Modifier.fillMaxWidth()) else {
                        Column(Modifier.fullBleed(16.dp)) {
                            matches.forEach { m -> MatchRow(m, onClick = { vm.push(Screen.MatchDetail(m.id)) }, showMyLegend = false) }
                        }
                    }
                }
            }
            }
        }
        // Add cards: a round button in the corner.
        val source = remember { MutableInteractionSource() }
        Box(
            Modifier.align(Alignment.BottomEnd).padding(20.dp).size(58.dp).squishWhenPressed(source).shadow(8.dp, CircleShape).clip(CircleShape).background(C.Accent)
                .clickable(interactionSource = source, indication = ripple()) { vm.push(Screen.DeckAddCards(id)) }
                .semantics { contentDescription = "Add cards" },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Default.Add, null, tint = C.OnAccent, modifier = Modifier.size(30.dp)) }
        pickingField?.let { i ->
            BattlefieldPicker(
                "Battlefield ${i + 1}", deck.battlefields, deck.battlefields.getOrNull(i),
                onPick = { name ->
                    edit { d ->
                        val list = d.battlefields.toMutableList()
                        if (name == null) { if (i < list.size) list.removeAt(i) } else if (i < list.size) list[i] = name else list += name
                        d.copy(battlefields = list)
                    }
                    pickingField = null
                },
                onClose = { pickingField = null },
            )
        }
        if (renaming) RenameOverlay(deck.name, onClose = { renaming = false }) { n -> edit { it.copy(name = n) }; renaming = false }
        moving?.let { (slug, from) ->
            val c = Cards.bySlug(slug)
            if (c == null || deck.count(from, slug) == 0) moving = null
            else MoveSheet(deck, c, from, onMove = { to, all -> edit { DeckRules.move(it, c, from, to, all) }; moving = null },
                onOpen = { moving = null; vm.push(Screen.CardDetail(slug)) }, onClose = { moving = null })
        }
    }
}

/** Deck · Stats · Matches, with counts. An accent underline glides to the open tab. */
@Composable
private fun DeckTabs(tab: DeckTab, total: Int, matchCount: Int, onTab: (DeckTab) -> Unit) {
    val density = LocalDensity.current
    // Each tab's label: left edge and width in px, within the row.
    val spans = remember { mutableStateMapOf<Int, Pair<Float, Float>>() }
    val pos by animateFloatAsState(tab.ordinal.toFloat(), spring(dampingRatio = 0.85f, stiffness = 420f), label = "tab underline")
    fun edge(right: Boolean): Float {
        val lo = pos.toInt().coerceIn(0, DeckTab.entries.size - 1); val hi = (lo + 1).coerceAtMost(DeckTab.entries.size - 1)
        fun at(k: Int) = spans[k]?.let { if (right) it.first + it.second else it.first } ?: 0f
        return at(lo) + (at(hi) - at(lo)) * (pos - lo)
    }
    Column {
        Box(Modifier.padding(horizontal = 16.dp)) {
            if (spans.size == DeckTab.entries.size) {
                val l = edge(false); val r = edge(true)
                Box(
                    Modifier.align(Alignment.BottomStart).offset { IntOffset(l.roundToInt(), 0) }
                        .size(with(density) { (r - l).toDp() }, 3.dp).clip(RoundedCornerShape(1.5.dp)).background(C.Accent),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                DeckTab.entries.forEach { t ->
                    val on = t == tab
                    val ink by animateColorAsState(if (on) C.Ink else C.Muted, tween(200), label = "tab ink")
                    Row(
                        Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onTab(t) }
                            .semantics(mergeDescendants = true) { contentDescription = "${t.label} tab" + if (on) ", on" else "" }
                            .padding(vertical = 12.dp)
                            .onGloballyPositioned { spans[t.ordinal] = it.positionInParent().x to it.size.width.toFloat() },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(t.label, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = ink)
                        val n = when (t) { DeckTab.Deck -> total; DeckTab.Matches -> matchCount; else -> 0 }
                        if (n > 0) Text(
                            "$n", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = C.Ink,
                            modifier = Modifier.padding(start = 6.dp).clip(RoundedCornerShape(6.dp)).background(C.Track).padding(horizontal = 6.dp, vertical = 1.dp),
                        )
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(C.Line))
    }
}

/** List / Grid (one frame), Stacked (Grid only), and the group-and-sort menu. */
@Composable
private fun DeckToolbar(
    view: DeckView, stacked: Boolean, group: DeckGroup, sort: DeckSort,
    onView: (DeckView) -> Unit, onStacked: (Boolean) -> Unit, onGroup: (DeckGroup) -> Unit, onSort: (DeckSort) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.clip(RoundedCornerShape(12.dp)).background(C.Card).border(1.dp, C.Line, RoundedCornerShape(12.dp)).padding(3.dp)) {
            ViewButton(RiftIcons.ViewList, "List view", view == DeckView.List) { onView(DeckView.List) }
            ViewButton(RiftIcons.ViewGrid, "Grid view", view == DeckView.Grid) { onView(DeckView.Grid) }
        }
        AnimatedVisibility(view == DeckView.Grid, enter = fadeIn() + scaleIn(initialScale = 0.8f), exit = fadeOut() + scaleOut(targetScale = 0.8f)) {
            Box(Modifier.clip(RoundedCornerShape(12.dp)).background(C.Card).border(1.dp, C.Line, RoundedCornerShape(12.dp)).padding(3.dp)) {
                ViewButton(RiftIcons.ViewStacked, "Stacked", stacked) { onStacked(!stacked) }
            }
        }
        Spacer(Modifier.weight(1f))
        GroupSortMenu(group, sort, onGroup, onSort)
    }
}

@Composable
private fun ViewButton(icon: ImageVector, label: String, on: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (on) C.Track else Color.Transparent, tween(180), label = "view button")
    Box(
        Modifier.size(width = 42.dp, height = 36.dp).clip(RoundedCornerShape(9.dp)).background(bg).clickable(onClick = onClick)
            .semantics { contentDescription = label + if (on) ", on" else "" },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = if (on) C.Ink else C.Muted, modifier = Modifier.size(20.dp)) }
}

/** A button the size of the view buttons ("≡ Type"); it opens group and sort as two rows of chips. */
@Composable
private fun GroupSortMenu(group: DeckGroup, sort: DeckSort, onGroup: (DeckGroup) -> Unit, onSort: (DeckSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val frame = RoundedCornerShape(12.dp)
    Box {
        Row(
            Modifier.clip(frame).background(C.Card).border(1.dp, if (open) C.Ink else C.Line, frame)
                .clickable { open = true }.semantics(mergeDescendants = true) { contentDescription = "Group and sort" }
                .padding(3.dp).height(36.dp).padding(start = 9.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(RiftIcons.Sort, null, tint = C.Muted, modifier = Modifier.size(20.dp))
            Text(if (group == DeckGroup.None) sort.label else group.label, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = C.Ink)
        }
        DropdownMenu(open, { open = false }, shape = RoundedCornerShape(16.dp), containerColor = C.Card) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("GROUP", style = Caps.copy(fontSize = 10.sp), color = C.Muted)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DeckGroup.entries.forEach { g -> MenuChip(g.label, g == group) { open = false; onGroup(g) } }
                }
                Text("SORT", style = Caps.copy(fontSize = 10.sp), color = C.Muted, modifier = Modifier.padding(top = 4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DeckSort.entries.forEach { o -> MenuChip(o.label, o == sort) { open = false; onSort(o) } }
                }
            }
        }
    }
}

@Composable
private fun MenuChip(label: String, on: Boolean, onClick: () -> Unit) = Text(
    label, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = if (on) C.OnAccent else C.Ink,
    modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(if (on) C.Accent else C.Track).clickable(onClick = onClick)
        .semantics { contentDescription = label + if (on) ", on" else "" }.padding(horizontal = 14.dp, vertical = 8.dp),
)

/** "MAIN DECK 40/40": the count green when complete (or within the limit), red when not. */
@Composable
private fun SectionHead(title: String, n: Int? = null, full: Int? = null, exact: Boolean = true, description: String? = null) {
    Row(
        Modifier.padding(top = 12.dp).then(if (description != null) Modifier.semantics(mergeDescendants = true) { contentDescription = description } else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontWeight = FontWeight.Black, fontSize = 15.sp, letterSpacing = 0.8.sp, color = C.Ink)
        if (n != null) {
            val ok = full == null || (if (exact) n == full else n <= full)
            val color = when { full == null -> C.Muted; !ok -> C.Red; exact -> C.Green; else -> C.Muted }
            RollingNumber(n, Modifier.padding(start = 8.dp)) { Text("$it", fontWeight = FontWeight.Black, fontSize = 15.sp, color = color) }
            if (full != null) Text("/$full", fontWeight = FontWeight.Black, fontSize = 15.sp, color = color)
        }
    }
}

@Composable
private fun SubHead(name: String, n: Int) =
    Text("${name.uppercase()} · $n", style = Caps.copy(fontSize = 10.sp), color = C.Muted, modifier = Modifier.padding(top = 4.dp))

private const val CHAMPION = "Champion"
private val typeOrder = listOf(CHAMPION, "Units", "Spells", "Gear", "Other")

/**
 * The main deck cut into sections by [group], each sorted by [sort]. The chosen champion is one copy
 * in its own section first; its other copies are ordinary cards in the deck.
 */
private fun sections(deck: Deck, group: DeckGroup, sort: DeckSort): List<Pair<String, List<Pair<Card, Int>>>> {
    val order = when (sort) {
        DeckSort.Energy -> compareBy<Pair<Card, Int>>({ it.first.energy ?: 99 }, { it.first.name })
        DeckSort.Name -> compareBy { it.first.name }
    }
    val champion = deck.champion?.let(Cards::bySlug)?.takeIf { deck.count(it.slug) > 0 }
    val entries = DeckRules.entries(deck).mapNotNull { (c, n) -> if (c == champion) (if (n > 1) c to n - 1 else null) else c to n }.sortedWith(order)
    val rest = when (group) {
        DeckGroup.None -> listOf("Main deck" to entries)
        DeckGroup.Cost -> entries.groupBy { it.first.energy?.coerceAtMost(7) ?: -1 }.toSortedMap()
            .map { (k, v) -> (if (k < 0) "No cost" else if (k == 7) "7+ energy" else "$k energy") to v }
        DeckGroup.Type -> entries.groupBy {
            when (it.first.type) { "unit" -> "Units"; "spell" -> "Spells"; "gear" -> "Gear"; else -> "Other" }
        }.toList().sortedBy { typeOrder.indexOf(it.first) }
    }
    return (listOfNotNull(champion?.let { CHAMPION to listOf(it to 1) }) + rest).filter { it.second.isNotEmpty() }
}

/** Card pictures, three across. Every copy is its own card unless [stacked] (then one with its count). */
@Composable
private fun CardGrid(list: List<Pair<Card, Int>>, stacked: Boolean, chosen: Boolean, onOpen: (Card) -> Unit, art: (Card) -> Printing? = { null }) {
    val tiles = if (stacked) list else list.flatMap { (c, n) -> List(n) { c to 1 } }
    tiles.chunked(3).forEach { rowCards ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            rowCards.forEach { (c, n) ->
                Box(Modifier.weight(1f)) {
                    CardImage(
                        c, Modifier.fillMaxWidth()
                            .then(if (chosen) Modifier.border(2.5.dp, C.Accent, RoundedCornerShape(8.dp)).padding(3.dp) else Modifier)
                            .shadow(2.dp, RoundedCornerShape(7.dp)).clickable { onOpen(c) }.semantics { contentDescription = "Deck card ${c.name}" },
                        printing = art(c),
                    )
                    if (stacked) CopyBadge(n, Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp), large = true)
                }
            }
            repeat(3 - rowCards.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun LegendRow(legend: Card, printing: Printing?, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.Card).border(1.dp, C.Line, RoundedCornerShape(12.dp))
            .clickable(onClick = onOpen).padding(start = 46.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CardArtThumb(legend, printing = printing)
        Text(DeckRules.championTag(legend)?.let { "$it, ${legend.name}" } ?: legend.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = C.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * A card row: cost, artwork, name, tag, ×count (nothing for one copy). Tapping it opens −/+, Move
 * and the card page underneath; the row stays clean otherwise.
 */
@Composable
private fun CardRow(
    deck: Deck, c: Card, n: Int, board: Board, chosen: Boolean, expanded: Boolean,
    onToggle: () -> Unit, onOpen: () -> Unit, onAdd: () -> Unit, onRemove: () -> Unit, onMove: () -> Unit,
) {
    val maybe = board == Board.MAYBE
    val where = if (board == Board.MAIN) "" else " ${board.label.lowercase()}"
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape)
            .then(if (maybe) Modifier else Modifier.background(if (expanded) C.Track else C.Card))
            .border(if (chosen) 1.5.dp else 1.dp, if (chosen) C.Accent else C.Line, shape)
            .graphicsLayer { alpha = if (maybe) 0.7f else 1f },
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggle).semantics(mergeDescendants = true) { contentDescription = "${c.name}$where $n" }
                .padding(start = 8.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            // Cost on the left: the energy, with the power under it.
            Column(Modifier.width(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                c.energy?.let { EnergySymbol(it, 24.dp) }
                val p = c.power ?: 0
                if (p > 0) Row { val ds = c.domainList.ifEmpty { listOf<Domain?>(null) }; repeat(p) { i -> RuneSymbol(ds[i % ds.size], 11.dp) } }
            }
            CardArtThumb(c, printing = deck.printingOf(c))
            Text(c.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = C.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            val tag = when {
                c.banned -> null
                chosen -> "CHOSEN"
                DeckRules.isSignature(c) -> "SIGNATURE"
                !maybe && !DeckRules.allowed(deck, c) -> "OFF-DOMAIN"
                else -> null
            }
            if (c.banned) BannedTag()
            else if (tag != null) Text(
                tag, fontWeight = FontWeight.Black, fontSize = 9.sp, letterSpacing = 0.3.sp,
                color = when (tag) { "CHOSEN" -> C.OnAccent; "OFF-DOMAIN" -> C.Red; else -> C.Ink },
                modifier = Modifier.clip(RoundedCornerShape(5.dp)).background(when (tag) { "CHOSEN" -> C.Accent; "OFF-DOMAIN" -> C.Red.copy(alpha = 0.18f); else -> C.Line })
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
            // The count on the right, as a solid pill; nothing for one copy.
            Box(Modifier.widthIn(min = 30.dp), contentAlignment = Alignment.CenterEnd) {
                if (n > 1) RollingNumber(n, Modifier.clip(RoundedCornerShape(8.dp)).background(if (expanded) C.Card else C.Track).padding(horizontal = 7.dp, vertical = 3.dp)) {
                    Text("×$it", fontWeight = FontWeight.Black, fontSize = 14.sp, color = C.Ink)
                }
            }
        }
        AnimatedVisibility(expanded) {
            Row(
                Modifier.padding(start = 45.dp, end = 10.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val canAdd = DeckRules.canAdd(deck, board, c)
                Row(Modifier.clip(RoundedCornerShape(10.dp)).background(C.Card), verticalAlignment = Alignment.CenterVertically) {
                    Text("−", color = C.Ink, fontWeight = FontWeight.Black, fontSize = 16.sp, modifier = Modifier.clickable(onClick = onRemove).semantics { contentDescription = "Remove ${c.name}$where" }.padding(horizontal = 12.dp, vertical = 5.dp))
                    RollingNumber(n) { Text("$it", color = C.Ink, fontWeight = FontWeight.Black, fontSize = 15.sp) }
                    Text("+", color = if (canAdd) C.Ink else C.Line, fontWeight = FontWeight.Black, fontSize = 16.sp,
                        modifier = Modifier.clickable(enabled = canAdd, onClick = onAdd).semantics { contentDescription = "Add ${c.name}$where" }.padding(horizontal = 12.dp, vertical = 5.dp))
                }
                RowChip("⇄ Move", "Move ${c.name}$where", onMove)
                RowChip("Card", "Open ${c.name}", onOpen)
            }
        }
    }
}

@Composable
private fun RowChip(text: String, description: String, onClick: () -> Unit) = Text(
    text, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = C.Ink,
    modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(C.Card).clickable(onClick = onClick)
        .semantics { contentDescription = description }.padding(horizontal = 11.dp, vertical = 6.dp),
)

/** ⇄ on a row: one copy or all of them to another board. */
@Composable
private fun MoveSheet(deck: Deck, c: Card, from: Board, onMove: (Board, Boolean) -> Unit, onOpen: () -> Unit, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).pointerInput(Unit) { detectTapGestures { onClose() } }) {
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)).background(C.Card)
                .pointerInput(Unit) { detectTapGestures { } }.navigationBarsPadding().padding(horizontal = 18.dp, vertical = 12.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(40.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(C.Line))
            Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CardThumb(c, 44.dp)
                Column {
                    Text(c.name, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = C.Ink)
                    Text("${deck.count(from, c.slug)} in ${from.label.lowercase()}", color = C.Muted, fontSize = 13.sp)
                }
            }
            Board.entries.filter { it != from }.forEach { to ->
                // Into main/side from the maybeboard, the copy limit applies.
                val possible = from != Board.MAYBE || to == Board.MAYBE || DeckRules.canAdd(deck, to, c)
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("To ${when (to) { Board.MAIN -> "main deck"; Board.SIDE -> "sideboard"; Board.MAYBE -> "maybeboard" }}", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = if (possible) C.Ink else C.Muted, modifier = Modifier.weight(1f))
                    listOf(false, true).forEach { all ->
                        Text(if (all) "All" else "1", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = if (possible) C.Ink else C.Muted,
                            modifier = Modifier.padding(start = 6.dp).clip(RoundedCornerShape(9.dp)).background(C.Track)
                                .clickable(enabled = possible) { onMove(to, all) }
                                .semantics { contentDescription = "Move ${if (all) "all" else "one"} to ${to.label.lowercase()}" }
                                .padding(horizontal = 14.dp, vertical = 6.dp))
                    }
                }
            }
            Text("Card page", color = C.Muted, fontSize = 15.sp, modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(vertical = 10.dp))
        }
    }
}

@Composable
private fun DeckHeader(deck: Deck, onBack: () -> Unit, onRename: () -> Unit, onDuplicate: () -> Unit, onArts: () -> Unit, onDelete: () -> Unit) {
    val art = legendArt(deck)
    val legend = DeckRules.legendOf(deck)
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().height(190.dp).background(C.Black)) {
        art?.let { Image(it, null, contentScale = ContentScale.Crop, alignment = BiasAlignment(0f, -0.5f), modifier = Modifier.matchParentSize()) }
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.35f), 1f to Color.Black.copy(alpha = 0.9f))))
        Row(Modifier.statusBarsPadding().fillMaxWidth().padding(horizontal = 4.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
            Spacer(Modifier.weight(1f))
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Deck options", tint = Color.White) }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; onRename() })
                    DropdownMenuItem(text = { Text("Duplicate") }, onClick = { menu = false; onDuplicate() })
                    DropdownMenuItem(text = { Text("Card arts") }, onClick = { menu = false; onArts() })
                    DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; confirmDelete = true })
                }
            }
        }
        Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            Text(deck.name, color = Color.White, fontWeight = FontWeight.Black, fontSize = 26.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            // Domain chips, then "✓ Legal" or what's wrong, like Piltover Archive.
            val problem = remember(deck) { DeckRules.problems(deck).firstOrNull() }
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                legend?.domainList.orEmpty().forEach { d ->
                    HeaderChip(d.name.lowercase().replaceFirstChar { it.uppercase() }, Color.White, Color.White.copy(alpha = 0.3f)) { RuneSymbol(d, 14.dp) }
                }
                if (problem == null) HeaderChip("✓ Legal", Color(0xFF4ADE80), Color(0xFF4ADE80).copy(alpha = 0.5f))
                else HeaderChip(problem, Color(0xFFFF8A80), Color(0xFFFF8A80).copy(alpha = 0.5f))
            }
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete this deck?") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
    )
}

@Composable
private fun HeaderChip(text: String, ink: Color, border: Color, icon: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = 0.4f)).border(1.dp, border, RoundedCornerShape(14.dp))
            .padding(horizontal = 9.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        icon?.invoke()
        Text(text, color = ink, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

@Composable
private fun EnergyCurve(deck: Deck, height: Dp = 64.dp, showAverage: Boolean = false) {
    val curve = DeckRules.energyCurve(deck)
    val max = (curve.maxOrNull() ?: 0).coerceAtLeast(1)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.Card).padding(horizontal = 12.dp, vertical = 10.dp)) {
        if (showAverage) Row(verticalAlignment = Alignment.CenterVertically) {
            Text("ENERGY", style = Caps, color = C.Muted, modifier = Modifier.weight(1f))
            Text("avg ", style = Caps, color = C.Muted)
            val avg by animateFloatAsState(DeckRules.averageEnergy(deck).toFloat(), tween(400), label = "avg")
            Text("%.1f".format(Locale.ENGLISH, avg), fontWeight = FontWeight.Black, fontSize = 14.sp, color = C.Ink)
        }
        Row(Modifier.fillMaxWidth().height(height).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
            curve.forEachIndexed { i, n ->
                // Bars grow and shrink as cards come and go; the count sits on top of each.
                val f by animateFloatAsState(n.toFloat() / max, spring(dampingRatio = 0.7f, stiffness = 300f), label = "bar")
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                        if (n > 0) Text("$n", color = C.Ink, fontSize = 12.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(bottom = 3.dp))
                        Box(Modifier.fillMaxWidth().weight(1f, fill = false)) {
                            if (f > 0.005f) Box(Modifier.fillMaxWidth().fillMaxHeight(f.coerceIn(0f, 1f)).align(Alignment.BottomCenter).clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp)).background(C.Accent))
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(C.Line))
                    Text(if (i == 7) "7+" else "$i", color = C.Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

/** "×3" on a card in the deck's grid and stacks: white on translucent black, nothing for a single copy. */
@Composable
private fun CopyBadge(n: Int, modifier: Modifier = Modifier, large: Boolean = false) {
    if (n > 1) Text(
        "×$n", color = Color.White, fontWeight = FontWeight.Black, fontSize = if (large) 11.sp else 9.sp, lineHeight = if (large) 13.sp else 11.sp,
        modifier = modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.72f))
            .padding(horizontal = if (large) 7.dp else 5.dp, vertical = if (large) 2.dp else 1.dp)
            .semantics { contentDescription = "$n copies" },
    )
}

/** "3×" on a card; pops when the number changes. */
@Composable
fun CountBadge(n: Int, modifier: Modifier = Modifier) {
    val scale = remember { Animatable(1f) }
    var last by remember { mutableStateOf(n) }
    LaunchedEffect(n) {
        if (n != last) { last = n; scale.snapTo(1.45f); scale.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 600f)) }
    }
    if (n > 0) Text(
        "$n×", color = C.OnAccent, fontWeight = FontWeight.Black, fontSize = 13.sp,
        modifier = modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value }.clip(RoundedCornerShape(9.dp))
            .background(C.Accent).padding(horizontal = 7.dp, vertical = 2.dp).semantics { contentDescription = "$n in deck" },
    )
}

/** 12 runes as one bar: + on a side takes one from the other; the divider can be dragged. Animated. */
@Composable
private fun RuneSplit(deck: Deck, edit: ((Deck) -> Deck) -> Unit) {
    val ds = DeckRules.domains(deck)
    if (ds.size < 2) return
    val (a, b) = ds
    val na = deck.runesOf(a); val nb = deck.runesOf(b)
    val split by animateFloatAsState(na / DeckRules.RUNES.toFloat(), spring(dampingRatio = 0.6f, stiffness = 400f), label = "rune split")
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.Card).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RuneSymbol(a, 24.dp)
            RollingNumber(na, Modifier.padding(start = 6.dp).semantics(mergeDescendants = true) { contentDescription = "${a.name.lowercase()} runes $na" }) {
                Text("$it", fontWeight = FontWeight.Black, fontSize = 24.sp, color = C.Ink)
            }
            Spacer(Modifier.weight(1f))
            RollingNumber(nb, Modifier.padding(end = 6.dp).semantics(mergeDescendants = true) { contentDescription = "${b.name.lowercase()} runes $nb" }) {
                Text("$it", fontWeight = FontWeight.Black, fontSize = 24.sp, color = C.Ink)
            }
            RuneSymbol(b, 24.dp)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PlusButton("More ${a.name.lowercase()} runes") { edit { DeckRules.addRune(it, a) } }
            BoxWithConstraints(Modifier.weight(1f).height(30.dp)) {
                val w = constraints.maxWidth.toFloat()
                val density = LocalDensity.current
                fun setAt(x: Float) = edit { DeckRules.setRunes(it, (x / w * DeckRules.RUNES).roundToInt()) }
                Row(
                    Modifier.fillMaxWidth().height(20.dp).align(Alignment.Center).clip(RoundedCornerShape(10.dp))
                        .pointerInput(w) { detectTapGestures { setAt(it.x) } }
                        .pointerInput(w) { detectHorizontalDragGestures { ch, _ -> ch.consume(); setAt(ch.position.x) } },
                ) {
                    if (split > 0.001f) Box(Modifier.weight(split).fillMaxHeight().background(Color(a.argb)))
                    if (split < 0.999f) Box(Modifier.weight(1f - split).fillMaxHeight().background(Color(b.argb)))
                }
                val x = with(density) { (w * split).toDp() }
                Box(Modifier.offset(x = x - 3.dp).size(6.dp, 30.dp).clip(RoundedCornerShape(3.dp)).background(Color.White))
            }
            PlusButton("More ${b.name.lowercase()} runes") { edit { DeckRules.addRune(it, b) } }
        }
    }
}

@Composable
private fun PlusButton(description: String, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Text("+", fontWeight = FontWeight.Black, fontSize = 20.sp, color = C.Ink, textAlign = TextAlign.Center,
        modifier = Modifier.width(44.dp).squishWhenPressed(source).clip(RoundedCornerShape(10.dp)).background(C.Track)
            .clickable(interactionSource = source, indication = ripple(), onClick = onClick).semantics { contentDescription = description }.padding(vertical = 4.dp))
}

/** Battlefields with their picture and what they do. */
@Composable
private fun FieldSlots(deck: Deck, onPick: (Int) -> Unit) {
    deck.battlefields.forEachIndexed { i, name -> BattlefieldCard(name) { onPick(i) } }
    if (deck.battlefields.size < DeckRules.BATTLEFIELDS) {
        Text("+ Battlefield", fontWeight = FontWeight.ExtraBold, color = C.Muted, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.5.dp, C.Line, RoundedCornerShape(12.dp)).clickable { onPick(deck.battlefields.size) }.padding(20.dp))
    }
}

@Composable
private fun DeckStats(deck: Deck, edit: ((Deck) -> Deck) -> Unit) {
    EnergyCurve(deck, 140.dp, showAverage = true)
    val ds = DeckRules.domains(deck)
    if (ds.size == 2) {
        val p = DeckRules.powerByDomain(deck)
        val pa = (p[ds[0]] ?: 0.0).roundToInt(); val pb = (p[ds[1]] ?: 0.0).roundToInt()
        val share by animateFloatAsState(if (pa + pb == 0) 0.5f else pa / (pa + pb).toFloat(), tween(400), label = "power")
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.Card).padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row {
                Text("POWER", style = Caps, color = C.Muted, modifier = Modifier.weight(1f))
                Text("${pa + pb}", fontWeight = FontWeight.Black, fontSize = 14.sp, color = C.Ink)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                RuneSymbol(ds[0], 20.dp)
                Text("$pa", fontWeight = FontWeight.Black, color = C.Ink)
                Row(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(C.Track), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (pa + pb > 0 && share > 0.001f) Box(Modifier.weight(share).fillMaxHeight().background(Color(ds[0].argb)))
                    if (pa + pb > 0 && share < 0.999f) Box(Modifier.weight(1f - share).fillMaxHeight().background(Color(ds[1].argb)))
                }
                Text("$pb", fontWeight = FontWeight.Black, color = C.Ink)
                RuneSymbol(ds[1], 20.dp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Runes ${deck.runesOf(ds[0])} · ${deck.runesOf(ds[1])}", color = C.Muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text("Match", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = C.Ink,
                    modifier = Modifier.clip(RoundedCornerShape(9.dp)).border(1.5.dp, C.Line, RoundedCornerShape(9.dp)).clickable { edit { DeckRules.matchRunes(it) } }.padding(horizontal = 12.dp, vertical = 5.dp))
            }
        }
    }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf<String?>(null) }
    val code = remember(deck) { runCatching { DeckCodes.fromDeck(deck) }.getOrNull() }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOfNotNull(("Copy deck code" to code).takeIf { code != null }, "Copy as text" to DeckRules.toText(deck)).forEach { (label, value) ->
            Text(if (copied == label) "Copied" else label, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = C.Ink, textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).border(1.5.dp, C.Line, RoundedCornerShape(12.dp))
                    .clickable { val text = value!!; scope.launch { clipboard.setClipEntry(ClipData.newPlainText(label, text).toClipEntry()) }; copied = label }.padding(12.dp))
        }
    }
}

/** Full-screen overlays (not dialogs: Robolectric's text-field quirk). */
@Composable
private fun Overlay(title: String, onClose: () -> Unit, content: @Composable () -> Unit) {
    BackHandler(onBack = onClose)
    Column(Modifier.fillMaxSize().background(C.Paper).pointerInput(Unit) { detectTapGestures { } }) {
        HeaderBlock(onBack = onClose) { Text(title, color = Color.White, fontWeight = FontWeight.Black, fontSize = 26.sp) }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
    }
}

@Composable
private fun RenameOverlay(current: String, onClose: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(current) }
    Overlay("Rename", onClose) {
        RiftTextField("Deck name", name, { name = it })
        BigButton("SAVE", C.Solid, C.OnSolid) { if (name.isNotBlank()) onSave(name.trim()) }
    }
}

@Composable
private fun PasteOverlay(onClose: () -> Unit, onImport: (Deck) -> Unit) {
    var text by remember { mutableStateOf("") }
    val parsed = remember(text) {
        when {
            text.isBlank() -> null
            // A deck code (Piltover Archive's format, used by most deck sites), else a card list.
            DeckCodeFormat.looksLikeCode(text) -> runCatching { DeckCodes.toDeck(text.trim(), "Imported deck") }.getOrElse { null to listOf(text.trim()) }
            else -> DeckRules.fromText(text, "Imported deck")
        }
    }
    Overlay("Import", onClose) {
        OutlinedTextField(
            text, { text = it }, label = { Text("Deck code or list") }, minLines = 8, maxLines = 14,
            shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(),
        )
        parsed?.second?.takeIf { it.isNotEmpty() }?.let { Text("${it.size} not found", color = C.Muted, fontSize = 13.sp) }
        val deck = parsed?.first
        BigButton("IMPORT", if (deck != null) C.Solid else C.Track, if (deck != null) C.OnSolid else C.Muted) { if (deck != null) onImport(deck) }
    }
}

/**
 * Card arts (from the deck's menu, owner's pick): the deck's cards that have other printings, each
 * with its printings side by side; the deck's printing has a ✓, tapping another uses it in the deck.
 */
@Composable
fun DeckArtsScreen(vm: MainViewModel, id: Long) {
    val drafts by vm.deckDrafts.collectAsState()
    val decks by vm.decks.collectAsState()
    val deck = drafts[id] ?: decks.firstOrNull { it.id == id } ?: return
    val cards = remember(deck.legend, deck.main.keys, deck.side.keys, deck.maybe.keys) {
        (listOf(deck.legend) + deck.main.keys + deck.side.keys + deck.maybe.keys).distinct()
            .mapNotNull(Cards::bySlug).filter { it.printings.size > 1 }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        HeaderBlock(onBack = { vm.back() }) {
            Text("Card arts", color = Color.White, fontWeight = FontWeight.Black, fontSize = 30.sp)
            Text(deck.name, color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        if (cards.isEmpty()) EmptyState("One art each", "", Modifier.fillMaxWidth())
        cards.forEach { c ->
            Text(c.name, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = C.Ink, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp))
            PrintingCarousel(c, selected = deck.printingOf(c), check = true) { p ->
                vm.editDeck(id) { d -> d.copy(arts = if (p == c.printings.first()) d.arts - c.slug else d.arts + (c.slug to p.code)) }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Deck value on Cardmarket at the chosen arts: the total, the priciest cards, the rest behind "All cards". */
@Composable
private fun DeckValue(deck: Deck) {
    val value = remember(deck, Prices.byCode) { Prices.deckValue(deck) } ?: return
    val (total, rows) = value
    var all by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.Card).padding(horizontal = 14.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("DECK VALUE", style = Caps, color = C.Muted, modifier = Modifier.weight(1f))
            Icon(RiftIcons.Cardmarket, "Cardmarket", tint = C.Muted, modifier = Modifier.size(14.dp))
        }
        Text(Prices.format(total), fontWeight = FontWeight.Black, fontSize = 30.sp, color = C.Ink, modifier = Modifier.padding(top = 2.dp, bottom = 6.dp).semantics { contentDescription = "Deck value ${Prices.format(total)}" })
        (if (all) rows else rows.take(4)).forEach { (c, n, p) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CardArtThumb(c, 44.dp, 30.dp, printing = deck.printingOf(c))
                Text(c.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = C.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (n > 1) Text("×$n", fontSize = 12.sp, color = C.Muted)
                Text(Prices.format(n * p.eur), fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = C.Ink)
            }
        }
        if (rows.size > 4) Text(
            if (all) "Fewer" else "All cards", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = C.Ink, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { all = !all }.padding(vertical = 8.dp),
        )
    }
}
