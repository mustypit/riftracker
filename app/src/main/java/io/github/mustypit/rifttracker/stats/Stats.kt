package io.github.mustypit.rifttracker.stats

import io.github.mustypit.rifttracker.game.Match
import io.github.mustypit.rifttracker.game.MatchRules
import io.github.mustypit.rifttracker.game.Outcome
import io.github.mustypit.rifttracker.game.Side
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Wins, losses, draws. Win rate counts a draw as a third of a win (3/1/0 points). */
data class Record(val wins: Int = 0, val losses: Int = 0, val draws: Int = 0) {
    val total get() = wins + losses + draws
    val winRate: Double? get() = if (total == 0) null else (3.0 * wins + draws) / (3.0 * total)
    operator fun plus(o: Outcome) = when (o) {
        Outcome.WIN -> copy(wins = wins + 1)
        Outcome.LOSS -> copy(losses = losses + 1)
        Outcome.DRAW -> copy(draws = draws + 1)
    }
    /** "12–7" or "12–7–3". */
    val line get() = if (draws == 0) "$wins–$losses" else "$wins–$losses–$draws"
}

data class LegendSummary(val legend: String, val record: Record, val lastPlayed: Long)

data class RivalSummary(val name: String, val record: Record, val legends: List<String>, val lastPlayed: Long)

data class LegendDetail(
    val legend: String,
    val record: Record,
    val games: Int,
    val bestRun: Int,
    /** Game results when you went first / second (games where it was recorded). */
    val goingFirst: Record,
    val goingSecond: Record,
    val against: List<LegendSummary>,
    val latest: List<Match>,
)

/** Matches played the same evening: gaps of less than [Stats.SESSION_GAP_MS]. Newest first. */
data class Session(val day: LocalDate, val matches: List<Match>, val record: Record)

data class MonthGroup(val month: YearMonth, val sessions: List<Session>, val record: Record)

enum class Period(val label: String, val days: Long?) { ALL("All time", null), MONTH("Last 30 days", 30), WEEK("Last 7 days", 7) }

object Stats {
    /** Show a win rate only after this many matches; before that it's mostly noise. */
    const val MIN_MATCHES_FOR_RATE = 3
    const val SESSION_GAP_MS = 3 * 60 * 60 * 1000L

    /** Matches played with the deck [deckId] (by the snapshot each match keeps). */
    fun deckMatches(matches: List<Match>, deckId: Long): List<Match> = matches.filter { it.deck?.id == deckId }.sortedByDescending { it.startedAt }

    /** The last [n] results, newest first. */
    fun form(matches: List<Match>, n: Int = 5): List<Outcome> = matches.sortedByDescending { it.startedAt }.take(n).map { MatchRules.result(it) }

    fun record(matches: List<Match>) = matches.fold(Record()) { r, m -> r + MatchRules.result(m) }

    fun inPeriod(matches: List<Match>, period: Period, now: Long): List<Match> {
        val days = period.days ?: return matches
        val cutoff = now - days * 24 * 60 * 60 * 1000
        return matches.filter { it.startedAt >= cutoff }
    }

    private fun key(name: String) = name.trim().lowercase()

    /** Groups by name ignoring case and spacing; shows the most recent spelling. */
    private fun <T> groupByName(matches: List<Match>, name: (Match) -> String, build: (String, List<Match>) -> T): List<T> =
        matches.filter { name(it).isNotBlank() }
            .groupBy { key(name(it)) }
            .map { (_, ms) -> build(name(ms.maxBy { it.startedAt }).trim(), ms) }

    /** Your legends, most played first (ties: most recent). */
    fun byMyLegend(matches: List<Match>): List<LegendSummary> =
        groupByName(matches, { it.myLegend }) { n, ms -> LegendSummary(n, record(ms), ms.maxOf { it.startedAt }) }
            .sortedWith(compareByDescending<LegendSummary> { it.record.total }.thenByDescending { it.lastPlayed })

    fun byOppLegend(matches: List<Match>): List<LegendSummary> =
        groupByName(matches, { it.oppLegend }) { n, ms -> LegendSummary(n, record(ms), ms.maxOf { it.startedAt }) }
            .sortedWith(compareByDescending<LegendSummary> { it.record.total }.thenByDescending { it.lastPlayed })

    /** People you've played, by name. Matches without a name don't count. */
    fun rivals(matches: List<Match>): List<RivalSummary> =
        groupByName(matches, { it.oppName }) { n, ms ->
            RivalSummary(
                n, record(ms),
                ms.sortedByDescending { it.startedAt }.map { it.oppLegend.trim() }.distinctBy(::key),
                ms.maxOf { it.startedAt },
            )
        }.sortedWith(compareByDescending<RivalSummary> { it.record.total }.thenByDescending { it.lastPlayed })

    /** Longest streak of match wins, in the order they were played. */
    fun bestRun(matches: List<Match>): Int {
        var best = 0
        var run = 0
        matches.sortedBy { it.startedAt }.forEach { m ->
            run = if (MatchRules.result(m) == Outcome.WIN) run + 1 else 0
            best = maxOf(best, run)
        }
        return best
    }

    fun legendDetail(matches: List<Match>, legend: String, latestCount: Int = 5): LegendDetail {
        val mine = matches.filter { key(it.myLegend) == key(legend) }
        var first = Record()
        var second = Record()
        mine.flatMap { it.games }.forEach { g ->
            val o = g.outcome ?: return@forEach
            when (g.wentFirst) {
                Side.ME -> first += o
                Side.OPP -> second += o
                null -> {}
            }
        }
        return LegendDetail(
            legend = mine.maxByOrNull { it.startedAt }?.myLegend?.trim() ?: legend,
            record = record(mine),
            games = mine.sumOf { m -> m.games.count { it.outcome != null } },
            bestRun = bestRun(mine),
            goingFirst = first,
            goingSecond = second,
            against = byOppLegend(mine),
            latest = mine.sortedByDescending { it.startedAt }.take(latestCount),
        )
    }

    /** Newest first: months, then evenings within them. */
    fun group(matches: List<Match>, zone: ZoneId): List<MonthGroup> {
        val sorted = matches.sortedByDescending { it.startedAt }
        val sessions = mutableListOf<MutableList<Match>>()
        sorted.forEach { m ->
            val cur = sessions.lastOrNull()
            val prev = cur?.last()
            // Going backwards in time: same session if this match ended (or started) shortly before the next one.
            if (prev != null && prev.startedAt - (m.endedAt ?: m.startedAt) < SESSION_GAP_MS) cur.add(m)
            else sessions.add(mutableListOf(m))
        }
        val built = sessions.map { ms ->
            Session(Instant.ofEpochMilli(ms.last().startedAt).atZone(zone).toLocalDate(), ms, record(ms))
        }
        return built.groupBy { YearMonth.from(it.day) }
            .map { (month, ss) -> MonthGroup(month, ss, ss.fold(Record()) { r, s -> Record(r.wins + s.record.wins, r.losses + s.record.losses, r.draws + s.record.draws) }) }
    }

    /**
     * Game results per battlefield played by [side] (yours, or your opponents'), most played first.
     * Counted per game, since each game of a best-of-3 can use a different battlefield.
     */
    fun byBattlefield(matches: List<Match>, side: Side): List<LegendSummary> =
        matches.flatMap { m -> m.games.mapNotNull { g -> val bf = g.battlefieldOf(side); val o = g.outcome; if (bf != null && o != null) Triple(bf, o, m.startedAt) else null } }
            .groupBy { key(it.first) }
            .map { (_, gs) -> LegendSummary(gs.maxBy { it.third }.first, gs.fold(Record()) { r, g -> r + g.second }, gs.maxOf { it.third }) }
            .sortedWith(compareByDescending<LegendSummary> { it.record.total }.thenByDescending { it.lastPlayed })

    fun percent(rate: Double?): String = if (rate == null) "–" else "${Math.round(rate * 100)}%"

    /** "59%", or "2 more matches" while there aren't enough to say. */
    fun rateOrHint(r: Record): String {
        val missing = MIN_MATCHES_FOR_RATE - r.total
        return if (missing > 0) "$missing more ${if (missing == 1) "match" else "matches"}" else percent(r.winRate)
    }
}
