package io.github.mustypit.rifttracker.ui

import android.content.Context
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import io.github.mustypit.rifttracker.data.AppDb
import io.github.mustypit.rifttracker.data.MatchStore
import io.github.mustypit.rifttracker.game.Format
import io.github.mustypit.rifttracker.game.Game
import io.github.mustypit.rifttracker.game.Match
import io.github.mustypit.rifttracker.game.MatchRules
import io.github.mustypit.rifttracker.game.Outcome
import io.github.mustypit.rifttracker.game.PointEvent
import io.github.mustypit.rifttracker.game.PointKind
import io.github.mustypit.rifttracker.game.Side
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

private val NOW = LocalDateTime.parse("2026-11-07T21:00").toInstant(ZoneOffset.UTC).toEpochMilli()

/**
 * Drives the real app (database, ViewModel, screens) on the JVM and saves screenshots to
 * app/screenshots/. Run with: scripts/gradle-retry.sh recordRoborazziDebug
 */
@OptIn(ExperimentalTestApi::class, ExperimentalRoborazziApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class AppTest {
    @get:Rule val rule = createEmptyComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val store get() = MatchStore(AppDb.get(context).matches())
    private val clock = AtomicLong(NOW)
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        AppGraph.clock = { clock.get() }
        AppGraph.random = Random(7)
        Zone.id = ZoneOffset.UTC
        runBlocking(Dispatchers.IO) { AppDb.get(context).clearAllTables() }
    }

    @After
    fun tearDown() {
        scenario?.close()
        AppDb.resetForTests()
    }

    private fun launch() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        rule.waitForIdle()
    }

    private fun shot(name: String) = rule.onRoot().captureRoboImage("screenshots/$name.png")
    private fun screenShot(name: String) = captureScreenRoboImage("screenshots/$name.png")

    private fun ComposeTestRule.waitFor(text: String, substring: Boolean = false) {
        waitUntilAtLeastOneExists(hasText(text, substring = substring), timeoutMillis = 10_000)
        waitForIdle()
    }

    /** Scrolls to the node first when it's inside a scrolling list. */
    private fun SemanticsNodeInteraction.scrollIfNeeded(): SemanticsNodeInteraction = apply { runCatching { performScrollTo() } }

    private fun click(text: String) { rule.onNode(hasText(text)).scrollIfNeeded().performClick(); rule.waitForIdle() }

    /** A counter button press, 40 seconds after the previous one. */
    private fun press(who: String, what: String, times: Int = 1) = repeat(times) {
        clock.addAndGet(40_000)
        rule.onNode(hasContentDescription("$who $what")).performClick()
        rule.waitForIdle()
    }

    private fun type(label: String, text: String) {
        rule.onNode(hasSetTextAction() and hasText(label)).scrollIfNeeded().performTextInput(text)
        rule.waitForIdle()
    }

    /** Opens a legend slot, searches the library and taps the legend. */
    private fun pickLegend(slot: String, legend: String) {
        rule.onNode(hasContentDescription(slot)).scrollIfNeeded().performClick(); rule.waitForIdle()
        type("Search legends", legend)
        rule.onNode(hasContentDescription("Legend $legend")).performClick(); rule.waitForIdle()
    }

    private fun saved(): List<Match> = runBlocking(Dispatchers.IO) { store.finished.first() }

    @Test
    fun playABestOfThreeFromSetupToSavedMatch() {
        launch()
        rule.waitFor("Who's playing?")
        shot("01_new_match")
        rule.onNode(hasContentDescription("Your legend")).performClick(); rule.waitForIdle()
        rule.waitFor("RADIANCE")
        shot("01b_legend_picker")
        type("Search legends", "Ahri")
        rule.onNode(hasContentDescription("Legend Ahri")).performClick(); rule.waitForIdle()
        type("Their name (optional)", "Morgan")
        pickLegend("Their legend", "Jinx")
        shot("02_new_match_filled")
        click("START")

        rule.waitFor("WHO GOES FIRST?")
        shot("03_who_goes_first")
        click("YOU")
        shot("04_counter_start")

        // Game 1: a back-and-forth, then the winning point by conquering (asks first).
        press("You", "HOLD"); press("Morgan", "CONQUER"); press("Morgan", "HOLD")
        press("You", "CONQUER", 2); press("Morgan", "ABILITY"); press("You", "HOLD", 2)
        press("Morgan", "minus one")
        shot("05_counter_game")
        press("You", "HOLD", 2)
        press("You", "CONQUER")
        rule.waitFor("Winning point by conquering?")
        screenShot("06_final_point_check")
        click("Count it")
        rule.waitFor("YOU WIN GAME 1")
        shot("07_game_won")

        // Game 2: Morgan lost, so they're suggested to go first; they win by holding.
        click("MORGAN ★")
        press("Morgan", "HOLD", 8)
        rule.waitFor("MORGAN WINS GAME 2")

        // Game 3: you go first and win; the match is decided.
        click("YOU ★")
        press("Morgan", "HOLD", 3)
        press("You", "ABILITY", 8)
        rule.waitFor("Match won 2–1")
        shot("08_match_won")
        click("SAVE MATCH")

        rule.waitFor("VICTORY")
        shot("09_match_detail")
        val m = saved().single()
        assertEquals(Outcome.WIN, MatchRules.result(m))
        assertEquals("2–1", MatchRules.scoreLine(m))
        assertEquals(listOf(Side.ME, Side.OPP, Side.ME), m.games.map { it.wentFirst })
        assertEquals(8, m.games[0].score(Side.ME))
        assertEquals(2, m.games[0].score(Side.OPP)) // 3 points minus the correction
        assertTrue(m.games[0].events.any { it.kind == PointKind.CORRECTION })
        assertEquals("Morgan", m.oppName)
        assertNull(runBlocking(Dispatchers.IO) { store.activeNow() })
    }

    @Test
    fun undoAndDeclineTheFinalConquer() {
        launch()
        pickLegend("Your legend", "Ahri"); pickLegend("Their legend", "Jinx")
        click("START"); click("YOU")
        press("You", "HOLD", 7)
        press("You", "CONQUER")
        click("Cancel")
        rule.onNode(hasContentDescription("You score 7")).assertExists()
        press("Opponent", "HOLD")
        rule.onNode(hasContentDescription("Undo")).performClick(); rule.waitForIdle()
        rule.onNode(hasContentDescription("Opponent score 0")).assertExists()
        // Win, then take the winning point back: the game reopens.
        press("You", "HOLD")
        rule.waitFor("YOU WIN GAME 1")
        click("Undo last point")
        rule.onNode(hasContentDescription("You score 7")).assertExists()
    }

    @Test
    fun aMatchInProgressSurvivesTheAppRestarting() {
        launch()
        pickLegend("Your legend", "Garen"); pickLegend("Their legend", "Zed")
        click("Best of 1")
        click("START"); click("FLIP A COIN")
        rule.waitFor("COIN:", substring = true)
        shot("10_coin_flip")
        press("You", "HOLD", 3)
        press("Opponent", "CONQUER", 2)
        Thread.sleep(300) // let the save land
        rule.waitForIdle()

        // A new activity and ViewModel, reading the match back from the database.
        scenario?.close()
        launch()
        rule.waitUntilAtLeastOneExists(hasContentDescription("You score 3"), 10_000)
        rule.onNode(hasContentDescription("Opponent score 2")).assertExists()
        rule.onNode(hasText("FIRST TO 8")).assertExists()

        // Abandon it: nothing saved.
        rule.onNode(hasContentDescription("Match options")).performClick(); rule.waitForIdle()
        click("Abandon match")
        click("Abandon")
        rule.waitFor("Who's playing?")
        assertTrue(saved().isEmpty())
        assertNull(runBlocking(Dispatchers.IO) { store.activeNow() })
    }

    @Test
    fun aDrawnGameInABestOfOne() {
        launch()
        pickLegend("Your legend", "Ahri"); pickLegend("Their legend", "Jinx")
        click("Best of 1")
        click("START"); click("YOU")
        press("You", "HOLD", 2)
        rule.onNode(hasContentDescription("Match options")).performClick(); rule.waitForIdle()
        click("End game as a draw")
        rule.waitFor("DRAW")
        click("SAVE MATCH")
        rule.waitUntil(10_000) { saved().isNotEmpty() }
        assertEquals(Outcome.DRAW, MatchRules.result(saved().single()))
    }

    @Test
    fun historyAndStats() {
        seedHistory()
        launch()
        click("Matches")
        rule.waitFor("NOVEMBER 2026")
        shot("11_matches")

        click("Legends")
        rule.waitFor("legends played", substring = true)
        shot("12_legends")
        click("Against")
        rule.waitFor("vs Jinx")
        shot("13_against")
        click("Rivals")
        rule.waitFor("Morgan")
        shot("14_rivals")

        click("Your legends")
        click("Ahri")
        rule.waitFor("best run")
        shot("15_legend_detail")
    }

    @Test
    fun logAPastMatch() {
        launch()
        click("Log a match you already played")
        rule.waitFor("Already played")
        pickLegend("Your legend", "Diana")
        pickLegend("Their legend", "Irelia")
        click("Won")
        rule.waitFor("GAME 2")
        rule.onAllNodes(hasText("Lost"))[1].scrollIfNeeded().performClick(); rule.waitForIdle()
        rule.waitFor("GAME 3")
        rule.onAllNodes(hasText("Won"))[2].scrollIfNeeded().performClick(); rule.waitForIdle()
        shot("16_log_match")
        click("SAVE")
        rule.waitFor("Who's playing?")
        val m = saved().single()
        assertTrue(m.logged)
        assertEquals(listOf(Outcome.WIN, Outcome.LOSS, Outcome.WIN), m.games.map { it.outcome })
        assertEquals("Diana", m.myLegend)
    }

    @Test
    fun emptyHistory() {
        launch()
        click("Matches")
        rule.waitFor("No matches yet")
        shot("17_matches_empty")
        click("Legends")
        rule.waitFor("No legends yet")
        shot("18_legends_empty")
    }

    /** Two weeks of evenings with a few legends and named opponents. */
    private fun seedHistory() = runBlocking(Dispatchers.IO) {
        val day = 24 * 60 * 60 * 1000L
        data class S(val daysAgo: Int, val hour: Int, val mine: String, val opp: String, val name: String, val results: List<Outcome>)
        val W = Outcome.WIN; val L = Outcome.LOSS; val D = Outcome.DRAW
        listOf(
            S(0, 19, "Ahri", "Jinx", "Morgan", listOf(W, L, W)),
            S(0, 20, "Ahri", "Garen", "Tyler", listOf(L, L)),
            S(0, 20, "Ahri", "Irelia", "", listOf(W, W)),
            S(3, 18, "Ahri", "Jinx", "Morgan", listOf(W, W)),
            S(3, 19, "Diana", "Zed", "Riley", listOf(D, W, L)),
            S(6, 20, "Ahri", "Garen", "Tyler", listOf(W, L, W)),
            S(6, 21, "Diana", "Jinx", "Morgan", listOf(L, W, L)),
            S(10, 19, "Akali", "Rek'Sai", "Sam", listOf(W, W)),
            S(13, 20, "Ahri", "Jinx", "Morgan", listOf(L, W, W)),
            S(40, 20, "Ahri", "Lux", "", listOf(W, W)),
        ).forEach { s ->
            val start = NOW - s.daysAgo * day - (21 - s.hour) * 3_600_000L
            var t = start
            val games = s.results.mapIndexed { i, o ->
                val g = Game(
                    wentFirst = if (i % 2 == 0) Side.ME else Side.OPP, startedAt = t, endedAt = t + 15 * 60_000, outcome = o,
                    events = listOf(PointEvent(if (o == Outcome.WIN) Side.ME else Side.OPP, PointKind.HOLD, 8, 14 * 60_000)),
                )
                t += 17 * 60_000
                g
            }
            store.save(Match(format = Format.BO3, myLegend = s.mine, oppName = s.name, oppLegend = s.opp, startedAt = start, endedAt = t, games = games))
        }
    }
}
