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
) {
    fun score(side: Side): Int = events.filter { it.side == side }.sumOf { it.delta }
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
) {
    val isFinished get() = endedAt != null
    fun durationMs(now: Long): Long = (endedAt ?: now) - startedAt
    /** "Morgan (Jinx)", or just the legend when there's no name. */
    val opponentLabel get() = if (oppName.isBlank()) oppLegend else "$oppName ($oppLegend)"
}
