package io.github.mustypit.rifttracker.game

import io.github.mustypit.rifttracker.game.PointKind.ABILITY
import io.github.mustypit.rifttracker.game.PointKind.CONQUER
import io.github.mustypit.rifttracker.game.PointKind.CORRECTION
import io.github.mustypit.rifttracker.game.PointKind.HOLD
import io.github.mustypit.rifttracker.game.Side.ME
import io.github.mustypit.rifttracker.game.Side.OPP
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Plays presses against a game the way the counter screen does, at one press per 30 s. */
private class Table(var game: Game = Game(wentFirst = ME, startedAt = 1_000_000)) {
    var now = 1_000_000L
    fun press(side: Side, kind: PointKind, confirmed: Boolean = false): AddResult {
        now += 30_000
        val r = GameRules.add(game, side, kind, now, confirmed)
        if (r is AddResult.Added) game = r.game
        return r
    }
    fun score() = game.score(ME) to game.score(OPP)
}

class GameRulesTest {

    @Test
    fun pointsAddUpPerSideAndKindIsKept() {
        val t = Table()
        t.press(ME, CONQUER); t.press(OPP, HOLD); t.press(ME, HOLD); t.press(ME, ABILITY)
        assertEquals(3 to 1, t.score())
        assertEquals(listOf(CONQUER, HOLD, HOLD, ABILITY), t.game.events.map { it.kind })
        assertEquals(30_000L, t.game.events.first().atMs)
        assertFalse(t.game.isOver)
    }

    @Test
    fun reachingEightByHoldingWinsAndRecordsTheEnd() {
        val t = Table()
        repeat(7) { t.press(ME, HOLD) }
        assertFalse(t.game.isOver)
        t.press(ME, HOLD)
        assertEquals(Outcome.WIN, t.game.outcome)
        assertEquals(t.now, t.game.endedAt)
        assertTrue("no presses after the end", t.press(OPP, HOLD) is AddResult.Ignored)
        assertEquals(8 to 0, t.score())
    }

    @Test
    fun opponentReachingEightIsALoss() {
        val t = Table()
        repeat(8) { t.press(OPP, ABILITY) }
        assertEquals(Outcome.LOSS, t.game.outcome)
    }

    @Test
    fun winningByConqueringAsksFirstAndCountsOnlyWhenConfirmed() {
        val t = Table()
        repeat(7) { t.press(ME, HOLD) }
        assertEquals(AddResult.ConfirmFinalConquer, t.press(ME, CONQUER))
        assertEquals("nothing changes until confirmed", 7 to 0, t.score())
        t.press(ME, CONQUER, confirmed = true)
        assertEquals(Outcome.WIN, t.game.outcome)
    }

    @Test
    fun conqueringBeforeTheLastPointNeedsNoCheck() {
        val t = Table()
        repeat(6) { t.press(ME, CONQUER) }
        assertTrue(t.press(ME, CONQUER) is AddResult.Added) // 7: not the winning point
        assertEquals(7 to 0, t.score())
    }

    @Test
    fun correctionTakesAPointAwayButNeverBelowZero() {
        val t = Table()
        assertTrue(t.press(ME, CORRECTION) is AddResult.Ignored)
        t.press(ME, HOLD); t.press(ME, HOLD)
        t.press(ME, CORRECTION)
        assertEquals(1 to 0, t.score())
        assertEquals(-1, t.game.events.last().delta)
    }

    @Test
    fun undoTakesBackTheLastPressEvenTheWinningOne() {
        val t = Table()
        repeat(8) { t.press(OPP, HOLD) }
        assertEquals(Outcome.LOSS, t.game.outcome)
        t.game = GameRules.undo(t.game)
        assertNull(t.game.outcome)
        assertNull(t.game.endedAt)
        assertEquals(0 to 7, t.score())
        t.press(ME, HOLD)
        assertEquals(1 to 7, t.score())
        assertEquals(Game(startedAt = 5), GameRules.undo(Game(startedAt = 5)))
    }

    @Test
    fun drawEndsTheGameAndUndoReopensIt() {
        val t = Table()
        t.press(ME, HOLD)
        t.game = GameRules.endAsDraw(t.game, t.now)
        assertEquals(Outcome.DRAW, t.game.outcome)
        t.game = GameRules.undo(t.game)
        assertNull(t.game.outcome)
        assertEquals("undoing a draw keeps the points", 1 to 0, t.score())
    }

    @Test
    fun timelineShowsTheRunningScore() {
        val t = Table()
        t.press(ME, CONQUER); t.press(OPP, HOLD); t.press(OPP, HOLD); t.press(ME, CORRECTION)
        assertEquals(listOf(1 to 0, 1 to 1, 1 to 2, 0 to 2), GameRules.timeline(t.game).map { it.second to it.third })
    }

    @Test
    fun sideTrackShowsHowEachCurrentPointWasScored() {
        val t = Table()
        t.press(ME, CONQUER); t.press(OPP, HOLD); t.press(ME, HOLD); t.press(ME, ABILITY)
        assertEquals(listOf(CONQUER, HOLD, ABILITY), GameRules.scoredKinds(t.game, ME))
        assertEquals(listOf(HOLD), GameRules.scoredKinds(t.game, OPP))
        t.press(ME, CORRECTION) // takes away the latest point (the ability)
        assertEquals(listOf(CONQUER, HOLD), GameRules.scoredKinds(t.game, ME))
        assertEquals(t.game.score(ME), GameRules.scoredKinds(t.game, ME).size)
    }

    @Test
    fun aRecentCorrectionIsReportedForAFewSeconds() {
        val t = Table()
        t.press(ME, HOLD)
        assertNull("last press wasn't a correction", GameRules.recentCorrection(t.game, t.now))
        t.press(ME, CORRECTION)
        assertEquals(PointKind.CORRECTION, GameRules.recentCorrection(t.game, t.now + 1000)?.kind)
        assertNull("gone after 4 s", GameRules.recentCorrection(t.game, t.now + 5000))
        assertNull(GameRules.recentCorrection(null, 0))
    }

    @Test
    fun durationsFormat() {
        assertEquals("0:00", formatDuration(0))
        assertEquals("4:05", formatDuration(245_000))
        assertEquals("1:02:03", formatDuration(3_723_000))
        assertEquals(60_000L, Game(startedAt = 0, endedAt = 60_000).durationMs(999_999))
        assertEquals(500L, Game(startedAt = 0).durationMs(500))
    }
}

class MatchRulesTest {
    private fun game(o: Outcome?) = Game(startedAt = 0, outcome = o, endedAt = if (o != null) 1 else null)
    private fun bo3(vararg o: Outcome?) = Match(format = Format.BO3, myLegend = "Ahri", oppLegend = "Jinx", startedAt = 0, games = o.map(::game))

    @Test
    fun bestOfThreeNeedsTwoWins() {
        assertFalse(MatchRules.isDecided(bo3()))
        assertFalse(MatchRules.isDecided(bo3(Outcome.WIN)))
        assertFalse(MatchRules.isDecided(bo3(Outcome.WIN, Outcome.LOSS)))
        assertTrue(MatchRules.isDecided(bo3(Outcome.WIN, Outcome.WIN)))
        assertTrue(MatchRules.isDecided(bo3(Outcome.LOSS, Outcome.WIN, Outcome.LOSS)))
        assertFalse("game in progress", MatchRules.isDecided(bo3(Outcome.WIN, Outcome.WIN, null)))
        assertEquals("2–1", MatchRules.scoreLine(bo3(Outcome.WIN, Outcome.LOSS, Outcome.WIN)))
        assertEquals(Outcome.LOSS, MatchRules.result(bo3(Outcome.LOSS, Outcome.WIN, Outcome.LOSS)))
    }

    @Test
    fun drawsInBestOfThree() {
        val m = bo3(Outcome.DRAW, Outcome.WIN, Outcome.LOSS)
        assertTrue("three games played", MatchRules.isDecided(m))
        assertEquals(Outcome.DRAW, MatchRules.result(m))
        assertEquals("1–1–1", MatchRules.scoreLine(m))
        assertTrue(MatchRules.isDecided(bo3(Outcome.DRAW, Outcome.DRAW, Outcome.WIN)))
        assertEquals(Outcome.WIN, MatchRules.result(bo3(Outcome.DRAW, Outcome.DRAW, Outcome.WIN)))
    }

    @Test
    fun bestOfOneIsOneGame() {
        val m = Match(format = Format.BO1, myLegend = "a", oppLegend = "b", startedAt = 0, games = listOf(game(Outcome.DRAW)))
        assertTrue(MatchRules.isDecided(m))
        assertEquals(Outcome.DRAW, MatchRules.result(m))
    }

    @Test
    fun loserOfThePreviousGameIsSuggestedToGoFirst() {
        assertNull(MatchRules.suggestedFirst(bo3()))
        assertEquals(Side.OPP, MatchRules.suggestedFirst(bo3(Outcome.WIN)))
        assertEquals(Side.ME, MatchRules.suggestedFirst(bo3(Outcome.LOSS)))
        assertNull(MatchRules.suggestedFirst(bo3(Outcome.DRAW)))
    }

    @Test
    fun startingGamesAndUndoAcrossTheEndOfAGame() {
        var m = bo3()
        m = MatchRules.startGame(m, Side.ME, now = 100)
        assertEquals(Side.ME, MatchRules.current(m)!!.wentFirst)
        var g = MatchRules.current(m)!!
        repeat(8) { g = (GameRules.add(g, Side.ME, PointKind.HOLD, 200) as AddResult.Added).game }
        m = MatchRules.replaceCurrent(m, g)
        assertNull(MatchRules.current(m))
        m = MatchRules.undo(m)
        assertEquals("the won game is open again", 7, MatchRules.current(m)!!.score(Side.ME))
    }

    @Test(expected = IllegalArgumentException::class)
    fun cannotStartAGameWhenTheMatchIsDecided() {
        MatchRules.startGame(bo3(Outcome.WIN, Outcome.WIN), null, 0)
    }

    @Test
    fun loggedMatchesMustBeComplete() {
        assertTrue(MatchRules.validLog(Format.BO3, listOf(Outcome.WIN, Outcome.WIN)))
        assertTrue(MatchRules.validLog(Format.BO3, listOf(Outcome.WIN, Outcome.LOSS, Outcome.DRAW)))
        assertFalse("not finished", MatchRules.validLog(Format.BO3, listOf(Outcome.WIN)))
        assertFalse("third game after it was decided", MatchRules.validLog(Format.BO3, listOf(Outcome.WIN, Outcome.WIN, Outcome.LOSS)))
        assertTrue(MatchRules.validLog(Format.BO1, listOf(Outcome.LOSS)))
        assertFalse(MatchRules.validLog(Format.BO1, emptyList()))
        val m = MatchRules.logged(Format.BO3, "Ahri", "", "Jinx", listOf(Outcome.LOSS, Outcome.WIN, Outcome.WIN), listOf(Side.ME, Side.ME, null), at = 5)
        assertTrue(m.logged && m.isFinished)
        assertEquals(Outcome.WIN, MatchRules.result(m))
        assertEquals(Side.ME, m.games[1].wentFirst)
    }

    @Test
    fun opponentLabel() {
        assertEquals("Jinx", bo3().opponentLabel)
        assertEquals("Morgan (Jinx)", bo3().copy(oppName = "Morgan").opponentLabel)
    }
}
