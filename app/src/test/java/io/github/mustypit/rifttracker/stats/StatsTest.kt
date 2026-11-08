package io.github.mustypit.rifttracker.stats

import io.github.mustypit.rifttracker.game.Format
import io.github.mustypit.rifttracker.game.Game
import io.github.mustypit.rifttracker.game.Match
import io.github.mustypit.rifttracker.game.Outcome
import io.github.mustypit.rifttracker.game.Outcome.DRAW
import io.github.mustypit.rifttracker.game.Outcome.LOSS
import io.github.mustypit.rifttracker.game.Outcome.WIN
import io.github.mustypit.rifttracker.game.Side
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneOffset

private val zone = ZoneOffset.UTC
private fun at(s: String) = LocalDateTime.parse(s).toInstant(zone).toEpochMilli()

/** A Bo1 match: the result is the single game's. */
private fun m(result: Outcome, mine: String = "Ahri", opp: String = "Jinx", name: String = "", time: String = "2026-11-06T20:00", first: Side? = null) =
    Match(
        format = Format.BO1, myLegend = mine, oppLegend = opp, oppName = name,
        startedAt = at(time), endedAt = at(time) + 20 * 60_000,
        games = listOf(Game(wentFirst = first, startedAt = at(time), outcome = result, endedAt = at(time) + 1)),
    )

class StatsTest {

    @Test
    fun aDrawCountsAsAThirdOfAWin() {
        val r = Record(12, 7, 3)
        assertEquals((36.0 + 3) / 66, r.winRate!!, 1e-9)
        assertEquals("59%", Stats.percent(r.winRate))
        assertEquals("12–7–3", r.line)
        assertEquals("2–1", Record(2, 1).line)
        assertNull(Record().winRate)
    }

    @Test
    fun winRateWaitsForEnoughMatches() {
        assertEquals("3 more matches", Stats.rateOrHint(Record()))
        assertEquals("1 more match", Stats.rateOrHint(Record(1, 1)))
        assertEquals("67%", Stats.rateOrHint(Record(2, 1)))
    }

    @Test
    fun legendsAreGroupedIgnoringCaseAndSortedByMatches() {
        val ms = listOf(
            m(WIN, mine = "Ahri", time = "2026-11-01T20:00"),
            m(LOSS, mine = "ahri ", time = "2026-11-02T20:00"),
            m(WIN, mine = "Garen", time = "2026-11-05T20:00"),
            m(DRAW, mine = "Ahri", time = "2026-11-03T20:00"),
        )
        val legends = Stats.byMyLegend(ms)
        assertEquals(listOf("Ahri", "Garen"), legends.map { it.legend })
        assertEquals(Record(1, 1, 1), legends[0].record)
    }

    @Test
    fun bestRunIsTheLongestWinStreakInPlayOrder() {
        val results = listOf(WIN, WIN, LOSS, WIN, WIN, WIN, DRAW, WIN)
        val ms = results.mapIndexed { i, r -> m(r, time = "2026-11-%02dT20:00".format(i + 1)) }.shuffled()
        assertEquals(3, Stats.bestRun(ms))
        assertEquals(0, Stats.bestRun(listOf(m(LOSS))))
    }

    @Test
    fun legendDetailSplitsGoingFirstAndSecond() {
        val ms = listOf(
            m(WIN, first = Side.ME, time = "2026-11-01T20:00"),
            m(LOSS, first = Side.ME, time = "2026-11-02T20:00"),
            m(WIN, first = Side.OPP, opp = "Garen", time = "2026-11-03T20:00"),
            m(WIN, first = null, time = "2026-11-04T20:00"),
            m(LOSS, mine = "Zed", first = Side.OPP, time = "2026-11-05T20:00"),
        )
        val d = Stats.legendDetail(ms, "ahri")
        assertEquals(Record(3, 1), d.record)
        assertEquals(4, d.games)
        assertEquals(Record(1, 1), d.goingFirst)
        assertEquals(Record(1, 0), d.goingSecond)
        assertEquals(listOf("Jinx", "Garen"), d.against.map { it.legend })
        assertEquals(2, d.bestRun)
        assertEquals("newest first", at("2026-11-04T20:00"), d.latest.first().startedAt)
    }

    @Test
    fun rivalsNeedANameAndListTheirLegends() {
        val ms = listOf(
            m(WIN, name = "Morgan", opp = "Jinx", time = "2026-11-01T20:00"),
            m(LOSS, name = "morgan", opp = "Garen", time = "2026-11-02T20:00"),
            m(WIN, name = "", opp = "Zed"),
            m(WIN, name = "Tyler", opp = "Irelia"),
        )
        val rivals = Stats.rivals(ms)
        assertEquals(listOf("morgan", "Tyler"), rivals.map { it.name }) // most recent spelling
        assertEquals(Record(1, 1), rivals[0].record)
        assertEquals(listOf("Garen", "Jinx"), rivals[0].legends)
    }

    @Test
    fun matchesGroupIntoEveningsAndMonths() {
        val ms = listOf(
            m(WIN, time = "2026-11-06T19:00"),
            m(LOSS, time = "2026-11-06T19:40"),
            m(WIN, time = "2026-11-06T21:30"), // 1h30 after the previous one ended: same evening
            m(WIN, time = "2026-11-02T20:00"),
            m(DRAW, time = "2026-10-30T20:00"),
        )
        val months = Stats.group(ms, zone)
        assertEquals(listOf(YearMonth.of(2026, 11), YearMonth.of(2026, 10)), months.map { it.month })
        val nov = months[0]
        assertEquals(listOf(3, 1), nov.sessions.map { it.matches.size })
        assertEquals(LocalDate.of(2026, 11, 6), nov.sessions[0].day)
        assertEquals(Record(2, 1), nov.sessions[0].record)
        assertEquals(Record(3, 1), nov.record)
        assertEquals("newest first inside an evening", at("2026-11-06T21:30"), nov.sessions[0].matches.first().startedAt)
    }

    @Test
    fun aLongBreakStartsANewSession() {
        val ms = listOf(m(WIN, time = "2026-11-06T14:00"), m(WIN, time = "2026-11-06T20:00"))
        assertEquals(2, Stats.group(ms, zone)[0].sessions.size)
    }

    @Test
    fun periodsFilterByStartTime() {
        val now = at("2026-11-07T12:00")
        val ms = listOf(m(WIN, time = "2026-11-06T20:00"), m(WIN, time = "2026-10-20T20:00"), m(WIN, time = "2026-08-01T20:00"))
        assertEquals(3, Stats.inPeriod(ms, Period.ALL, now).size)
        assertEquals(2, Stats.inPeriod(ms, Period.MONTH, now).size)
        assertEquals(1, Stats.inPeriod(ms, Period.WEEK, now).size)
    }

    @Test
    fun bestOfThreeMatchResultDecidesTheRecord() {
        val g = { o: Outcome -> Game(startedAt = 0, outcome = o, endedAt = 1) }
        val match = Match(format = Format.BO3, myLegend = "Ahri", oppLegend = "Jinx", startedAt = 0, endedAt = 1, games = listOf(g(LOSS), g(WIN), g(WIN)))
        assertEquals(Record(1, 0, 0), Stats.record(listOf(match)))
        assertEquals(3, Stats.legendDetail(listOf(match), "Ahri").games)
    }
}

class BattlefieldStatsTest {
    private fun g(o: Outcome, mine: String?, theirs: String?) =
        Game(startedAt = 0, endedAt = 1, outcome = o).withBattlefield(Side.ME, mine).withBattlefield(Side.OPP, theirs)

    @Test
    fun recordPerBattlefieldCountsGames() {
        val m1 = Match(format = Format.BO3, myLegend = "Ahri", oppLegend = "Jinx", startedAt = 10, endedAt = 20,
            games = listOf(g(WIN, "Altar of Blood", "Black Market"), g(LOSS, "Risen Altar", "Black Market"), g(WIN, "Altar of Blood", null)))
        val m2 = Match(format = Format.BO1, myLegend = "Ahri", oppLegend = "Zed", startedAt = 30, endedAt = 40,
            games = listOf(g(DRAW, "altar of blood", "Sunken Temple")))
        val mine = Stats.byBattlefield(listOf(m1, m2), Side.ME)
        assertEquals(listOf("altar of blood", "Risen Altar"), mine.map { it.legend }) // most recent spelling
        assertEquals(Record(2, 0, 1), mine[0].record)
        assertEquals(Record(0, 1), mine[1].record)
        val theirs = Stats.byBattlefield(listOf(m1, m2), Side.OPP)
        assertEquals(Record(1, 1), theirs.first { it.legend == "Black Market" }.record)
        assertEquals("games without a battlefield don't count", 3, theirs.sumOf { it.record.total })
    }

    @Test
    fun choosingAndClearingABattlefield() {
        val game = Game(startedAt = 0).withBattlefield(Side.ME, " Altar of Blood ")
        assertEquals("Altar of Blood", game.battlefieldOf(Side.ME))
        assertNull(game.withBattlefield(Side.ME, null).battlefieldOf(Side.ME))
    }
}
