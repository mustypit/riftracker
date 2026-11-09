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

    val sets: List<String> get() = all.map { it.set }.distinct().sortedWith(compareBy({ setOrder.indexOf(it).let { i -> if (i < 0) 99 else i } }, { it }))

    /**
     * Cards matching [query] (name, champion/region tags or keywords), any of [domains] (none selected
     * = all) and [type], grouped by set in [sets] order and by collector number within a set.
     */
    fun browse(query: String = "", domains: Set<Domain> = emptySet(), type: CardType = CardType.ALL): List<Pair<String, List<Card>>> {
        val q = query.trim().lowercase()
        val matching = all.filter { c ->
            c.type in type.types &&
                (domains.isEmpty() || c.domainList.any { it in domains }) &&
                (q.isEmpty() || c.name.lowercase().contains(q) || c.tags.any { it.lowercase().contains(q) } || c.keywords.any { it.lowercase().contains(q) })
        }
        val bySet = matching.groupBy { it.set }
        return sets.mapNotNull { s -> bySet[s]?.sortedWith(compareBy({ it.code ?: "" }, { it.name }))?.let { s to it } }
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
