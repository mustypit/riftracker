package io.github.mustypit.rifttracker.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
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
                Text("$n", fontWeight = FontWeight.Black, fontSize = 14.sp, color = if (ok) C.Ink else C.Red)
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
    Box(modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            item { HeaderBlock { Text("Decks", color = Color.White, fontWeight = FontWeight.Black, fontSize = 30.sp) } }
            items(shown, key = { it.id }) { d -> DeckRow(d) { vm.push(Screen.DeckDetail(d.id)) } }
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
private fun DeckRow(d: Deck, onClick: () -> Unit) {
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
            if (problem != null) Text(
                problem, color = C.Red, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFF2A1513)).padding(horizontal = 8.dp, vertical = 3.dp),
            )
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
                    CardImage(legend, Modifier.width(46.dp), corner = 5.dp)
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

private enum class DeckTab(val label: String) { Main("Main"), Runes("Runes"), Fields("Fields"), Stats("Stats") }

@Composable
fun DeckScreen(vm: MainViewModel, id: Long) {
    val drafts by vm.deckDrafts.collectAsState()
    val decks by vm.decks.collectAsState()
    val deck = drafts[id] ?: decks.firstOrNull { it.id == id } ?: return
    var tab by rememberSaveable { mutableStateOf(DeckTab.Main) }
    var renaming by remember { mutableStateOf(false) }
    var pickingField by remember { mutableStateOf<Int?>(null) }
    fun edit(f: (Deck) -> Deck) = vm.editDeck(id, f)

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 96.dp)) {
            DeckHeader(deck, onBack = { vm.back() }, onRename = { renaming = true }, onDuplicate = { vm.duplicateDeck(id) }, onDelete = { vm.deleteDeck(id) })
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DeckCounts(deck)
                Segmented(DeckTab.entries, tab, { it.label }, { tab = it })
                when (tab) {
                    DeckTab.Main -> MainList(deck, vm, ::edit)
                    DeckTab.Runes -> RuneSplit(deck, ::edit)
                    DeckTab.Fields -> FieldSlots(deck, { pickingField = it }, ::edit)
                    DeckTab.Stats -> DeckStats(deck, ::edit)
                }
            }
        }
        if (tab == DeckTab.Main) {
            Text(
                "+ Add cards", fontWeight = FontWeight.Black, fontSize = 16.sp, color = C.OnAccent,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp).shadow(8.dp, RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp)).background(C.Accent).clickable { vm.push(Screen.DeckAddCards(id)) }.padding(horizontal = 26.dp, vertical = 14.dp),
            )
        }
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
        if (showAverage) Row {
            Text("ENERGY", style = Caps, color = C.Muted, modifier = Modifier.weight(1f))
            Text("avg ", style = Caps, color = C.Muted)
            Text("%.1f".format(java.util.Locale.ENGLISH, DeckRules.averageEnergy(deck)), fontWeight = FontWeight.Black, fontSize = 14.sp, color = C.Ink)
        }
        Row(Modifier.fillMaxWidth().height(height).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
            curve.forEachIndexed { i, n ->
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                        if (n > 0) Box(Modifier.fillMaxWidth().fillMaxHeight(n.toFloat() / max).clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)).background(C.Accent))
                    }
                    Text(if (i == 7) "7+" else "$i", color = C.Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun MainList(deck: Deck, vm: MainViewModel, edit: ((Deck) -> Deck) -> Unit) {
    EnergyCurve(deck)
    val entries = DeckRules.entries(deck)
    val groups = listOf(
        "Champion" to entries.filter { it.first.slug == deck.champion },
        "Units" to entries.filter { it.first.slug != deck.champion && it.first.type == "unit" },
        "Spells" to entries.filter { it.first.type == "spell" },
        "Gear" to entries.filter { it.first.type == "gear" },
        "Other" to entries.filter { it.first.type !in setOf("unit", "spell", "gear") },
    )
    if (entries.size <= 1) EmptyState("Add cards", "Tap + Add cards.", Modifier.fillMaxWidth())
    groups.forEach { (name, list) ->
        if (list.isEmpty()) return@forEach
        Row(Modifier.padding(top = 4.dp)) {
            Text(name.uppercase(), style = Caps, color = C.Muted, modifier = Modifier.weight(1f))
            Text("${list.sumOf { it.second }}", style = Caps, color = C.Muted)
        }
        list.forEach { (c, n) -> DeckCardRow(deck, c, n, onOpen = { vm.push(Screen.CardDetail(c.slug)) }, onAdd = { edit { DeckRules.add(it, c) } }, onRemove = { edit { DeckRules.remove(it, c) } }) }
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
            CardImage(c, Modifier.fillMaxWidth().aspectRatio(744f / 1039f).offset(y = (-4).dp).graphicsScale(2.1f), corner = 0.dp)
        }
        Column(Modifier.weight(1f)) {
            Text(c.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = C.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val tag = when { c.slug == deck.champion -> "Chosen"; DeckRules.isSignature(c) -> "Signature"; !DeckRules.allowed(deck, c) -> "Off-domain"; else -> null }
            if (tag != null) Text(
                tag, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp,
                color = when (tag) { "Chosen" -> C.OnAccent; "Off-domain" -> C.Red; else -> C.Ink },
                modifier = Modifier.padding(top = 2.dp).clip(RoundedCornerShape(6.dp)).background(when (tag) { "Chosen" -> C.Accent; "Off-domain" -> C.Red.copy(alpha = 0.18f); else -> C.Line }).padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
        Row(Modifier.clip(RoundedCornerShape(10.dp)).background(C.Track), verticalAlignment = Alignment.CenterVertically) {
            Text("−", color = C.Muted, fontWeight = FontWeight.Black, fontSize = 16.sp, modifier = Modifier.clickable(onClick = onRemove).semantics { contentDescription = "Remove ${c.name}" }.padding(horizontal = 10.dp, vertical = 4.dp))
            Text("$n", color = C.Ink, fontWeight = FontWeight.Black, fontSize = 15.sp, modifier = Modifier.semantics { contentDescription = "${c.name} $n" })
            Text("+", color = if (n < DeckRules.maxCopies(c)) C.Muted else C.Line, fontWeight = FontWeight.Black, fontSize = 16.sp,
                modifier = Modifier.clickable(enabled = n < DeckRules.maxCopies(c), onClick = onAdd).semantics { contentDescription = "Add ${c.name}" }.padding(horizontal = 10.dp, vertical = 4.dp))
        }
    }
}

private fun Modifier.graphicsScale(s: Float) = this.graphicsLayer { scaleX = s; scaleY = s; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.12f) }

/** 12 runes as one bar: + on a side takes one from the other; the divider can be dragged. */
@Composable
private fun RuneSplit(deck: Deck, edit: ((Deck) -> Deck) -> Unit) {
    val ds = DeckRules.domains(deck)
    if (ds.size < 2) return
    val (a, b) = ds
    val na = deck.runesOf(a); val nb = deck.runesOf(b)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.Card).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RuneSymbol(a, 28.dp)
            Text("$na", fontWeight = FontWeight.Black, fontSize = 28.sp, color = C.Ink, modifier = Modifier.padding(start = 8.dp).semantics { contentDescription = "${a.name.lowercase()} runes $na" })
            Spacer(Modifier.weight(1f))
            Text("$nb", fontWeight = FontWeight.Black, fontSize = 28.sp, color = C.Ink, modifier = Modifier.padding(end = 8.dp).semantics { contentDescription = "${b.name.lowercase()} runes $nb" })
            RuneSymbol(b, 28.dp)
        }
        BoxWithConstraints(Modifier.fillMaxWidth().height(30.dp)) {
            val w = constraints.maxWidth.toFloat()
            val density = LocalDensity.current
            fun setAt(x: Float) = edit { DeckRules.setRunes(it, (x / w * DeckRules.RUNES).roundToInt()) }
            Row(
                Modifier.fillMaxWidth().height(22.dp).align(Alignment.Center).clip(RoundedCornerShape(11.dp))
                    .pointerInput(w) { detectTapGestures { setAt(it.x) } }
                    .pointerInput(w) { detectHorizontalDragGestures { ch, _ -> ch.consume(); setAt(ch.position.x) } },
            ) {
                if (na > 0) Box(Modifier.weight(na.toFloat()).fillMaxHeight().background(Color(a.argb)))
                if (nb > 0) Box(Modifier.weight(nb.toFloat()).fillMaxHeight().background(Color(b.argb)))
            }
            val x = with(density) { (w * na / DeckRules.RUNES).toDp() }
            Box(Modifier.offset(x = x - 3.dp).size(6.dp, 30.dp).clip(RoundedCornerShape(3.dp)).background(Color.White))
        }
        Row {
            PlusButton("More ${a.name.lowercase()} runes") { edit { DeckRules.addRune(it, a) } }
            Spacer(Modifier.weight(1f))
            PlusButton("More ${b.name.lowercase()} runes") { edit { DeckRules.addRune(it, b) } }
        }
    }
}

@Composable
private fun PlusButton(description: String, onClick: () -> Unit) {
    Text("+", fontWeight = FontWeight.Black, fontSize = 20.sp, color = C.Ink, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = Modifier.width(56.dp).clip(RoundedCornerShape(10.dp)).background(C.Track).clickable(onClick = onClick).semantics { contentDescription = description }.padding(vertical = 6.dp))
}

@Composable
private fun FieldSlots(deck: Deck, onPick: (Int) -> Unit, edit: ((Deck) -> Deck) -> Unit) {
    deck.battlefields.forEachIndexed { i, name ->
        Box(Modifier.fillMaxWidth().height(70.dp).clip(RoundedCornerShape(12.dp)).clickable { onPick(i) }.semantics(mergeDescendants = true) { contentDescription = "Battlefield $name" }) {
            BattlefieldThumb(name, Modifier.matchParentSize(), corner = 0)
            Box(Modifier.matchParentSize().background(Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.6f), 1f to Color.Transparent)))
            Text(name, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, modifier = Modifier.align(Alignment.BottomStart).padding(12.dp))
        }
    }
    if (deck.battlefields.size < DeckRules.BATTLEFIELDS) {
        Text("+ Battlefield", fontWeight = FontWeight.ExtraBold, color = C.Muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.5.dp, C.Line, RoundedCornerShape(12.dp)).clickable { onPick(deck.battlefields.size) }.padding(22.dp))
    }
}

@Composable
private fun DeckStats(deck: Deck, edit: ((Deck) -> Deck) -> Unit) {
    // Stacks: card images piled by energy cost.
    val byCost = DeckRules.entries(deck).filter { it.first.energy != null }.groupBy { it.first.energy!!.coerceAtMost(6) }.toSortedMap()
    if (byCost.isNotEmpty()) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (0..6).filter { it in byCost }.take(6).forEach { k ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                EnergySymbol(k, 18.dp)
                Spacer(Modifier.height(4.dp))
                // Each card shows only its top strip (cost and name) under the next one.
                Box(Modifier.fillMaxWidth()) {
                    byCost[k]!!.take(8).forEachIndexed { i, (c, _) ->
                        CardImage(c, Modifier.padding(top = (22 * i).dp).fillMaxWidth().shadow(2.dp, RoundedCornerShape(4.dp)), corner = 4.dp)
                    }
                }
            }
        }
    }
    EnergyCurve(deck, 52.dp, showAverage = true)
    val ds = DeckRules.domains(deck)
    if (ds.size == 2) {
        val p = DeckRules.powerByDomain(deck)
        val pa = (p[ds[0]] ?: 0.0).roundToInt(); val pb = (p[ds[1]] ?: 0.0).roundToInt()
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.Card).padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row {
                Text("POWER", style = Caps, color = C.Muted, modifier = Modifier.weight(1f))
                Text("${pa + pb}", fontWeight = FontWeight.Black, fontSize = 14.sp, color = C.Ink)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                RuneSymbol(ds[0], 20.dp)
                Text("$pa", fontWeight = FontWeight.Black, color = C.Ink)
                Row(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(C.Track), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (pa > 0) Box(Modifier.weight(pa.toFloat()).fillMaxHeight().background(Color(ds[0].argb)))
                    if (pb > 0) Box(Modifier.weight(pb.toFloat()).fillMaxHeight().background(Color(ds[1].argb)))
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
