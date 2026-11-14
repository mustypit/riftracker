package io.github.mustypit.rifttracker.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mustypit.rifttracker.game.Card
import io.github.mustypit.rifttracker.game.Cards
import io.github.mustypit.rifttracker.game.Deck
import io.github.mustypit.rifttracker.game.DeckRules
import io.github.mustypit.rifttracker.game.Domain
import io.github.mustypit.rifttracker.game.Legends
import kotlin.math.roundToInt

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
@Composable
fun DeckCounts(deck: Deck, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(
            Triple("Main", deck.mainSize, DeckRules.MAIN_SIZE),
            Triple("Runes", deck.runeCount, DeckRules.RUNES),
            Triple("Fields", deck.battlefields.distinctBy { it.lowercase() }.size, DeckRules.BATTLEFIELDS),
        ).forEach { (label, n, full) ->
            val ok = n == full
            Row(
                Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (ok) C.Track else C.Red.copy(alpha = 0.18f)).padding(vertical = 6.dp)
                    .semantics(mergeDescendants = true) { contentDescription = "$label $n of $full" },
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom,
            ) {
                RollingNumber(n) { Text("$it", fontWeight = FontWeight.Black, fontSize = 14.sp, color = if (ok) C.Ink else C.Red) }
                Text("/$full $label", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = if (ok) C.Muted else C.Red, maxLines = 1)
            }
        }
    }
}

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
            items(shown, key = { it.id }) { d -> DeckRow(d, io.github.mustypit.rifttracker.stats.Stats.deckMatches(history, d.id)) { vm.push(Screen.DeckDetail(d.id)) } }
            item {
                Text(
                    "+ New deck", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = C.Muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .border(1.5.dp, C.Line, RoundedCornerShape(16.dp)).clickable { vm.push(Screen.NewDeck) }.padding(18.dp),
                )
                Text(
                    "Paste a list", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = C.Muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clickable { pasting = true }.padding(16.dp),
                )
            }
        }
        if (pasting) PasteOverlay(onClose = { pasting = false }) { deck -> pasting = false; vm.createDeck(deck, replaceTop = false) }
    }
}

@Composable
private fun DeckRow(d: Deck, matches: List<io.github.mustypit.rifttracker.game.Match>, onClick: () -> Unit) {
    val art = legendArt(d)
    val problem = DeckRules.problems(d).firstOrNull()
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
                    val rec = io.github.mustypit.rifttracker.stats.Stats.record(matches)
                    Text(rec.line, fontWeight = FontWeight.Black, fontSize = 18.sp, color = Color.White, style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Color.Black, blurRadius = 8f)))
                    if (problem == null) {
                        Text(io.github.mustypit.rifttracker.stats.Stats.rateOrHint(rec), fontSize = 11.sp, color = Color.White.copy(alpha = 0.85f))
                        FormDots(io.github.mustypit.rifttracker.stats.Stats.form(matches))
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
private fun FormDots(form: List<io.github.mustypit.rifttracker.game.Outcome>, modifier: Modifier = Modifier) = Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
    form.reversed().forEach { o -> Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(C.outcome(o))) }
}

/** The deck's results: record, going first/second, by opponent legend, latest matches. */
@Composable
private fun DeckRecord(vm: MainViewModel, deck: Deck) {
    val history by vm.history.collectAsState()
    val matches = remember(history, deck.id) { io.github.mustypit.rifttracker.stats.Stats.deckMatches(history, deck.id) }
    if (matches.isEmpty()) return
    val legend = matches.first().myLegend
    val detail = remember(matches) { io.github.mustypit.rifttracker.stats.Stats.legendDetail(matches, legend, latestCount = 3) }
    val rec = detail.record
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.Card).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(rec.line, fontWeight = FontWeight.Black, fontSize = 26.sp, color = C.Ink, modifier = Modifier.semantics { contentDescription = "Deck record ${rec.line}" })
            Text("  " + listOfNotNull(rec.winRate?.takeIf { rec.total >= io.github.mustypit.rifttracker.stats.Stats.MIN_MATCHES_FOR_RATE }?.let { io.github.mustypit.rifttracker.stats.Stats.percent(it) }, "${rec.total} ${if (rec.total == 1) "match" else "matches"}").joinToString(" · "), color = C.Muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 5.dp))
        }
        Row(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            if (rec.wins > 0) Box(Modifier.weight(rec.wins.toFloat()).fillMaxHeight().background(C.Green))
            if (rec.draws > 0) Box(Modifier.weight(rec.draws.toFloat()).fillMaxHeight().background(C.DrawGrey))
            if (rec.losses > 0) Box(Modifier.weight(rec.losses.toFloat()).fillMaxHeight().background(C.Red))
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(
            "Going first" to io.github.mustypit.rifttracker.stats.Stats.percent(detail.goingFirst.winRate),
            "Going second" to io.github.mustypit.rifttracker.stats.Stats.percent(detail.goingSecond.winRate),
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
    Text("LATEST", style = Caps, color = C.Muted, modifier = Modifier.padding(top = 4.dp))
    Column(Modifier.fullBleed(16.dp)) {
        detail.latest.forEach { m -> MatchRow(m, onClick = { vm.push(Screen.MatchDetail(m.id)) }, showMyLegend = false) }
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

/** How the main deck is shown (Moxfield-style View · Group · Sort). */
enum class DeckView(val label: String) { List("List"), Grid("Grid"), Stacks("Stacks"), Split("Stacks (split)") }
enum class DeckGroup(val label: String) { Type("Type"), Cost("Cost"), None("None") }
enum class DeckSort(val label: String) { Energy("Energy"), Name("Name") }

/**
 * A deck, one scrolling page (owner's layout): counts, runes, the main deck in a chosen view,
 * battlefields with their text, then stats. "+ Add cards" floats at the bottom.
 */
@Composable
fun DeckScreen(vm: MainViewModel, id: Long) {
    val drafts by vm.deckDrafts.collectAsState()
    val decks by vm.decks.collectAsState()
    val deck = drafts[id] ?: decks.firstOrNull { it.id == id } ?: return
    val view by vm.deckView.collectAsState()
    val group by vm.deckGroup.collectAsState()
    val sort by vm.deckSort.collectAsState()
    var renaming by remember { mutableStateOf(false) }
    var pickingField by remember { mutableStateOf<Int?>(null) }
    fun edit(f: (Deck) -> Deck) = vm.editDeck(id, f)

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 96.dp)) {
            DeckHeader(deck, onBack = { vm.back() }, onRename = { renaming = true }, onDuplicate = { vm.duplicateDeck(id) }, onDelete = { vm.deleteDeck(id) })
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp).animateContentSize(spring(stiffness = 500f)), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DeckRecord(vm, deck)
                DeckCounts(deck)
                // Toolbar: View · Group · Sort, each a dropdown.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToolbarMenu("View", view.label, DeckView.entries, { it.label }, vm.deckView::value::set, Modifier.weight(1.25f))
                    ToolbarMenu("Group", group.label, DeckGroup.entries, { it.label }, vm.deckGroup::value::set, Modifier.weight(1f))
                    ToolbarMenu("Sort", sort.label, DeckSort.entries, { it.label }, vm.deckSort::value::set, Modifier.weight(1f))
                }
                if (deck.main.size <= 1) EmptyState("Add cards", "Tap + Add cards.", Modifier.fillMaxWidth())
                androidx.compose.animation.Crossfade(Triple(view, group, sort), animationSpec = tween(220), label = "deck view") { (v, g, so) ->
                    val sections = sections(deck, g, so)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        when (v) {
                            DeckView.List -> sections.forEach { (name, list) ->
                                GroupLabel(name, list)
                                list.forEach { (c, n) ->
                                    androidx.compose.runtime.key(c.slug) {
                                        DeckCardRow(deck, c, n, onOpen = { vm.push(Screen.CardDetail(c.slug)) }, onAdd = { edit { DeckRules.add(it, c) } }, onRemove = { edit { DeckRules.remove(it, c) } })
                                    }
                                }
                            }
                            DeckView.Grid -> sections.forEach { (name, list) -> GroupLabel(name, list); CardGrid(deck, list, vm) }
                            DeckView.Stacks -> StackColumns(sections, vm, everyCopy = false)
                            DeckView.Split -> StackColumns(sections, vm, everyCopy = true)
                        }
                    }
                }
                Text("RUNES", style = Caps, color = C.Muted, modifier = Modifier.padding(top = 8.dp))
                RuneSplit(deck, ::edit)
                Text("BATTLEFIELDS", style = Caps, color = C.Muted, modifier = Modifier.padding(top = 8.dp))
                FieldSlots(deck) { pickingField = it }
                Text("STATS", style = Caps, color = C.Muted, modifier = Modifier.padding(top = 8.dp))
                DeckStats(deck, ::edit)
            }
        }
        Text(
            "+ Add cards", fontWeight = FontWeight.Black, fontSize = 16.sp, color = C.OnAccent,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp).squishWhenPressed(remember { androidx.compose.foundation.interaction.MutableInteractionSource() })
                .shadow(8.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)).background(C.Accent)
                .clickable { vm.push(Screen.DeckAddCards(id)) }.padding(horizontal = 26.dp, vertical = 14.dp),
        )
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
    }
}

@Composable
private fun DeckHeader(deck: Deck, onBack: () -> Unit, onRename: () -> Unit, onDuplicate: () -> Unit, onDelete: () -> Unit) {
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
                    DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; confirmDelete = true })
                }
            }
        }
        Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            Text(deck.name, color = Color.White, fontWeight = FontWeight.Black, fontSize = 26.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DomainIcons(legend?.domainList.orEmpty(), 16.dp)
                Text(legend?.name.orEmpty(), color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
            }
        }
    }
    if (confirmDelete) androidx.compose.material3.AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete this deck?") },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete") } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
    )
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
            Text("%.1f".format(java.util.Locale.ENGLISH, avg), fontWeight = FontWeight.Black, fontSize = 14.sp, color = C.Ink)
        }
        Row(Modifier.fillMaxWidth().height(height).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
            curve.forEachIndexed { i, n ->
                // Bars grow and shrink as cards come and go.
                val f by animateFloatAsState(n.toFloat() / max, spring(dampingRatio = 0.7f, stiffness = 300f), label = "bar")
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                        if (f > 0.005f) Box(Modifier.fillMaxWidth().fillMaxHeight(f.coerceIn(0f, 1f)).clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)).background(C.Accent))
                    }
                    Text(if (i == 7) "7+" else "$i", color = C.Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private val typeOrder = listOf("Champion", "Units", "Spells", "Gear", "Other")

/** The main deck cut into sections by [group], each sorted by [sort]. */
private fun sections(deck: Deck, group: DeckGroup, sort: DeckSort): List<Pair<String, List<Pair<Card, Int>>>> {
    val order = when (sort) {
        DeckSort.Energy -> compareBy<Pair<Card, Int>>({ it.first.energy ?: 99 }, { it.first.name })
        DeckSort.Name -> compareBy { it.first.name }
    }
    val entries = DeckRules.entries(deck).sortedWith(order)
    return when (group) {
        DeckGroup.None -> listOf("Main deck" to entries)
        DeckGroup.Cost -> entries.groupBy { it.first.energy?.coerceAtMost(7) ?: -1 }.toSortedMap()
            .map { (k, v) -> (if (k < 0) "No cost" else if (k == 7) "7+ energy" else "$k energy") to v }
        DeckGroup.Type -> entries.groupBy {
            when {
                it.first.slug == deck.champion -> "Champion"
                it.first.type == "unit" -> "Units"; it.first.type == "spell" -> "Spells"; it.first.type == "gear" -> "Gear"
                else -> "Other"
            }
        }.toList().sortedBy { typeOrder.indexOf(it.first) }
    }.filter { it.second.isNotEmpty() }
}

/** "Label ▾ value" dropdown, as on Moxfield's deck toolbar. */
@Composable
private fun <T> ToolbarMenu(label: String, value: String, options: List<T>, name: (T) -> String, onPick: (T) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.Card)
                .border(if (open) 2.dp else 1.5.dp, if (open) C.Ink else C.Line, RoundedCornerShape(12.dp))
                .clickable { open = true }.semantics(mergeDescendants = true) { contentDescription = label }
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text(label.uppercase(), style = Caps.copy(fontSize = 10.sp), color = C.Muted)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = C.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text("▾", color = C.Muted, fontSize = 12.sp)
            }
        }
        DropdownMenu(open, { open = false }) {
            options.forEach { o -> DropdownMenuItem(text = { Text(name(o), fontWeight = if (name(o) == value) FontWeight.ExtraBold else FontWeight.Normal) }, onClick = { open = false; onPick(o) }) }
        }
    }
}

@Composable
private fun GroupLabel(name: String, list: List<Pair<Card, Int>>) = Row(Modifier.padding(top = 4.dp)) {
    Text(name.uppercase(), style = Caps, color = C.Muted, modifier = Modifier.weight(1f))
    Text("${list.sumOf { it.second }}", style = Caps, color = C.Muted)
}

/** Card pictures, three across, each with its copy count. */
@Composable
private fun CardGrid(deck: Deck, list: List<Pair<Card, Int>>, vm: MainViewModel) {
    list.chunked(3).forEach { rowCards ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            rowCards.forEach { (c, n) ->
                Box(Modifier.weight(1f)) {
                    CardImage(c, Modifier.fillMaxWidth().shadow(2.dp, RoundedCornerShape(7.dp)).clickable { vm.push(Screen.CardDetail(c.slug)) }.semantics { contentDescription = "Deck card ${c.name}" })
                    CountBadge(n, Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp))
                }
            }
            repeat(3 - rowCards.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/** "3×" on a card; pops when the number changes. */
@Composable
fun CountBadge(n: Int, modifier: Modifier = Modifier) {
    val scale = remember { androidx.compose.animation.core.Animatable(1f) }
    var last by remember { mutableStateOf(n) }
    androidx.compose.runtime.LaunchedEffect(n) {
        if (n != last) { last = n; scale.snapTo(1.45f); scale.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 600f)) }
    }
    if (n > 0) Text(
        "$n×", color = C.OnAccent, fontWeight = FontWeight.Black, fontSize = 13.sp,
        modifier = modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value }.clip(RoundedCornerShape(9.dp))
            .background(C.Accent).padding(horizontal = 7.dp, vertical = 2.dp).semantics { contentDescription = "$n in deck" },
    )
}

/**
 * Moxfield's visual stacks: a column per section, each card's top strip showing under the next, the
 * last card whole. [everyCopy] (split) shows each copy; otherwise one card with its count.
 */
@Composable
private fun StackColumns(sections: List<Pair<String, List<Pair<Card, Int>>>>, vm: MainViewModel, everyCopy: Boolean) {
    sections.chunked(3).forEach { chunk ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            chunk.forEach { (name, list) ->
                Column(Modifier.weight(1f)) {
                    Text("$name (${list.sumOf { it.second }})", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, color = C.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 6.dp))
                    val cards = if (everyCopy) list.flatMap { (c, n) -> List(n) { c to 1 } } else list
                    Box(Modifier.fillMaxWidth()) {
                        cards.forEachIndexed { i, (c, n) ->
                            Box(Modifier.padding(top = (19 * i).dp).fillMaxWidth()) {
                                CardImage(c, Modifier.fillMaxWidth().shadow(2.dp, RoundedCornerShape(5.dp)).clickable { vm.push(Screen.CardDetail(c.slug)) }, corner = 5.dp)
                                if (!everyCopy) Text(
                                    "$n", color = C.Ink, fontWeight = FontWeight.Black, fontSize = 11.sp,
                                    modifier = Modifier.offset(x = (-4).dp, y = 1.dp).clip(RoundedCornerShape(5.dp)).background(C.Track).padding(horizontal = 5.dp, vertical = 1.dp),
                                )
                            }
                        }
                    }
                }
            }
            repeat(3 - chunk.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun DeckCardRow(deck: Deck, c: Card, n: Int, onOpen: () -> Unit, onAdd: () -> Unit, onRemove: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.Card).clickable(onClick = onOpen).padding(start = 8.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.width(30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            c.energy?.let { EnergySymbol(it, 22.dp) }
            val p = c.power ?: 0
            if (p > 0) Row { val ds = c.domainList.ifEmpty { listOf<Domain?>(null) }; repeat(p) { i -> RuneSymbol(ds[i % ds.size], 12.dp) } }
        }
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(7.dp))) {
            CardImage(c, Modifier.fillMaxWidth().aspectRatio(744f / 1039f).offset(y = (-4).dp).graphicsScale(2.1f), corner = 0.dp, showBanned = false)
        }
        Column(Modifier.weight(1f)) {
            Text(c.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = C.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val tag = when { c.slug == deck.champion -> "Chosen"; DeckRules.isSignature(c) -> "Signature"; !DeckRules.allowed(deck, c) -> "Off-domain"; else -> null }
            if (c.banned) BannedTag(Modifier.padding(top = 2.dp))
            else if (tag != null) Text(
                tag, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp,
                color = when (tag) { "Chosen" -> C.OnAccent; "Off-domain" -> C.Red; else -> C.Ink },
                modifier = Modifier.padding(top = 2.dp).clip(RoundedCornerShape(6.dp)).background(when (tag) { "Chosen" -> C.Accent; "Off-domain" -> C.Red.copy(alpha = 0.18f); else -> C.Line }).padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
        Row(Modifier.clip(RoundedCornerShape(10.dp)).background(C.Track), verticalAlignment = Alignment.CenterVertically) {
            Text("−", color = C.Muted, fontWeight = FontWeight.Black, fontSize = 16.sp, modifier = Modifier.clickable(onClick = onRemove).semantics { contentDescription = "Remove ${c.name}" }.padding(horizontal = 10.dp, vertical = 4.dp))
            RollingNumber(n, Modifier.semantics(mergeDescendants = true) { contentDescription = "${c.name} $n" }) { Text("$it", color = C.Ink, fontWeight = FontWeight.Black, fontSize = 15.sp) }
            Text("+", color = if (n < DeckRules.maxCopies(c)) C.Muted else C.Line, fontWeight = FontWeight.Black, fontSize = 16.sp,
                modifier = Modifier.clickable(enabled = n < DeckRules.maxCopies(c), onClick = onAdd).semantics { contentDescription = "Add ${c.name}" }.padding(horizontal = 10.dp, vertical = 4.dp))
        }
    }
}

private fun Modifier.graphicsScale(s: Float) = this.graphicsLayer { scaleX = s; scaleY = s; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.12f) }

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
    val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Text("+", fontWeight = FontWeight.Black, fontSize = 20.sp, color = C.Ink, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = Modifier.width(44.dp).squishWhenPressed(source).clip(RoundedCornerShape(10.dp)).background(C.Track)
            .clickable(interactionSource = source, indication = androidx.compose.material3.ripple(), onClick = onClick).semantics { contentDescription = description }.padding(vertical = 4.dp))
}

/** Battlefields with their picture and what they do. */
@Composable
private fun FieldSlots(deck: Deck, onPick: (Int) -> Unit) {
    deck.battlefields.forEachIndexed { i, name ->
        val card = remember(name, Cards.all) { DeckRules.battlefieldCard(name) }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.Card).clickable { onPick(i) }
                .semantics(mergeDescendants = true) { contentDescription = "Battlefield $name" },
        ) {
            Box(Modifier.fillMaxWidth().height(64.dp)) {
                BattlefieldThumb(name, Modifier.matchParentSize(), corner = 0)
                Box(Modifier.matchParentSize().background(Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.65f), 1f to Color.Transparent)))
                Text(name, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, modifier = Modifier.align(Alignment.BottomStart).padding(12.dp))
                if (card?.banned == true) BannedTag(Modifier.align(Alignment.TopEnd).padding(8.dp))
            }
            if (card != null && card.text.isNotBlank()) RulesTextView(card.text, Modifier.padding(12.dp), fontSize = 14.sp)
        }
    }
    if (deck.battlefields.size < DeckRules.BATTLEFIELDS) {
        Text("+ Battlefield", fontWeight = FontWeight.ExtraBold, color = C.Muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.5.dp, C.Line, RoundedCornerShape(12.dp)).clickable { onPick(deck.battlefields.size) }.padding(20.dp))
    }
}

@Composable
private fun DeckStats(deck: Deck, edit: ((Deck) -> Deck) -> Unit) {
    EnergyCurve(deck, 52.dp, showAverage = true)
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
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    Text(if (copied) "Copied" else "Copy as text", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = C.Ink, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.5.dp, C.Line, RoundedCornerShape(12.dp))
            .clickable { clipboard.setText(AnnotatedString(DeckRules.toText(deck))); copied = true }.padding(12.dp))
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
    val parsed = remember(text) { if (text.isBlank()) null else DeckRules.fromText(text, "Imported deck") }
    Overlay("Paste a list", onClose) {
        androidx.compose.material3.OutlinedTextField(
            text, { text = it }, label = { Text("Deck list") }, minLines = 8, maxLines = 14,
            shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(),
        )
        parsed?.second?.takeIf { it.isNotEmpty() }?.let { Text("${it.size} not found", color = C.Muted, fontSize = 13.sp) }
        val deck = parsed?.first
        BigButton("IMPORT", if (deck != null) C.Solid else C.Track, if (deck != null) C.OnSolid else C.Muted) { if (deck != null) onImport(deck) }
    }
}
