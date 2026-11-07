package io.github.mustypit.rifttracker.game

data class Legend(val id: String, val name: String, val set: String)

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

    private fun set(name: String, vararg legends: String) = legends.map { Legend(idOf(it), it, name) }

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
