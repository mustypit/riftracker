package io.github.mustypit.rifttracker.game

import kotlinx.serialization.Serializable

/** The two seats at the table. Everything is from the phone owner's point of view. */
@Serializable
enum class Side { ME, OPP;
    val other get() = if (this == ME) OPP else ME
}

/** How a point was scored. CORRECTION is a manual −1. */
@Serializable
enum class PointKind { CONQUER, HOLD, ABILITY, CORRECTION }

@Serializable
enum class Outcome { WIN, LOSS, DRAW }

@Serializable
enum class Format(val maxGames: Int, val winsNeeded: Int, val label: String) {
    BO1(1, 1, "Best of 1"),
    BO3(3, 2, "Best of 3"),
}

/** One button press. [atMs] is milliseconds since the game started. */
@Serializable
data class PointEvent(val side: Side, val kind: PointKind, val delta: Int, val atMs: Long)

@Serializable
data class Game(
    val wentFirst: Side? = null,
    val startedAt: Long,
    val endedAt: Long? = null,
    val events: List<PointEvent> = emptyList(),
    /** Set when the game is over; from the owner's point of view. */
    val outcome: Outcome? = null,
    /** XP per player, set from the pull-up panel (not part of the score). */
    val xp: Map<Side, Int> = emptyMap(),
    /** The battlefield each player brought to this game, by name (optional). */
    val battlefields: Map<Side, String> = emptyMap(),
) {
    fun battlefieldOf(side: Side): String? = battlefields[side]
    fun withBattlefield(side: Side, name: String?): Game =
        copy(battlefields = if (name.isNullOrBlank()) battlefields - side else battlefields + (side to name.trim()))
    fun score(side: Side): Int = events.filter { it.side == side }.sumOf { it.delta }
    fun xpOf(side: Side): Int = xp[side] ?: 0
    val isOver get() = outcome != null
    fun durationMs(now: Long): Long = (endedAt ?: now) - startedAt
}

@Serializable
data class Match(
    val id: Long = 0,
    val format: Format,
    val myLegend: String,
    val oppName: String = "",
    val oppLegend: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val games: List<Game> = emptyList(),
    /** Entered afterwards from the Matches tab rather than counted live: no point log or times. */
    val logged: Boolean = false,
    /** The deck played, as it was when the match started (later edits don't change it). */
    val deck: Deck? = null,
) {
    val isFinished get() = endedAt != null
    fun durationMs(now: Long): Long = (endedAt ?: now) - startedAt
    /** "Morgan (Jinx)" for old matches that have a name; otherwise the legend. */
    val opponentLabel get() = if (oppName.isBlank()) oppLegend else "$oppName ($oppLegend)"
}
