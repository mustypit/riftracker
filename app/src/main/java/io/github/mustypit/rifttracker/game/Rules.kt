package io.github.mustypit.rifttracker.game

/** What happened when a point button was pressed. */
sealed interface AddResult {
    data class Added(val game: Game) : AddResult
    /** Game already over, or the score would go below zero. */
    data object Ignored : AddResult
}

/** Scoring within one game. Pure functions: the clock is passed in. */
object GameRules {
    const val VICTORY_SCORE = 8

    /** Players know the final-point rules, so any kind of point can win the game. */
    fun add(game: Game, side: Side, kind: PointKind, now: Long): AddResult {
        if (game.isOver) return AddResult.Ignored
        val delta = if (kind == PointKind.CORRECTION) -1 else 1
        val score = game.score(side)
        if (score + delta < 0) return AddResult.Ignored
        val events = game.events + PointEvent(side, kind, delta, (now - game.startedAt).coerceAtLeast(0))
        val won = score + delta >= VICTORY_SCORE
        return AddResult.Added(
            game.copy(
                events = events,
                outcome = if (won) (if (side == Side.ME) Outcome.WIN else Outcome.LOSS) else null,
                endedAt = if (won) now else null,
            ),
        )
    }

    /** Takes back the last press; reopens the game if that press ended it. */
    fun undo(game: Game): Game {
        if (game.events.isEmpty()) return if (game.outcome == Outcome.DRAW) game.copy(outcome = null, endedAt = null) else game
        if (game.outcome == Outcome.DRAW) return game.copy(outcome = null, endedAt = null)
        return game.copy(events = game.events.dropLast(1), outcome = null, endedAt = null)
    }

    /** Time ran out or players agreed: a draw. */
    fun endAsDraw(game: Game, now: Long): Game = if (game.isOver) game else game.copy(outcome = Outcome.DRAW, endedAt = now)

    /** Score after each press, for the timeline: (event, my score, their score). */
    fun timeline(game: Game): List<Triple<PointEvent, Int, Int>> {
        var me = 0
        var opp = 0
        return game.events.map { e ->
            if (e.side == Side.ME) me += e.delta else opp += e.delta
            Triple(e, me, opp)
        }
    }

    /**
     * How each of [side]'s current points was scored, oldest first: one entry per point on the side
     * track. A correction (−1) takes away the most recent point.
     */
    fun scoredKinds(game: Game, side: Side): List<PointKind> {
        val kinds = ArrayDeque<PointKind>()
        game.events.filter { it.side == side }.forEach { e ->
            if (e.delta > 0) kinds.addLast(e.kind) else if (kinds.isNotEmpty()) kinds.removeLast()
        }
        return kinds.toList()
    }

    /** Changes [side]'s XP by [delta], never below zero. Works after the game ended too (to fix it). */
    fun adjustXp(game: Game, side: Side, delta: Int): Game =
        game.copy(xp = game.xp + (side to (game.xpOf(side) + delta).coerceAtLeast(0)))

    /** One line of the score chart: a press, the score after it, who leads, and whether the lead changed hands. */
    data class ChartRow(val event: PointEvent, val me: Int, val opp: Int, val leader: Side?, val leadChange: Boolean)

    fun chart(game: Game): List<ChartRow> {
        var lastLeader: Side? = null
        return timeline(game).map { (e, me, opp) ->
            val leader = when { me > opp -> Side.ME; opp > me -> Side.OPP; else -> null }
            // A lead change: someone takes the lead after the other player had it (a tie in between still counts).
            val change = leader != null && lastLeader != null && leader != lastLeader
            if (leader != null) lastLeader = leader
            ChartRow(e, me, opp, leader, change)
        }
    }

    /** How [side]'s current points were scored, per kind (conquer / hold / ability). */
    fun breakdown(game: Game, side: Side): Map<PointKind, Int> =
        listOf(PointKind.CONQUER, PointKind.HOLD, PointKind.ABILITY).associateWith { k -> scoredKinds(game, side).count { it == k } }

    /** The longest run of points one player scored in a row, unanswered: (who, how many), or null. */
    fun longestRun(game: Game): Pair<Side, Int>? {
        var best: Pair<Side, Int>? = null
        var who: Side? = null
        var run = 0
        game.events.filter { it.delta > 0 }.forEach { e ->
            run = if (e.side == who) run + 1 else 1
            who = e.side
            if (best == null || run > best!!.second) best = e.side to run
        }
        return best
    }

    /** The last press if it was a correction made less than [withinMs] ago (for the "−1 · UNDO" note). */
    fun recentCorrection(game: Game?, now: Long, withinMs: Long = 4000): PointEvent? {
        val e = game?.events?.lastOrNull() ?: return null
        return e.takeIf { it.kind == PointKind.CORRECTION && now - (game.startedAt + it.atMs) < withinMs }
    }
}

/** Best-of-1 / best-of-3 bookkeeping. */
object MatchRules {
    fun wins(m: Match) = m.games.count { it.outcome == Outcome.WIN }
    fun losses(m: Match) = m.games.count { it.outcome == Outcome.LOSS }
    fun draws(m: Match) = m.games.count { it.outcome == Outcome.DRAW }

    /** The game being played, if any. */
    fun current(m: Match): Game? = m.games.lastOrNull()?.takeIf { !it.isOver }

    /** True when no more games are needed: someone has enough wins, or all games are played. */
    fun isDecided(m: Match): Boolean {
        if (current(m) != null) return false
        return wins(m) >= m.format.winsNeeded || losses(m) >= m.format.winsNeeded || m.games.size >= m.format.maxGames
    }

    fun result(m: Match): Outcome = when {
        wins(m) > losses(m) -> Outcome.WIN
        losses(m) > wins(m) -> Outcome.LOSS
        else -> Outcome.DRAW
    }

    /** "2–1", or "1–1–1" when there were draws. */
    fun scoreLine(m: Match): String {
        val d = draws(m)
        return if (d == 0) "${wins(m)}–${losses(m)}" else "${wins(m)}–${losses(m)}–$d"
    }

    /** Whoever lost the previous game usually picks, and goes first. Null for game 1 or after a draw. */
    fun suggestedFirst(m: Match): Side? = when (m.games.lastOrNull()?.outcome) {
        Outcome.WIN -> Side.OPP
        Outcome.LOSS -> Side.ME
        else -> null
    }

    fun startGame(m: Match, wentFirst: Side?, now: Long): Match {
        require(current(m) == null && !isDecided(m)) { "No game to start" }
        return m.copy(games = m.games + Game(wentFirst = wentFirst, startedAt = now))
    }

    fun replaceCurrent(m: Match, game: Game): Match = m.copy(games = m.games.dropLast(1) + game)

    /** Undo works across the end of a game: it reopens the last game while the match isn't saved. */
    fun undo(m: Match): Match {
        val last = m.games.lastOrNull() ?: return m
        return replaceCurrent(m, GameRules.undo(last))
    }

    /** A match entered afterwards: just game results, no point log. */
    fun logged(format: Format, myLegend: String, oppName: String, oppLegend: String, results: List<Outcome>, firsts: List<Side?>, at: Long): Match =
        Match(
            format = format, myLegend = myLegend, oppName = oppName, oppLegend = oppLegend,
            startedAt = at, endedAt = at, logged = true,
            games = results.mapIndexed { i, o -> Game(wentFirst = firsts.getOrNull(i), startedAt = at, endedAt = at, outcome = o) },
        )

    /** Whether a list of logged game results makes a complete match for the format. */
    fun validLog(format: Format, results: List<Outcome>): Boolean {
        if (results.isEmpty() || results.size > format.maxGames) return false
        val m = Match(format = format, myLegend = "", oppLegend = "", startedAt = 0, games = results.map { Game(startedAt = 0, outcome = it) })
        // Every game before the last must have left the match undecided.
        val undecidedBefore = (1 until results.size).all { n -> !isDecided(m.copy(games = m.games.take(n))) }
        return isDecided(m) && undecidedBefore
    }
}

fun formatDuration(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
