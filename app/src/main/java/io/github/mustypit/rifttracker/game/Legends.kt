package io.github.mustypit.rifttracker.game

/** Riftbound's six domains, with the colours printed on the cards. */
enum class Domain(val argb: Long) {
    FURY(0xFFE2483A), CALM(0xFF3FAE63), MIND(0xFF3B8BEB), BODY(0xFFF08A2E), CHAOS(0xFF7B4FC0), ORDER(0xFFE3B52B)
}

data class Legend(
    val id: String,
    val name: String,
    val set: String,
    val domains: List<Domain> = emptyList(),
    /** The legend card's title, e.g. "The Exile" (from Riftcodex). */
    val title: String = "",
    val artist: String? = null,
)

/** One entry of assets/legends/legends.json, written by scripts/build_legend_assets.py. */
@kotlinx.serialization.Serializable
data class LegendEntry(
    val name: String,
    val title: String = "",
    val set: String = "",
    val domains: List<String> = emptyList(),
    val artist: String? = null,
    val image: String? = null,
)

/**
 * The built-in legend library. Players pick from this; they never type a legend.
 *
 * Art: put `app/src/main/assets/legends/<id>.webp` (or .png / .jpg) in the app and it's used
 * automatically; without it the app draws a monogram tile.
 *
 * Gathered from public set coverage (Nov 2026); Unleashed lists 10 of its 12 legends so far.
 * Radiance (Oct 2026): nine legends per launch coverage.
 * Add new sets at the top of [sets] and their legends to [all]. Matches store the legend's name,
 * so renaming one here would split its history: don't.
 */
object Legends {
    /** Newest first. */
    private val builtInSets = listOf("Radiance", "Vendetta", "Unleashed", "Spiritforged", "Origins")
    val sets = builtInSets.toMutableList()

    private fun set(name: String, vararg legends: String) = legends.map { Legend(idOf(it), it, name, domainsOf[it].orEmpty()) }

    /**
     * Domain pairs known so far: from the cards themselves (Riven, Mordekaiser) and public set
     * coverage. To be replaced by Riftcodex data; a legend without domains gets neutral colours.
     */
    private val domainsOf: Map<String, List<Domain>> = mapOf(
        "Mordekaiser" to listOf(Domain.FURY, Domain.ORDER),
        "Riven" to listOf(Domain.CALM, Domain.CHAOS),
        "Azir" to listOf(Domain.CALM, Domain.ORDER),
        "Fiora" to listOf(Domain.BODY, Domain.ORDER),
        "Jax" to listOf(Domain.CALM, Domain.BODY),
        "Rumble" to listOf(Domain.MIND, Domain.FURY),
        "Draven" to listOf(Domain.FURY, Domain.CHAOS),
        "Ezreal" to listOf(Domain.MIND, Domain.CHAOS),
        "Shen" to listOf(Domain.CALM, Domain.ORDER),
        "Zed" to listOf(Domain.FURY, Domain.CHAOS),
        "Akali" to listOf(Domain.FURY, Domain.CALM),
        "Master Yi" to listOf(Domain.CALM, Domain.BODY),
        "Diana" to listOf(Domain.MIND, Domain.CHAOS),
    )

    private val builtIn: List<Legend> = listOf(
        set("Radiance", "Ekko", "Evelynn", "Jarvan IV", "K'Sante", "Mordekaiser", "Orianna", "Riven", "Seraphine", "Ziggs"),
        set("Vendetta", "Akali", "Ambessa", "Jayce", "Kennen", "Mel", "Nasus", "Renekton", "Shen", "Zed"),
        set("Unleashed", "Diana", "Ivern", "Kha'Zix", "Lillia", "Master Yi", "Poppy", "Pyke", "Rengar", "Vex", "Vi"),
        set("Spiritforged", "Azir", "Draven", "Ezreal", "Fiora", "Irelia", "Jax", "Lucian", "Ornn", "Rek'Sai", "Renata Glasc", "Rumble", "Sivir"),
        set("Origins", "Ahri", "Annie", "Darius", "Garen", "Jinx", "Kai'Sa", "Lee Sin", "Leona", "Lux", "Miss Fortune", "Sett", "Teemo", "Viktor", "Volibear", "Yasuo"),
    ).flatten()

    /** Built-in legends, updated with Riftcodex data once [install] has run. */
    @Volatile var all: List<Legend> = builtIn
        private set

    /** Riftcodex set labels that belong under one of the app's set headings. */
    private fun setHeading(label: String) = when {
        label.startsWith("Origins") || label.contains("Proving Grounds") -> "Origins"
        else -> label
    }

    /**
     * Merges Riftcodex data (the contents of legends.json) into the built-in list: known legends get
     * their domains, title and artist; legends the app doesn't know yet are added. Legends missing
     * from Riftcodex (a set it hasn't added yet) stay as they are.
     */
    /**
     * Merges legend files into the built-in list. Earlier files win for a name in several: pass
     * `legends.json` (Riftcodex, rewritten weekly) before `extra.json` (sets Riftcodex lacks).
     */
    fun install(vararg jsons: String) {
        val format = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val entries = jsons.flatMap { format.decodeFromString<List<LegendEntry>>(it) }.distinctBy { it.name.trim().lowercase() }
        val byKey = entries.associateBy { it.name.trim().lowercase() }
        fun domains(e: LegendEntry) = e.domains.mapNotNull { d -> Domain.entries.firstOrNull { it.name.equals(d, ignoreCase = true) } }
        val updated = builtIn.map { l ->
            val e = byKey[l.name.lowercase()] ?: return@map l
            l.copy(domains = domains(e).ifEmpty { l.domains }, title = e.title, artist = e.artist)
        }
        val known = builtIn.map { it.name.lowercase() }.toSet()
        val added = entries.filter { it.name.trim().lowercase() !in known }.map { e ->
            val set = setHeading(e.set).ifBlank { "Other" }
            if (set !in sets) sets.add(set)
            Legend(idOf(e.name), e.name.trim(), set, domains(e), e.title, e.artist)
        }
        all = updated + added
    }

    /** Tests: back to the built-in list. */
    fun resetForTests() {
        all = builtIn
        sets.clear(); sets.addAll(builtInSets)
    }

    /** "Kai'Sa" -> "kaisa", "Lee Sin" -> "lee_sin": the image file name. */
    fun idOf(name: String) = name.lowercase().filter { it.isLetter() || it == ' ' }.trim().replace(' ', '_')

    fun byName(name: String): Legend? = all.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }

    /** "MY" for Master Yi, "KA" for Kai'Sa. */
    fun initials(name: String): String {
        val words = name.split(' ').filter { it.isNotBlank() }
        return if (words.size >= 2) "${words[0][0]}${words[1][0]}".uppercase()
        else name.filter { it.isLetter() }.take(2).uppercase()
    }

    /** Picker contents: matches for [query] (name contains it), grouped by set, newest set first. */
    fun grouped(query: String): List<Pair<String, List<Legend>>> {
        val q = query.trim().lowercase()
        return sets.map { s -> s to all.filter { it.set == s && (q.isEmpty() || it.name.lowercase().contains(q)) }.sortedBy { it.name } }
            .filter { it.second.isNotEmpty() }
    }

    /** Recently used legends (newest first, from match history), only ones in the library. */
    fun recent(names: List<String>, limit: Int = 6): List<Legend> = names.mapNotNull(::byName).distinct().take(limit)
}
