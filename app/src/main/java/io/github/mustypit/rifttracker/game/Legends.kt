package io.github.mustypit.rifttracker.game

/** Riftbound's six domains, with the colours printed on the cards. */
enum class Domain(val argb: Long) {
    FURY(0xFFE2483A), CALM(0xFF3FAE63), MIND(0xFF3B8BEB), BODY(0xFFF08A2E), CHAOS(0xFF7B4FC0), ORDER(0xFFE3B52B)
}

data class Legend(val id: String, val name: String, val set: String, val domains: List<Domain> = emptyList())

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
    val sets = listOf("Radiance", "Vendetta", "Unleashed", "Spiritforged", "Origins")

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

    val all: List<Legend> = listOf(
        set("Radiance", "Ekko", "Evelynn", "Jarvan IV", "K'Sante", "Mordekaiser", "Orianna", "Riven", "Seraphine", "Ziggs"),
        set("Vendetta", "Akali", "Ambessa", "Jayce", "Kennen", "Mel", "Nasus", "Renekton", "Shen", "Zed"),
        set("Unleashed", "Diana", "Ivern", "Kha'Zix", "Lillia", "Master Yi", "Poppy", "Pyke", "Rengar", "Vex", "Vi"),
        set("Spiritforged", "Azir", "Draven", "Ezreal", "Fiora", "Irelia", "Jax", "Lucian", "Ornn", "Rek'Sai", "Renata Glasc", "Rumble", "Sivir"),
        set("Origins", "Ahri", "Annie", "Darius", "Garen", "Jinx", "Kai'Sa", "Lee Sin", "Leona", "Lux", "Miss Fortune", "Sett", "Teemo", "Viktor", "Volibear", "Yasuo"),
    ).flatten()

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
