package io.github.mustypit.rifttracker.game

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class Battlefield(val id: String, val name: String, val set: String, val artist: String? = null)

/** One entry of assets/battlefields/battlefields.json, written by scripts/build_legend_assets.py. */
@Serializable
data class BattlefieldEntry(val name: String, val set: String = "", val artist: String? = null, val image: String? = null)

/**
 * The battlefield library, from Riftcodex (no built-in list: battlefields are many and change every
 * set). Art: assets/battlefields/<id>.jpg. Games store the battlefield's name.
 */
object Battlefields {
    @Volatile var all: List<Battlefield> = emptyList()
        private set

    fun install(json: String) {
        all = Json { ignoreUnknownKeys = true }.decodeFromString<List<BattlefieldEntry>>(json)
            .map { Battlefield(Legends.idOf(it.name), it.name.trim(), it.set.ifBlank { "Other" }, it.artist) }
            .distinctBy { it.name.lowercase() }
    }

    fun resetForTests() { all = emptyList() }

    fun byName(name: String?): Battlefield? = name?.let { n -> all.firstOrNull { it.name.equals(n.trim(), ignoreCase = true) } }

    /** Picker contents for [query], grouped by set (newest of the app's legend sets first, others after). */
    fun grouped(query: String): List<Pair<String, List<Battlefield>>> {
        val q = query.trim().lowercase()
        val matching = all.filter { q.isEmpty() || it.name.lowercase().contains(q) }
        val order = Legends.sets + matching.map { it.set }.distinct().filter { it !in Legends.sets }
        return order.map { s -> s to matching.filter { it.set == s }.sortedBy { it.name } }.filter { it.second.isNotEmpty() }
    }

    /** Battlefields used recently (newest first), only ones in the library. */
    fun recent(names: List<String?>, limit: Int = 6): List<Battlefield> = names.mapNotNull(::byName).distinct().take(limit)
}
