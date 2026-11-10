package io.github.mustypit.rifttracker.game

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One card in the library: its original normal printing (from OpenRift, see build_legend_assets.py). */
@Serializable
data class Card(
    val slug: String,
    val name: String,
    val type: String,
    val superTypes: List<String> = emptyList(),
    val domains: List<String> = emptyList(),
    val energy: Int? = null,
    /** Rune cost: how many runes of the card's domains it needs. */
    val power: Int? = null,
    val might: Int? = null,
    val keywords: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val text: String = "",
    val set: String = "",
    val code: String? = null,
    val rarity: String? = null,
    val artist: String? = null,
    val maxCopies: Int? = null,
    val banned: Boolean = false,
    val image: String? = null,
) {
    val domainList: List<Domain> get() = domains.mapNotNull { d -> Domain.entries.firstOrNull { it.name.equals(d, true) } }
    val isChampion: Boolean get() = "champion" in superTypes
    val landscape: Boolean get() = type == "battlefield"
}

/** The filter chips: one per card type shown in the library. */
enum class CardType(val label: String, val types: Set<String>) {
    ALL("All", setOf("unit", "spell", "gear", "legend", "battlefield", "rune")),
    UNITS("Units", setOf("unit")),
    SPELLS("Spells", setOf("spell")),
    GEAR("Gear", setOf("gear")),
    LEGENDS("Legends", setOf("legend")),
    FIELDS("Fields", setOf("battlefield")),
    RUNES("Runes", setOf("rune")),
}

/** A number on a card that the filters compare. */
enum class Stat(val label: String) { ENERGY("Energy"), POWER("Power"), MIGHT("Might") }

/** Comparisons, as Scryfall offers them; [symbol] is what the compact field shows. */
enum class Compare(val symbol: String, val label: String, val test: (Int, Int) -> Boolean) {
    EQ("=", "equal to", { a, b -> a == b }),
    LT("<", "less than", { a, b -> a < b }),
    GT(">", "greater than", { a, b -> a > b }),
    LE("≤", "less than or equal to", { a, b -> a <= b }),
    GE("≥", "greater than or equal to", { a, b -> a >= b }),
    NE("≠", "not equal to", { a, b -> a != b }),
}

/** One stats row: e.g. Might ≥ 4. Rows combine with AND, so "might between 4 and 6" is ≥ 4 plus ≤ 6. */
data class StatFilter(val stat: Stat, val compare: Compare, val value: Int) {
    val text: String get() = "${stat.label} ${compare.symbol} $value"
}

enum class CardSort(val label: String) { ENERGY("Energy"), NAME("Name"), SET("Set") }

/** Everything the card search can narrow by. */
data class CardQuery(
    /** Searched in card names only. */
    val text: String = "",
    /** Words that must all appear in the rules text, in any order (Scryfall's "Text"). */
    val rulesText: String = "",
    /** Type-line tags (champion, region, species…); the card needs every one. */
    val tags: Set<String> = emptySet(),
    val domains: Set<Domain> = emptySet(),
    val type: CardType = CardType.ALL,
    val stats: List<StatFilter> = emptyList(),
    /** The card must have every one of these. */
    val keywords: Set<String> = emptySet(),
    val sort: CardSort = CardSort.ENERGY,
)

object Cards {
    var all: List<Card> = emptyList()
        private set

    /** Main sets newest first, then the starter and supplemental ones. */
    private val setOrder = listOf("Radiance", "Vendetta", "Unleashed", "Spiritforged", "Origins", "Proving Grounds")

    /** Loads assets/cards/cards.json. Token cards and rules reminders ("other") aren't shown. */
    fun install(json: String) {
        all = Json { ignoreUnknownKeys = true }.decodeFromString<List<Card>>(json).filter { it.type != "other" }
    }

    fun resetForTests() { all = emptyList() }

    fun bySlug(slug: String): Card? = all.firstOrNull { it.slug == slug }

    val sets: List<String> get() = all.map { it.set }.distinct().sortedWith(compareBy({ setRank(it) }, { it }))
    private fun setRank(set: String) = setOrder.indexOf(set).let { if (it < 0) 99 else it }

    /** Every tag on some card (champions, regions, species), alphabetical, with how many cards have it. */
    val tags: List<Pair<String, Int>> get() = all.flatMap { it.tags }.groupingBy { it }.eachCount().toList().sortedBy { it.first }

    private val symbolWords = Regex(""":rb_(?:energy_(\d+)|rune_([a-z]+)|([a-z]+)):""")

    /** Rules text as plain words: markup dropped, symbols as words ("2 energy", "fury rune", "might"). */
    fun plainText(card: Card): String = card.text
        .replace(symbolWords) { m ->
            val (energy, rune, other) = m.destructured
            when { energy.isNotEmpty() -> " $energy energy "; rune.isNotEmpty() -> " $rune rune "; else -> " $other " }
        }
        .replace("_(", "(").replace(")_", ")").replace("[", "").replace("]", "")

    /** Every keyword on some card, alphabetical, with how many cards have it. */
    val keywords: List<Pair<String, Int>> get() = all.flatMap { it.keywords }.groupingBy { it }.eachCount().toList().sortedBy { it.first }

    /**
     * A card's value for [stat], or null when it has none (it then never matches that stat, like
     * Scryfall). A card with an energy cost but no power cost has power 0.
     */
    fun value(card: Card, stat: Stat): Int? = when (stat) {
        Stat.ENERGY -> card.energy
        Stat.POWER -> card.power ?: if (card.energy != null) 0 else null
        Stat.MIGHT -> card.might
    }

    fun matches(c: Card, q: CardQuery): Boolean {
        val text = q.text.trim().lowercase()
        return c.type in q.type.types &&
            (q.domains.isEmpty() || c.domainList.any { it in q.domains }) &&
            (text.isEmpty() || c.name.lowercase().contains(text)) &&
            q.rulesText.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }.let { words ->
                words.isEmpty() || plainText(c).lowercase().let { t -> words.all { it in t } }
            } &&
            q.tags.all { tag -> c.tags.any { it.equals(tag, ignoreCase = true) } } &&
            q.keywords.all { k -> c.keywords.any { it.equals(k, ignoreCase = true) } } &&
            q.stats.all { f -> value(c, f.stat)?.let { f.compare.test(it, f.value) } ?: false }
    }

    /** The cards matching [q], in its sort order (cards without an energy cost last when sorting by energy). */
    fun search(q: CardQuery): List<Card> {
        val byNumber = compareBy<Card>({ setRank(it.set) }, { it.code ?: "" }, { it.name })
        val order = when (q.sort) {
            CardSort.ENERGY -> compareBy<Card>({ it.energy ?: Int.MAX_VALUE }, { it.name })
            CardSort.NAME -> compareBy { it.name }
            CardSort.SET -> byNumber
        }
        return all.filter { matches(it, q) }.sortedWith(order)
    }
}

/** Card rules text split into what to draw: words (plain, keyword, reminder) and symbols. */
object RulesText {
    sealed interface Part
    data class Words(val text: String, val keyword: Boolean = false, val reminder: Boolean = false) : Part
    data class Energy(val amount: Int) : Part
    data class Rune(val domain: Domain?) : Part // null = any domain
    data object Might : Part
    data object Exhaust : Part

    private val token = Regex(""":rb_([a-z0-9_]+):""")

    /**
     * OpenRift's markup: `[Keyword]`, `_(reminder text)_` and `:rb_…:` symbols (energy_N, might, exhaust,
     * rune_<domain>, rune_rainbow). Unknown symbols are dropped rather than shown as code.
     */
    fun parse(text: String): List<Part> {
        val out = mutableListOf<Part>()
        var reminder = false
        var i = 0
        val buf = StringBuilder()
        var keyword = false
        fun flush() { if (buf.isNotEmpty()) { out += Words(buf.toString(), keyword, reminder); buf.clear() } }
        while (i < text.length) {
            when {
                text.startsWith("_(", i) -> { flush(); reminder = true; buf.append('('); i += 2 }
                text.startsWith(")_", i) && reminder -> { buf.append(')'); flush(); reminder = false; i += 2 }
                text[i] == '[' && !keyword -> { flush(); keyword = true; i++ }
                text[i] == ']' && keyword -> { flush(); keyword = false; i++ }
                text[i] == ':' && token.matchAt(text, i) != null -> {
                    val m = token.matchAt(text, i)!!
                    flush()
                    symbol(m.groupValues[1])?.let { out += it }
                    i += m.value.length
                }
                else -> { buf.append(text[i]); i++ }
            }
        }
        flush()
        return out
    }

    private fun symbol(name: String): Part? = when {
        name == "might" -> Might
        name == "exhaust" -> Exhaust
        name.startsWith("energy_") -> name.removePrefix("energy_").toIntOrNull()?.let { Energy(it) }
        name == "rune_rainbow" -> Rune(null)
        name.startsWith("rune_") -> Domain.entries.firstOrNull { it.name.equals(name.removePrefix("rune_"), true) }?.let { Rune(it) }
        else -> null
    }
}
