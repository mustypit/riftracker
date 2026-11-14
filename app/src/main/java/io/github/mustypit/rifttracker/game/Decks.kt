package io.github.mustypit.rifttracker.game

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/**
 * A deck: a legend, the chosen champion, the 40-card main deck (champion included), 12 runes and 3
 * battlefields. Cards are stored by slug, battlefields by name (the battlefield library's names).
 * Decks may be unfinished or break rules; [DeckRules.problems] says what's wrong.
 */
@Serializable
data class Deck(
    val id: Long = 0,
    val name: String,
    /** Slug of the legend card. */
    val legend: String,
    /** Slug of the chosen champion (a champion unit of the legend's champion). */
    val champion: String? = null,
    /** Main deck: slug -> copies. Includes the chosen champion. */
    val main: Map<String, Int> = emptyMap(),
    /** Runes per domain (Domain.name) -> count. */
    val runes: Map<String, Int> = emptyMap(),
    val battlefields: List<String> = emptyList(),
    val updatedAt: Long = 0,
    /** Sideboard: slug -> copies (up to [DeckRules.SIDEBOARD] cards; copies count with the main deck). */
    val side: Map<String, Int> = emptyMap(),
    /** Maybeboard: cards being considered. No limits, never makes a deck illegal, not exported in codes. */
    val maybe: Map<String, Int> = emptyMap(),
) {
    fun count(slug: String) = main[slug] ?: 0
    fun count(board: Board, slug: String) = board(board)[slug] ?: 0
    fun board(board: Board): Map<String, Int> = when (board) { Board.MAIN -> main; Board.SIDE -> side; Board.MAYBE -> maybe }
    fun withBoard(board: Board, cards: Map<String, Int>): Deck = when (board) {
        Board.MAIN -> copy(main = cards); Board.SIDE -> copy(side = cards); Board.MAYBE -> copy(maybe = cards)
    }
    /** Copies across main deck and sideboard (what the copy limit counts). */
    fun copies(slug: String) = count(slug) + (side[slug] ?: 0)
    val sideSize: Int get() = side.values.sum()
    val maybeSize: Int get() = maybe.values.sum()
    val mainSize: Int get() = main.values.sum()
    val runeCount: Int get() = runes.values.sum()
    fun runesOf(d: Domain) = runes[d.name] ?: 0
}

/** Sideboarding between games: copies brought in from the sideboard and taken out of the main deck. */
@Serializable
data class Swaps(val brought: Map<String, Int> = emptyMap(), val taken: Map<String, Int> = emptyMap()) {
    val inCount: Int get() = brought.values.sum()
    val outCount: Int get() = taken.values.sum()
    fun isEmpty() = inCount == 0 && outCount == 0
    /** In and out match, so the main deck keeps its size. */
    val balanced: Boolean get() = inCount == outCount

    /** One more copy of [slug] in (up to the sideboard's copies) or out (up to the main deck's). */
    fun bringIn(deck: Deck, slug: String) = if ((brought[slug] ?: 0) < (deck.side[slug] ?: 0)) copy(brought = brought + (slug to (brought[slug] ?: 0) + 1)) else this
    fun takeOut(deck: Deck, slug: String) = if ((taken[slug] ?: 0) < deck.count(slug)) copy(taken = taken + (slug to (taken[slug] ?: 0) + 1)) else this
    fun undoIn(slug: String) = copy(brought = dec(brought, slug))
    fun undoOut(slug: String) = copy(taken = dec(taken, slug))
    private fun dec(m: Map<String, Int>, slug: String) = (m[slug] ?: 0).let { n -> if (n <= 1) m - slug else m + (slug to n - 1) }

    /** The deck as played: swapped cards moved between main deck and sideboard. */
    fun applyTo(deck: Deck): Deck {
        fun plus(a: Map<String, Int>, b: Map<String, Int>, sign: Int) = (a.keys + b.keys).associateWith { (a[it] ?: 0) + sign * (b[it] ?: 0) }.filterValues { it > 0 }
        return deck.copy(main = plus(plus(deck.main, taken, -1), brought, 1), side = plus(plus(deck.side, brought, -1), taken, 1))
    }
}

/** Where a card sits in a deck. */
enum class Board(val label: String) { MAIN("Main"), SIDE("Side"), MAYBE("Maybe") }

/** Riftbound deckbuilding rules, as pure functions over the card library ([Cards]). */
object DeckRules {
    const val MAIN_SIZE = 40
    const val RUNES = 12
    const val BATTLEFIELDS = 3
    const val COPIES = 3
    const val SIGNATURES = 3
    /** Sideboard size (tournament rules, July 2026; it was 8 before). */
    const val SIDEBOARD = 10

    fun legendOf(deck: Deck): Card? = Cards.bySlug(deck.legend)
    fun domains(deck: Deck): List<Domain> = legendOf(deck)?.domainList.orEmpty()

    /** The legend's champion name (a legend card's only tag, e.g. "Kai'Sa"). */
    fun championTag(legend: Card): String? = legend.tags.firstOrNull()

    /** Champion units that can be the chosen champion for [legend]. */
    fun champions(legend: Card): List<Card> {
        val tag = championTag(legend) ?: return emptyList()
        return Cards.all.filter { it.type == "unit" && it.isChampion && tag in it.tags }.sortedWith(compareBy({ it.energy ?: 0 }, { it.name }))
    }

    val legends: List<Card> get() = Cards.all.filter { it.type == "legend" }.sortedBy { it.tags.firstOrNull() ?: it.name }

    fun isSignature(c: Card) = "signature" in c.superTypes
    fun maxCopies(c: Card) = c.maxCopies ?: COPIES

    /** Cards that go in a main deck at all (no legends, battlefields, runes or tokens). */
    fun isMainCard(c: Card) = c.type in setOf("unit", "spell", "gear") && "token" !in c.superTypes

    /** Whether [c] may be in [deck]'s main deck: right domains, and a signature card of the deck's champion. */
    fun allowed(deck: Deck, c: Card): Boolean {
        if (!isMainCard(c)) return false
        val legend = legendOf(deck) ?: return false
        val ds = legend.domainList
        if (!c.domainList.all { it in ds }) return false
        if (isSignature(c) && championTag(legend) !in c.tags) return false
        return true
    }

    /** Adds one copy to the main deck (up to the card's copy limit, sideboard copies included). Going past 40 is allowed; it's labelled. */
    fun add(deck: Deck, c: Card): Deck = add(deck, Board.MAIN, c)

    fun remove(deck: Deck, c: Card): Deck = remove(deck, Board.MAIN, c)

    /** Whether one more copy of [c] may go on [board]: main and side share the copy limit; maybe has none. */
    fun canAdd(deck: Deck, board: Board, c: Card): Boolean = board == Board.MAYBE || deck.copies(c.slug) < maxCopies(c)

    /** One more copy on [board]. Going past 40 or 10 is allowed; it's labelled. */
    fun add(deck: Deck, board: Board, c: Card): Deck {
        if (!canAdd(deck, board, c)) return deck
        return deck.withBoard(board, deck.board(board) + (c.slug to deck.count(board, c.slug) + 1))
    }

    fun remove(deck: Deck, board: Board, c: Card): Deck {
        val n = deck.count(board, c.slug)
        if (n <= 0) return deck
        val cards = deck.board(board)
        return deck.withBoard(board, if (n == 1) cards - c.slug else cards + (c.slug to n - 1))
    }

    /**
     * Moves one copy (or all of them) of [c] from one board to another. Moving between main and side
     * never breaks the copy limit (the total stays the same); moving out of the maybeboard is limited
     * by it. The chosen champion stays the chosen champion even when its copies move.
     */
    fun move(deck: Deck, c: Card, from: Board, to: Board, all: Boolean = false): Deck {
        if (from == to) return deck
        var d = deck
        repeat(if (all) deck.count(from, c.slug) else 1) {
            if (d.count(from, c.slug) == 0) return d
            val removed = remove(d, from, c)
            if (to != Board.MAYBE && from == Board.MAYBE && !canAdd(removed, to, c)) return d
            d = removed.withBoard(to, removed.board(to) + (c.slug to removed.count(to, c.slug) + 1))
        }
        return d
    }

    /** One more rune of [d], taken from the other domain: the rune deck always stays at 12. */
    fun addRune(deck: Deck, d: Domain): Deck {
        val ds = domains(deck)
        val other = ds.firstOrNull { it != d } ?: return deck
        val fix = normaliseRunes(deck)
        if (fix.runesOf(other) == 0) return fix
        return fix.copy(runes = mapOf(d.name to fix.runesOf(d) + 1, other.name to fix.runesOf(other) - 1))
    }

    /** Sets the split directly (the bar's divider): [first] runes of the legend's first domain. */
    fun setRunes(deck: Deck, first: Int): Deck {
        val ds = domains(deck)
        if (ds.size < 2) return deck
        val a = first.coerceIn(0, RUNES)
        return deck.copy(runes = mapOf(ds[0].name to a, ds[1].name to RUNES - a))
    }

    /** Runes that add up to 12 in the legend's domains (6 · 6 when unset). */
    fun normaliseRunes(deck: Deck): Deck {
        val ds = domains(deck)
        if (ds.size < 2) return deck
        val a = deck.runesOf(ds[0]); val b = deck.runesOf(ds[1])
        if (a + b == RUNES && deck.runes.keys.all { k -> ds.any { it.name == k } }) return deck
        return setRunes(deck, if (a + b == 0) RUNES / 2 else (a * RUNES.toFloat() / (a + b)).roundToInt())
    }

    /** What's wrong with [deck], most important first, as short labels ("Banned", "37/40", "Runes 10/12"…). Empty = legal. */
    fun problems(deck: Deck): List<String> {
        val legend = legendOf(deck) ?: return listOf("No legend")
        val out = mutableListOf<String>()
        if (bannedCards(deck).isNotEmpty()) out += "Banned"
        val cards = deck.main.mapNotNull { (slug, n) -> Cards.bySlug(slug)?.let { it to n } }
        if (deck.champion == null || deck.count(deck.champion) == 0) out += "No champion"
        if (deck.mainSize != MAIN_SIZE) out += "${deck.mainSize}/$MAIN_SIZE"
        val sideCards = deck.side.mapNotNull { (slug, n) -> Cards.bySlug(slug)?.let { it to n } }
        if ((cards + sideCards).any { (c, _) -> !allowed(deck, c) }) out += "Off-domain"
        if ((cards + sideCards).map { it.first }.distinct().any { c -> deck.copies(c.slug) > maxCopies(c) }) out += "Too many copies"
        if (cards.filter { (c, _) -> isSignature(c) }.sumOf { it.second } > SIGNATURES) out += "Signatures"
        if (deck.runeCount != RUNES || deck.runes.filterValues { it > 0 }.keys.any { k -> legend.domainList.none { it.name == k } }) out += "Runes ${deck.runeCount}/$RUNES"
        val fields = deck.battlefields.distinctBy { it.lowercase() }
        if (fields.size != BATTLEFIELDS || fields.size != deck.battlefields.size) out += "Fields ${fields.size}/$BATTLEFIELDS"
        if (deck.sideSize > SIDEBOARD) out += "Side ${deck.sideSize}/$SIDEBOARD"
        return out
    }

    fun isLegal(deck: Deck) = problems(deck).isEmpty()

    /** The battlefield card with this name (decks store battlefields by name). */
    fun battlefieldCard(name: String): Card? = Cards.all.firstOrNull { it.type == "battlefield" && it.name.equals(name, ignoreCase = true) }

    /** Banned cards in [deck]: its legend, main deck and sideboard cards, and battlefields (not the maybeboard). */
    fun bannedCards(deck: Deck): List<Card> =
        (listOfNotNull(legendOf(deck)) + deck.main.keys.mapNotNull(Cards::bySlug) + deck.side.keys.mapNotNull(Cards::bySlug) + deck.battlefields.mapNotNull(::battlefieldCard))
            .filter { it.banned }.distinctBy { it.slug }

    /** Main-deck cards with their counts, in deck order (champion, then units, spells, gear; by energy). */
    fun entries(deck: Deck): List<Pair<Card, Int>> = entries(deck, Board.MAIN)

    fun entries(deck: Deck, board: Board): List<Pair<Card, Int>> = deck.board(board).mapNotNull { (slug, n) -> Cards.bySlug(slug)?.let { it to n } }
        .sortedWith(compareBy({ it.first.energy ?: 99 }, { it.first.name }))

    /** Copies per energy cost, 0..7 (7 = seven or more). */
    fun energyCurve(deck: Deck): IntArray {
        val out = IntArray(8)
        entries(deck).forEach { (c, n) -> c.energy?.let { out[it.coerceIn(0, 7)] += n } }
        return out
    }

    fun averageEnergy(deck: Deck): Double {
        val withCost = entries(deck).filter { it.first.energy != null }
        val n = withCost.sumOf { it.second }
        return if (n == 0) 0.0 else withCost.sumOf { (c, k) -> c.energy!! * k }.toDouble() / n
    }

    /**
     * Power symbols per domain across the main deck. A two-domain card's power is split between its
     * domains (the data doesn't say which symbol is which).
     */
    fun powerByDomain(deck: Deck): Map<Domain, Double> {
        val out = mutableMapOf<Domain, Double>()
        entries(deck).forEach { (c, n) ->
            val p = (c.power ?: 0) * n
            val ds = c.domainList
            if (p > 0 && ds.isNotEmpty()) ds.forEach { d -> out[d] = (out[d] ?: 0.0) + p.toDouble() / ds.size }
        }
        return out
    }

    /** Runes in the same proportion as the deck's power symbols (6 · 6 without any). */
    fun matchRunes(deck: Deck): Deck {
        val ds = domains(deck)
        if (ds.size < 2) return deck
        val p = powerByDomain(deck)
        val a = p[ds[0]] ?: 0.0; val b = p[ds[1]] ?: 0.0
        return setRunes(deck, if (a + b == 0.0) RUNES / 2 else (a * RUNES / (a + b)).roundToInt())
    }

    /** A copyable list: legend, champion, main deck, runes and battlefields. */
    fun toText(deck: Deck): String = buildString {
        legendOf(deck)?.let { appendLine("Legend: ${it.name}") }
        deck.champion?.let { Cards.bySlug(it) }?.let { appendLine("Champion: ${it.name}") }
        appendLine()
        appendLine("Main deck:")
        entries(deck).forEach { (c, n) -> appendLine("$n ${c.name}") }
        appendLine()
        appendLine("Runes:")
        domains(deck).forEach { d -> if (deck.runesOf(d) > 0) appendLine("${deck.runesOf(d)} ${d.name.lowercase().replaceFirstChar { it.uppercase() }} Rune") }
        if (deck.battlefields.isNotEmpty()) {
            appendLine()
            appendLine("Battlefields:")
            deck.battlefields.forEach { appendLine("1 $it") }
        }
        if (deck.side.isNotEmpty()) {
            appendLine()
            appendLine("Sideboard:")
            entries(deck, Board.SIDE).forEach { (c, n) -> appendLine("$n ${c.name}") }
        }
    }.trimEnd()

    /**
     * Reads a pasted list (the [toText] format, or plain "3 Card Name" lines with section headers).
     * Unknown names are skipped and returned so the screen can say how many didn't match.
     */
    fun fromText(text: String, name: String): Pair<Deck?, List<String>> {
        val unknown = mutableListOf<String>()
        var legend: Card? = null
        var champion: Card? = null
        val main = linkedMapOf<String, Int>()
        val runes = mutableMapOf<String, Int>()
        val fields = mutableListOf<String>()
        val side = linkedMapOf<String, Int>()
        val maybe = linkedMapOf<String, Int>()
        var section = "main"
        val line = Regex("""^(\d+)\s*x?\s+(.+)$""", RegexOption.IGNORE_CASE)
        fun card(n: String) = Cards.all.firstOrNull { it.name.equals(n.trim(), ignoreCase = true) }
        for (raw in text.lines()) {
            val l = raw.trim()
            if (l.isEmpty()) continue
            val lower = l.lowercase()
            when {
                lower.startsWith("legend:") -> { legend = card(l.substringAfter(":")); if (legend == null) unknown += l.substringAfter(":").trim(); continue }
                lower.startsWith("champion:") -> { champion = card(l.substringAfter(":")); if (champion == null) unknown += l.substringAfter(":").trim(); continue }
                lower.trimEnd(':').let { it == "main deck" || it == "main" || it == "deck" } -> { section = "main"; continue }
                lower.trimEnd(':').let { it == "runes" || it == "rune deck" } -> { section = "runes"; continue }
                lower.trimEnd(':').let { it == "battlefields" || it == "battlefield" } -> { section = "fields"; continue }
                lower.trimEnd(':').let { it == "sideboard" || it == "side" || it == "side deck" } -> { section = "side"; continue }
                lower.trimEnd(':').let { it == "maybeboard" || it == "maybe" || it == "considering" } -> { section = "maybe"; continue }
            }
            val m = line.find(l)
            val (n, cardName) = if (m != null) m.groupValues[1].toInt() to m.groupValues[2] else 1 to l
            when (section) {
                "runes" -> {
                    val d = Domain.entries.firstOrNull { cardName.lowercase().startsWith(it.name.lowercase()) }
                    if (d != null) runes[d.name] = (runes[d.name] ?: 0) + n else unknown += cardName
                }
                "fields" -> fields += cardName.trim()
                "side", "maybe" -> {
                    val c = card(cardName)
                    val into = if (section == "side") side else maybe
                    if (c == null) unknown += cardName.trim() else into[c.slug] = (into[c.slug] ?: 0) + n
                }
                else -> {
                    val c = card(cardName)
                    when {
                        c == null -> unknown += cardName.trim()
                        c.type == "legend" -> legend = c
                        c.type == "battlefield" -> fields += c.name
                        else -> main[c.slug] = (main[c.slug] ?: 0) + n
                    }
                }
            }
        }
        val leg = legend ?: return null to unknown
        val champ = champion ?: champions(leg).firstOrNull { main.containsKey(it.slug) }
        if (champ != null && !main.containsKey(champ.slug)) main[champ.slug] = 1
        return Deck(name = name, legend = leg.slug, champion = champ?.slug, main = main, runes = runes, battlefields = fields, side = side, maybe = maybe) to unknown
    }

    /** A new deck: legend and champion (one copy in the main deck), runes split 6 · 6. */
    fun newDeck(legend: Card, champion: Card, name: String = "${championTag(legend) ?: legend.name} deck"): Deck =
        normaliseRunes(Deck(name = name, legend = legend.slug, champion = champion.slug, main = mapOf(champion.slug to 1)))
}
