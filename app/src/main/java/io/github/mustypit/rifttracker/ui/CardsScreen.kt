package io.github.mustypit.rifttracker.ui

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.NumberFormat

/** Whole-card images from assets/cards, decoded off the main thread and kept in a small cache. */
internal object CardArt {
    private val cache = LruCache<String, ImageBitmap>(48)
    fun cached(file: String): ImageBitmap? = cache.get(file)
    suspend fun load(context: android.content.Context, file: String): ImageBitmap? = cache.get(file) ?: withContext(Dispatchers.IO) {
        runCatching { context.assets.open("cards/$file").use { BitmapFactory.decodeStream(it) }?.asImageBitmap() }.getOrNull()
    }?.also { cache.put(file, it) }
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
    val query by vm.cardQuery.collectAsState()
    val domains by vm.cardDomains.collectAsState()
    val type by vm.cardType.collectAsState()
    val groups = remember(query, domains, type, Cards.all) { Cards.browse(query, domains, type) }
    val total = Cards.all.size

    LazyVerticalGrid(
        columns = GridCells.Fixed(3), state = vm.cardsGrid, modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "header") {
            Column(Modifier.fullBleed(16.dp)) {
                HeaderBlock {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(NumberFormat.getIntegerInstance(java.util.Locale.ENGLISH).format(total), color = Color.White, fontWeight = FontWeight.Black, fontSize = 56.sp)
                        Text(" cards", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.padding(bottom = 12.dp))
                    }
                    Cards.sets.firstOrNull()?.let {
                        Text("${Cards.sets.size} sets · $it newest", color = Color.White.copy(alpha = 0.8f), fontWeight = FontWeight.Bold)
                    }
                }
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    RiftTextField("Search cards", query, { vm.cardQuery.value = it }, Modifier.padding(top = 12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Domain.entries.forEach { d ->
                            val on = d in domains
                            Box(
                                Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(12.dp))
                                    .background(if (on) Color(d.argb) else C.Card)
                                    .border(1.5.dp, if (on) Color(d.argb) else C.Line, RoundedCornerShape(12.dp))
                                    .clickable { vm.cardDomains.value = if (on) domains - d else domains + d }
                                    .semantics { contentDescription = "${d.name.lowercase().replaceFirstChar { it.uppercase() }} filter" + if (on) " on" else "" },
                                contentAlignment = Alignment.Center,
                            ) { Icon(RiftSymbols.rune(d), null, tint = if (on) Color.White else Color(d.argb), modifier = Modifier.size(20.dp)) }
                        }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CardType.entries.forEach { t -> Chip(t.label, t == type) { vm.cardType.value = t } }
                    }
                }
            }
        }
        if (groups.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
            EmptyState("No cards", "Try fewer filters.", Modifier.fillMaxWidth())
        }
        groups.forEach { (set, cards) ->
            item(span = { GridItemSpan(maxLineSpan) }, key = "set:$set") {
                Row(Modifier.padding(top = 12.dp)) {
                    Text(set.uppercase(), style = Caps, color = C.Muted, modifier = Modifier.weight(1f))
                    Text("${cards.size}", style = Caps, color = C.Muted)
                }
            }
            items(cards, key = { it.slug }, span = { GridItemSpan(if (it.landscape) maxLineSpan else 1) }) { c ->
                CardImage(
                    c,
                    Modifier.shadow(2.dp, RoundedCornerShape(7.dp))
                        .clickable { vm.push(Screen.CardDetail(c.slug)) }
                        .semantics { contentDescription = "Card ${c.name}" },
                )
            }
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
            if (card.energy != null || card.power != null || card.might != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stat("Energy", Modifier.weight(1f)) { card.energy?.let { EnergySymbol(it, 26.dp) } ?: Dash() }
                    Stat("Runes", Modifier.weight(1f)) {
                        val n = card.power ?: 0
                        if (n == 0) Dash() else Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            val ds = card.domainList.ifEmpty { listOf<Domain?>(null) }
                            repeat(n) { i -> RuneSymbol(ds[i % ds.size], 22.dp) }
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
