package io.github.mustypit.rifttracker.ui

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import io.github.mustypit.rifttracker.game.StatFilter
import io.github.mustypit.rifttracker.game.Stat
import io.github.mustypit.rifttracker.game.Compare
import io.github.mustypit.rifttracker.game.CardSort
import io.github.mustypit.rifttracker.game.CardQuery
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.mustypit.rifttracker.game.Card
import io.github.mustypit.rifttracker.game.CardType
import io.github.mustypit.rifttracker.game.Cards
import io.github.mustypit.rifttracker.game.Domain
import io.github.mustypit.rifttracker.game.RulesText
import java.text.NumberFormat

/**
 * Whole-card images from assets/cards, decoded on a background thread and kept in a small cache. The
 * result is handed back on the main thread, so the screen only ever updates there.
 */
internal object CardArt {
    private val cache = LruCache<String, ImageBitmap>(48)
    private val decoder = java.util.concurrent.Executors.newFixedThreadPool(2)
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    fun cached(file: String): ImageBitmap? = cache.get(file)
    suspend fun load(context: android.content.Context, file: String): ImageBitmap? = cache.get(file)
        ?: kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            decoder.execute {
                val bmp = runCatching { context.assets.open("cards/$file").use { BitmapFactory.decodeStream(it) }?.asImageBitmap() }.getOrNull()
                main.post {
                    bmp?.let { cache.put(file, it) }
                    if (cont.isActive) cont.resumeWith(Result.success(bmp))
                }
            }
        }
}

/** A card's picture (the whole printed card), with a quiet placeholder while it loads. */
@Composable
fun CardImage(card: Card, modifier: Modifier = Modifier, corner: Dp = 7.dp) {
    val context = LocalContext.current
    val file = card.image
    val bitmap by produceState(file?.let(CardArt::cached), file) { if (file != null && value == null) value = CardArt.load(context, file) }
    val ratio = if (card.landscape) 1039f / 744f else 744f / 1039f
    Box(modifier.aspectRatio(ratio).clip(RoundedCornerShape(corner)).background(C.Track)) {
        bitmap?.let { Image(it, card.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
    }
}

/** The energy symbol: a white disc with the number in dark, as printed on the cards. */
@Composable
fun EnergySymbol(n: Int, size: Dp, modifier: Modifier = Modifier) {
    // White disc with a dark rim, as printed, so it reads on light surfaces too.
    Box(modifier.size(size).clip(CircleShape).background(Color(0xFF111111)).padding((size.value * 0.07f).dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
        Text("$n", color = Color(0xFF111111), fontWeight = FontWeight.Black, fontSize = (size.value * if (n > 9) 0.46f else 0.58f).sp, lineHeight = (size.value * 0.6f).sp)
    }
}

@Composable
fun RuneSymbol(domain: Domain?, size: Dp, modifier: Modifier = Modifier) {
    if (domain == null) Icon(RiftSymbols.Rainbow, "any rune", tint = Color.Unspecified, modifier = modifier.size(size))
    else Icon(RiftSymbols.rune(domain), "${domain.name.lowercase()} rune", tint = Color(domain.argb), modifier = modifier.size(size))
}

/** Rules text with the card's symbols drawn inline, keywords in bold and reminder text muted. */
@Composable
fun RulesTextView(text: String, modifier: Modifier = Modifier, fontSize: TextUnit = 15.sp) {
    val parts = remember(text) { RulesText.parse(text) }
    val symbols = LinkedHashMap<String, InlineTextContent>()
    val annotated: AnnotatedString = buildAnnotatedString {
        parts.forEachIndexed { i, p ->
            when (p) {
                is RulesText.Words -> withStyle(
                    when {
                        p.keyword -> SpanStyle(fontWeight = FontWeight.ExtraBold)
                        p.reminder -> SpanStyle(fontStyle = FontStyle.Italic, color = C.Muted)
                        else -> SpanStyle()
                    },
                ) { append(p.text) }
                else -> {
                    val id = "s$i"
                    appendInlineContent(id, "·")
                    symbols[id] = InlineTextContent(Placeholder(1.25.em, 1.15.em, PlaceholderVerticalAlign.TextCenter)) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            when (p) {
                                is RulesText.Energy -> EnergySymbol(p.amount, (fontSize.value * 1.05f).dp)
                                is RulesText.Rune -> RuneSymbol(p.domain, (fontSize.value * 1.1f).dp)
                                RulesText.Might -> Icon(RiftSymbols.Might, "might", tint = C.Ink, modifier = Modifier.size((fontSize.value * 1.1f).dp))
                                RulesText.Exhaust -> Icon(RiftSymbols.Exhaust, "exhaust", tint = C.Ink, modifier = Modifier.size((fontSize.value * 1.1f).dp))
                                is RulesText.Words -> Unit
                            }
                        }
                    }
                }
            }
        }
    }
    Text(annotated, inlineContent = symbols, color = C.Ink, fontSize = fontSize, lineHeight = (fontSize.value * 1.5f).sp, modifier = modifier)
}

private val typeLabel = mapOf("unit" to "Unit", "spell" to "Spell", "gear" to "Gear", "legend" to "Legend", "battlefield" to "Battlefield", "rune" to "Rune")

@Composable
fun CardsScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val q by vm.cardSearch.collectAsState()
    val filtersOpen by vm.cardFiltersOpen.collectAsState()
    val results = remember(q, Cards.all) { Cards.search(q) }
    fun update(f: (CardQuery) -> CardQuery) { vm.cardSearch.value = f(vm.cardSearch.value) }
    androidx.activity.compose.BackHandler(enabled = filtersOpen) { vm.cardFiltersOpen.value = false }

    Box(modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3), state = vm.cardsGrid, modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "header") {
                Column(Modifier.fullBleed(16.dp)) {
                    HeaderBlock { RiftTextField("Search cards", q.text, { t -> update { it.copy(text = t) } }) }
                    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Domain.entries.forEach { d ->
                                val on = d in q.domains
                                Box(
                                    Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(12.dp))
                                        .background(if (on) Color(d.argb) else C.Card)
                                        .border(1.5.dp, if (on) Color(d.argb) else C.Line, RoundedCornerShape(12.dp))
                                        .clickable { update { it.copy(domains = if (on) it.domains - d else it.domains + d) } }
                                        .semantics { contentDescription = "${d.name.lowercase().replaceFirstChar { it.uppercase() }} filter" + if (on) " on" else "" },
                                    contentAlignment = Alignment.Center,
                                ) { Icon(RiftSymbols.rune(d), null, tint = if (on) Color.White else Color(d.argb), modifier = Modifier.size(20.dp)) }
                            }
                        }
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            CardType.entries.forEach { t -> Chip(t.label, t == q.type) { update { it.copy(type = t) } } }
                        }
                        // Active filters, each removable, then the way in to add more.
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            q.stats.forEachIndexed { i, f ->
                                FilterToken(f.text, statIcon = f.stat) { update { it.copy(stats = it.stats.filterIndexed { j, _ -> j != i }) } }
                            }
                            if (q.rulesText.isNotBlank()) FilterToken("Text: “${q.rulesText.trim()}”") { update { it.copy(rulesText = "") } }
                            q.tags.sorted().forEach { k -> FilterToken(k) { update { it.copy(tags = it.tags - k) } } }
                            q.keywords.sorted().forEach { k -> FilterToken(k) { update { it.copy(keywords = it.keywords - k) } } }
                            Text(
                                "+ Filters", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = C.Ink,
                                modifier = Modifier.clip(RoundedCornerShape(10.dp)).border(1.5.dp, C.Line, RoundedCornerShape(10.dp))
                                    .clickable { vm.cardFiltersOpen.value = true }.padding(horizontal = 12.dp, vertical = 7.dp),
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${results.size} cards".uppercase(), style = Caps, color = C.Muted, modifier = Modifier.weight(1f))
                            SortMenu(q.sort) { s -> update { it.copy(sort = s) } }
                        }
                    }
                }
            }
            if (results.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                EmptyState("No cards", "Try fewer filters.", Modifier.fillMaxWidth())
            }
            items(results, key = { it.slug }, span = { GridItemSpan(if (it.landscape) maxLineSpan else 1) }) { c ->
                CardImage(
                    c,
                    Modifier.shadow(2.dp, RoundedCornerShape(7.dp))
                        .clickable { vm.push(Screen.CardDetail(c.slug)) }
                        .semantics { contentDescription = "Card ${c.name}" },
                )
            }
        }
        if (filtersOpen) FilterSheet(q, results.size, ::update) { vm.cardFiltersOpen.value = false }
    }
}

@Composable
private fun StatIcon(stat: Stat, size: Dp) = when (stat) {
    Stat.ENERGY -> EnergySymbol(1, size)
    Stat.POWER -> Icon(RiftSymbols.Rainbow, null, tint = Color.Unspecified, modifier = Modifier.size(size))
    Stat.MIGHT -> Icon(RiftSymbols.Might, null, tint = C.Ink, modifier = Modifier.size(size))
}

/** A yellow, removable token for an active filter. */
@Composable
private fun FilterToken(text: String, statIcon: Stat? = null, onRemove: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(10.dp)).background(C.Yellow).clickable(onClick = onRemove)
            .semantics(mergeDescendants = true) { contentDescription = "Remove $text" }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (statIcon != null) {
            when (statIcon) {
                Stat.MIGHT -> Icon(RiftSymbols.Might, null, tint = C.Black, modifier = Modifier.size(16.dp))
                else -> StatIcon(statIcon, 16.dp)
            }
        }
        Text(text, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = C.Black)
        Text("×", fontWeight = FontWeight.Black, fontSize = 14.sp, color = C.Black.copy(alpha = 0.55f))
    }
}

@Composable
private fun SortMenu(sort: CardSort, onSort: (CardSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Text("Sort: ${sort.label} ▾".uppercase(), style = Caps, color = C.Muted, modifier = Modifier.clickable { open = true }.padding(4.dp))
        DropdownMenu(open, { open = false }) {
            CardSort.entries.forEach { s -> DropdownMenuItem(text = { Text(s.label) }, onClick = { open = false; onSort(s) }) }
        }
    }
}

/** A compact dropdown field (Scryfall-style): shows [label], opens [items] below it. */
@Composable
private fun <T> DropField(label: @Composable () -> Unit, description: String, items: List<T>, item: @Composable (T) -> Unit, onPick: (T) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(10.dp)).background(C.Card)
                .border(if (open) 2.dp else 1.5.dp, if (open) C.Ink else C.Line, RoundedCornerShape(10.dp))
                .clickable { open = true }.semantics { contentDescription = description }.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.weight(1f)) { label() }
            Text("▾", color = C.Muted, fontSize = 12.sp)
        }
        DropdownMenu(open, { open = false }) {
            items.forEach { t -> DropdownMenuItem(text = { item(t) }, onClick = { open = false; onPick(t) }) }
        }
    }
}

/** The filter sheet: stats rows (stat, comparison, value) and a multi-select keyword field. */
@Composable
private fun FilterSheet(q: CardQuery, count: Int, update: ((CardQuery) -> CardQuery) -> Unit, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null, onClick = onClose)) {
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max = 640.dp)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).background(C.Paper)
                .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {}
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 16.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(40.dp, 5.dp).clip(RoundedCornerShape(3.dp)).background(C.Line))
            Row(Modifier.padding(top = 10.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Filters", fontWeight = FontWeight.Black, fontSize = 20.sp, color = C.Ink, modifier = Modifier.weight(1f))
                Text("Clear all", fontWeight = FontWeight.Bold, color = C.Muted, modifier = Modifier.clickable { update { it.copy(stats = emptyList(), keywords = emptySet(), tags = emptySet(), rulesText = "") } }.padding(4.dp))
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Stats", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = C.Ink, modifier = Modifier.padding(top = 6.dp))
                q.stats.forEachIndexed { i, f ->
                    fun set(nf: StatFilter) = update { it.copy(stats = it.stats.toMutableList().also { l -> l[i] = nf }) }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        DropField(
                            { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) { StatIcon(f.stat, 18.dp); Text(f.stat.label, fontWeight = FontWeight.ExtraBold, color = C.Ink) } },
                            "Stat ${i + 1}", Stat.entries,
                            { s -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { StatIcon(s, 18.dp); Text(s.label) } },
                            { set(f.copy(stat = it)) }, Modifier.weight(1f),
                        )
                        DropField(
                            { Text(f.compare.symbol, fontWeight = FontWeight.Black, fontSize = 18.sp, color = C.Ink) },
                            "Comparison ${i + 1}", Compare.entries,
                            { c -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Text(c.symbol, fontWeight = FontWeight.Black, modifier = Modifier.width(16.dp)); Text(c.label) } },
                            { set(f.copy(compare = it)) }, Modifier.width(64.dp),
                        )
                        DropField(
                            { Text("${f.value}", fontWeight = FontWeight.Black, fontSize = 16.sp, color = C.Ink) },
                            "Value ${i + 1}", (0..12).toList(), { v -> Text("$v") }, { set(f.copy(value = it)) }, Modifier.width(58.dp),
                        )
                        Text("×", fontWeight = FontWeight.Black, fontSize = 18.sp, color = C.Muted,
                            modifier = Modifier.clickable { update { it.copy(stats = it.stats.filterIndexed { j, _ -> j != i }) } }.semantics { contentDescription = "Remove stat ${i + 1}" }.padding(4.dp))
                    }
                }
                Text("+ Add stat", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = C.Ink,
                    modifier = Modifier.clickable { update { it.copy(stats = it.stats + StatFilter(Stat.ENERGY, Compare.LE, 3)) } }.padding(vertical = 6.dp))
                Text("Text", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = C.Ink, modifier = Modifier.padding(top = 6.dp))
                RiftTextField("Rules text, e.g. draw 2", q.rulesText, { t -> update { it.copy(rulesText = t) } })
                Text("Tags", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = C.Ink, modifier = Modifier.padding(top = 6.dp))
                MultiPicker("tag", remember(Cards.all) { Cards.tags }, q.tags) { k -> update { it.copy(tags = if (k in it.tags) it.tags - k else it.tags + k) } }
                Text("Keywords", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = C.Ink, modifier = Modifier.padding(top = 6.dp))
                MultiPicker("keyword", remember(Cards.all) { Cards.keywords }, q.keywords) { k -> update { it.copy(keywords = if (k in it.keywords) it.keywords - k else it.keywords + k) } }
            }
            Box(Modifier.padding(top = 12.dp)) { BigButton("SHOW $count CARDS", C.Solid, C.OnSolid, onClick = onClose) }
        }
    }
}

/**
 * Multi-select from a list (keywords, tags): picked ones as tokens in the field, a list with checkboxes
 * and counts that typing narrows. [what] names it ("keyword") for the field and accessibility.
 */
@Composable
private fun MultiPicker(what: String, all: List<Pair<String, Int>>, selected: Set<String>, onToggle: (String) -> Unit) {
    var typed by remember { mutableStateOf("") }
    var open by remember { mutableStateOf(false) }
    val title = what.replaceFirstChar { it.uppercase() }
    FlowRow(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.Card)
            .border(if (open) 2.dp else 1.5.dp, if (open) C.Ink else C.Line, RoundedCornerShape(14.dp))
            .clickable { open = !open }.padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        selected.sorted().forEach { k ->
            Text("$k  ×", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = C.OnSolid,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(C.Solid).clickable { onToggle(k) }.padding(horizontal = 8.dp, vertical = 5.dp))
        }
        androidx.compose.foundation.text.BasicTextField(
            typed, { typed = it; open = true }, singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(color = C.Ink, fontSize = 15.sp),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(C.Ink),
            modifier = Modifier.widthIn(min = 120.dp).padding(vertical = 5.dp, horizontal = 4.dp)
                .onFocusChanged { if (it.isFocused) open = true }.semantics { contentDescription = "Add $what" },
            decorationBox = { inner -> Box { if (typed.isEmpty()) Text("Add $what", color = C.Muted, fontSize = 15.sp); inner() } },
        )
    }
    if (open) {
        val shown = all.filter { typed.isBlank() || it.first.contains(typed.trim(), ignoreCase = true) }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.Card).border(1.5.dp, C.Line, RoundedCornerShape(14.dp)).padding(vertical = 4.dp)) {
            shown.take(40).forEach { (k, n) ->
                val on = k in selected
                Row(
                    Modifier.fillMaxWidth().clickable { onToggle(k); typed = "" }.semantics(mergeDescendants = true) { contentDescription = "$title $k" + if (on) " on" else "" }
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        Modifier.size(18.dp).clip(RoundedCornerShape(5.dp)).background(if (on) C.Solid else Color.Transparent).border(1.5.dp, if (on) C.Solid else C.Line, RoundedCornerShape(5.dp)),
                        contentAlignment = Alignment.Center,
                    ) { if (on) Text("✓", color = C.OnSolid, fontSize = 12.sp, fontWeight = FontWeight.Black) }
                    Text(k, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.Normal, fontSize = 15.sp, color = C.Ink, modifier = Modifier.weight(1f))
                    Text("$n", color = C.Muted, fontSize = 12.sp)
                }
            }
            if (shown.size > 40) Text("${shown.size - 40} more: keep typing", color = C.Muted, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
            if (shown.isEmpty()) Text("No $what like “$typed”", color = C.Muted, modifier = Modifier.padding(12.dp))
        }
    }
}

/** Lets a grid item reach past the grid's side padding (the header runs edge to edge). */
private fun Modifier.fullBleed(side: Dp): Modifier = layout { m, c ->
    val extra = (side * 2).roundToPx()
    val p = m.measure(c.copy(minWidth = c.maxWidth + extra, maxWidth = c.maxWidth + extra))
    layout(c.maxWidth, p.height) { p.place(-side.roundToPx(), 0) }
}

@Composable
fun CardDetailScreen(vm: MainViewModel, slug: String) {
    val card = Cards.bySlug(slug) ?: return
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().background(C.Black).statusBarsPadding().padding(bottom = 20.dp)) {
            IconButton(onClick = { vm.back() }, modifier = Modifier.padding(start = 8.dp, top = 4.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
            }
            CardImage(
                card, Modifier.align(Alignment.Center).padding(top = 48.dp).widthIn(max = if (card.landscape) 340.dp else 250.dp)
                    .shadow(16.dp, RoundedCornerShape(12.dp)),
                corner = 12.dp,
            )
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column {
                Text(card.name, fontWeight = FontWeight.Black, fontSize = 26.sp, color = C.Ink)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    card.domainList.forEach { Box(Modifier.size(10.dp).clip(CircleShape).background(Color(it.argb))) }
                    val kind = (if (card.isChampion) "Champion " + typeLabel[card.type].orEmpty().lowercase() else typeLabel[card.type].orEmpty())
                    Text(listOf(kind, card.tags.take(2).joinToString(", ")).filter { it.isNotBlank() }.joinToString(" · "), color = C.Muted, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
            if (card.energy != null || card.might != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Cost as printed: energy, then one power symbol per point of power.
                    if (card.energy != null) Stat("Cost", Modifier.weight(1.6f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            EnergySymbol(card.energy, 28.dp)
                            val ds = card.domainList.ifEmpty { listOf<Domain?>(null) }
                            repeat(card.power ?: 0) { i -> RuneSymbol(ds[i % ds.size], 24.dp) }
                        }
                    }
                    Stat("Might", Modifier.weight(1f)) {
                        card.might?.let {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("$it", fontWeight = FontWeight.Black, fontSize = 20.sp, color = C.Ink)
                                Icon(RiftSymbols.Might, "might", tint = C.Ink, modifier = Modifier.padding(start = 3.dp).size(20.dp))
                            }
                        } ?: Dash()
                    }
                }
            }
            if (card.text.isNotBlank()) {
                RulesTextView(card.text, Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.Card).padding(14.dp))
            }
            if (card.keywords.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    card.keywords.forEach { k ->
                        Text(k, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = C.Ink, modifier = Modifier.clip(RoundedCornerShape(10.dp)).border(1.5.dp, C.Line, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 5.dp))
                    }
                }
            }
            Text(
                listOfNotNull(card.set, card.code, card.rarity?.replaceFirstChar { it.uppercase() }, card.artist).joinToString(" · "),
                color = C.Muted, fontSize = 13.sp,
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun Stat(label: String, modifier: Modifier, value: @Composable () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(C.Card).padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = C.Muted, fontSize = 12.sp)
        Box(Modifier.height(28.dp), contentAlignment = Alignment.CenterStart) { value() }
    }
}

@Composable
private fun Dash() = Text("–", fontWeight = FontWeight.Black, fontSize = 20.sp, color = C.Muted)
