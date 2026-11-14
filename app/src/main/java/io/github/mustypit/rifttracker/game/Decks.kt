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
) {
    fun count(slug: String) = main[slug] ?: 0
    val mainSize: Int get() = main.values.sum()
    val runeCount: Int get() = runes.values.sum()
    fun runesOf(d: Domain) = runes[d.name] ?: 0
}

/** Riftbound deckbuilding rules, as pure functions over the card library ([Cards]). */
object DeckRules {
    const val MAIN_SIZE = 40
    const val RUNES = 12
    const val BATTLEFIELDS = 3
    const val COPIES = 3
    const val SIGNATURES = 3

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

    /** Adds one copy (up to the card's copy limit). Going past 40 is allowed; it's labelled. */
    fun add(deck: Deck, c: Card): Deck {
        val n = deck.count(c.slug)
        if (n >= maxCopies(c)) return deck
        return deck.copy(main = deck.main + (c.slug to n + 1))
    }

    fun remove(deck: Deck, c: Card): Deck {
        val n = deck.count(c.slug)
        if (n <= 0) return deck
        return deck.copy(main = if (n == 1) deck.main - c.slug else deck.main + (c.slug to n - 1))
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
        if (cards.any { (c, _) -> !allowed(deck, c) }) out += "Off-domain"
        if (cards.any { (c, n) -> n > maxCopies(c) }) out += "Too many copies"
        if (cards.filter { (c, _) -> isSignature(c) }.sumOf { it.second } > SIGNATURES) out += "Signatures"
        if (deck.runeCount != RUNES || deck.runes.filterValues { it > 0 }.keys.any { k -> legend.domainList.none { it.name == k } }) out += "Runes ${deck.runeCount}/$RUNES"
        val fields = deck.battlefields.distinctBy { it.lowercase() }
        if (fields.size != BATTLEFIELDS || fields.size != deck.battlefields.size) out += "Fields ${fields.size}/$BATTLEFIELDS"
        return out
    }

    fun isLegal(deck: Deck) = problems(deck).isEmpty()

    /** The battlefield card with this name (decks store battlefields by name). */
    fun battlefieldCard(name: String): Card? = Cards.all.firstOrNull { it.type == "battlefield" && it.name.equals(name, ignoreCase = true) }

    /** Banned cards in [deck]: its legend, main deck cards and battlefields. */
    fun bannedCards(deck: Deck): List<Card> =
        (listOfNotNull(legendOf(deck)) + deck.main.keys.mapNotNull(Cards::bySlug) + deck.battlefields.mapNotNull(::battlefieldCard))
            .filter { it.banned }.distinctBy { it.slug }

    /** Main-deck cards with their counts, in deck order (champion, then units, spells, gear; by energy). */
    fun entries(deck: Deck): List<Pair<Card, Int>> = deck.main.mapNotNull { (slug, n) -> Cards.bySlug(slug)?.let { it to n } }
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
            }
            val m = line.find(l)
            val (n, cardName) = if (m != null) m.groupValues[1].toInt() to m.groupValues[2] else 1 to l
            when (section) {
                "runes" -> {
                    val d = Domain.entries.firstOrNull { cardName.lowercase().startsWith(it.name.lowercase()) }
                    if (d != null) runes[d.name] = (runes[d.name] ?: 0) + n else unknown += cardName
                }
                "fields" -> fields += cardName.trim()
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
        return Deck(name = name, legend = leg.slug, champion = champ?.slug, main = main, runes = runes, battlefields = fields) to unknown
    }

    /** A new deck: legend and champion (one copy in the main deck), runes split 6 · 6. */
    fun newDeck(legend: Card, champion: Card, name: String = "${championTag(legend) ?: legend.name} deck"): Deck =
        normaliseRunes(Deck(name = name, legend = legend.slug, champion = champion.slug, main = mapOf(champion.slug to 1)))
}
