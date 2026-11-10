package io.github.mustypit.rifttracker.ui

import android.content.Context
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.graphics.toArgb
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
open class AppTest {
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
        // A few real battlefields (the app adds the full OpenRift list when it has it).
        if (io.github.mustypit.rifttracker.game.Battlefields.all.isEmpty()) {
            io.github.mustypit.rifttracker.game.Battlefields.install(
                """[{"name": "Altar of Blood", "set": "Vendetta"}, {"name": "Sunken Temple", "set": "Origins"},
                    {"name": "Ravenbloom Conservatory", "set": "Spiritforged"}, {"name": "Risen Altar", "set": "Vendetta"}]""",
            )
        }
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

    /** Screenshots from the dark-mode run (AppDarkTest) get a "dark_" prefix. */
    private val prefix get() =
        if (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES) "dark_" else ""
    private fun shot(name: String) = rule.onRoot().captureRoboImage("screenshots/$prefix$name.png")
    private fun screenShot(name: String) = captureScreenRoboImage("screenshots/$prefix$name.png")

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
        rule.waitFor("New match")
        shot("01_new_match")
        rule.onNode(hasContentDescription("Your legend")).performClick(); rule.waitForIdle()
        rule.waitFor("RADIANCE")
        shot("01b_legend_picker")
        type("Search legends", "Riven")
        rule.onNode(hasContentDescription("Legend Riven")).performClick(); rule.waitForIdle()
        type("Their name", "Morgan")
        pickLegend("Their legend", "Mordekaiser")
        shot("02_new_match_filled")
        click("START")

        rule.waitFor("WHO GOES FIRST?")
        // Battlefields for game 1, picked from the library (optional).
        rule.onNode(hasContentDescription("Your battlefield")).performClick(); rule.waitForIdle()
        rule.waitFor("Search battlefields")
        shot("03a_battlefield_picker")
        type("Search battlefields", "Altar of")
        rule.onNode(hasContentDescription("Battlefield Altar of Blood")).performClick(); rule.waitForIdle()
        rule.onNode(hasContentDescription("Morgan's battlefield")).performClick(); rule.waitForIdle()
        type("Search battlefields", "Sunken")
        rule.onNode(hasContentDescription("Battlefield Sunken Temple")).performClick(); rule.waitForIdle()
        shot("03_who_goes_first")
        click("YOU")
        shot("04_counter_start")

        // Game 1: a back-and-forth, then the winning point by conquering (asks first).
        press("You", "HOLD"); press("Morgan", "CONQUER"); press("Morgan", "HOLD")
        press("You", "CONQUER", 2); press("Morgan", "ABILITY"); press("You", "HOLD", 2)
        // Tapping a score takes a point off; the middle pill offers to undo it.
        clock.addAndGet(40_000)
        rule.onNode(hasContentDescription("Morgan score 3")).performClick(); rule.waitForIdle()
        rule.onNode(hasText("TAP TO UNDO", substring = true)).assertExists()
        shot("05_counter_game")
        // The hidden panel: open it from the handle, add 2 XP, close it; the XP shows as a badge.
        // Drag from the centre (the handle by the middle bar) towards your edge to open it.
        rule.onNode(hasContentDescription("Show You extras")).performTouchInput { swipeDown(startY = top, endY = top + 400f) }; rule.waitForIdle()
        rule.onNode(hasContentDescription("You XP plus")).performClick()
        rule.onNode(hasContentDescription("You XP plus")).performClick(); rule.waitForIdle()
        rule.onNode(hasContentDescription("You XP 2")).assertExists()
        shot("05b_xp_panel")
        rule.onNode(hasContentDescription("Hide You extras")).performClick(); rule.waitForIdle()
        rule.onNode(hasContentDescription("You XP 2")).assertExists() // the badge
        press("You", "HOLD", 2)
        press("You", "CONQUER") // the winning point, no questions asked
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

        // Straight back to New match, still filled in for a rematch; no review screen.
        rule.waitFor("New match")
        rule.onNode(hasText("Riven")).assertExists()
        rule.onNode(hasText("Mordekaiser")).assertExists()
        shot("09_back_to_new_match")
        rule.waitUntil(10_000) { saved().isNotEmpty() }
        val m = saved().single()
        assertEquals(Outcome.WIN, MatchRules.result(m))
        assertEquals("2–1", MatchRules.scoreLine(m))
        assertEquals(listOf(Side.ME, Side.OPP, Side.ME), m.games.map { it.wentFirst })
        assertEquals(8, m.games[0].score(Side.ME))
        assertEquals(2, m.games[0].score(Side.OPP)) // 3 points minus the correction
        assertTrue(m.games[0].events.any { it.kind == PointKind.CORRECTION })
        assertEquals("XP is saved with the game", 2, m.games[0].xpOf(Side.ME))
        assertEquals("Morgan", m.oppName)
        assertNull(runBlocking(Dispatchers.IO) { store.activeNow() })

        // The review is still there, from Matches.
        click("Matches")
        rule.onNode(hasText("vs Mordekaiser")).performClick(); rule.waitForIdle()
        rule.waitFor("VICTORY")
        shot("09b_match_detail")
        assertEquals("Altar of Blood", m.games[0].battlefieldOf(Side.ME))
        assertEquals("Sunken Temple", m.games[0].battlefieldOf(Side.OPP))
        assertNull("game 2 had none picked", m.games[1].battlefieldOf(Side.ME))

        // Forgot one? Add it from the review afterwards.
        rule.onNode(androidx.compose.ui.test.hasScrollToNodeAction()).performScrollToNode(hasContentDescription("You battlefield: not set"))
        rule.onAllNodes(hasContentDescription("You battlefield: not set"))[0].performClick(); rule.waitForIdle()
        type("Search battlefields", "Risen")
        rule.onNode(hasContentDescription("Battlefield Risen Altar")).performClick(); rule.waitForIdle()
        rule.waitUntil(10_000) { saved().single().games[1].battlefieldOf(Side.ME) == "Risen Altar" }
    }

    @Test
    fun undoAndCorrections() {
        launch()
        pickLegend("Your legend", "Ahri"); pickLegend("Their legend", "Jinx")
        click("START"); click("YOU")
        press("You", "HOLD", 7)
        rule.onNode(hasContentDescription("You score 7")).assertExists()
        press("Opponent", "HOLD")
        rule.onNode(hasContentDescription("Undo")).performClick(); rule.waitForIdle()
        rule.onNode(hasContentDescription("Opponent score 0")).assertExists()
        // A stray tap on a score takes a point off; tapping the pill puts it back.
        rule.onNode(hasContentDescription("You score 7")).performClick(); rule.waitForIdle()
        rule.onNode(hasContentDescription("You score 6")).assertExists()
        rule.onNode(hasText("TAP TO UNDO", substring = true)).performClick(); rule.waitForIdle()
        rule.onNode(hasContentDescription("You score 7")).assertExists()
        rule.onNode(hasContentDescription("7 of 8 points")).assertExists()
        // Tapping a point on the side track removes that point; the pill (or Undo) puts it back.
        clock.addAndGet(40_000)
        rule.onNode(hasContentDescription("You point 3")).performClick(); rule.waitForIdle()
        rule.onNode(hasContentDescription("You score 6")).assertExists()
        rule.onNode(hasContentDescription("6 of 8 points")).assertExists()
        rule.onNode(hasText("TAP TO UNDO", substring = true)).performClick(); rule.waitForIdle()
        rule.onNode(hasContentDescription("You score 7")).assertExists()
        rule.onNode(hasContentDescription("You point 3")).performClick(); rule.waitForIdle()
        rule.onNode(hasContentDescription("Undo")).performClick(); rule.waitForIdle()
        rule.onNode(hasContentDescription("You score 7")).assertExists()
        // A score of 0 can't be tapped below zero.
        rule.onNode(hasContentDescription("Opponent score 0")).performClick(); rule.waitForIdle()
        rule.onNode(hasContentDescription("Opponent score 0")).assertExists()
        // Win, then take the winning point back: the game reopens.
        press("You", "HOLD")
        rule.waitFor("YOU WIN GAME 1")
        click("Undo")
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
        rule.waitFor("New match")
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
        click("Draw game")
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
        click("Fields")
        rule.waitFor("YOURS")
        shot("14b_battlefields")

        click("Yours")
        click("Ahri")
        rule.waitFor("best run")
        shot("15_legend_detail")
    }

    @Test
    fun logAPastMatch() {
        launch()
        click("Log a past match")
        rule.waitFor("Log a match")
        pickLegend("Your legend", "Diana")
        pickLegend("Their legend", "Irelia")
        click("Won")
        rule.waitFor("GAME 2")
        rule.onAllNodes(hasText("Lost"))[1].scrollIfNeeded().performClick(); rule.waitForIdle()
        rule.waitFor("GAME 3")
        rule.onAllNodes(hasText("Won"))[2].scrollIfNeeded().performClick(); rule.waitForIdle()
        shot("16_log_match")
        click("SAVE")
        rule.waitFor("New match")
        val m = saved().single()
        assertTrue(m.logged)
        assertEquals(listOf(Outcome.WIN, Outcome.LOSS, Outcome.WIN), m.games.map { it.outcome })
        assertEquals("Diana", m.myLegend)
    }

    @Test
    fun backgroundDarknessSetting() {
        launch()
        rule.onNode(hasContentDescription("Settings")).performClick(); rule.waitForIdle()
        rule.waitFor("SCORE BACKGROUND")
        shot("19_settings")
        rule.onNode(hasContentDescription("Background darkness"))
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(0.9f) }
        rule.waitForIdle()
        rule.waitFor("90%")
        shot("19b_settings_darker")
        // Kept after the app restarts.
        scenario?.close()
        launch()
        rule.onNode(hasContentDescription("Settings")).performClick(); rule.waitForIdle()
        rule.waitFor("90%")
        // Lightest allowed is the veil that passed the readability check.
        assertEquals(0.66f to 0.24f, Veil.alphas(0f))
        assertEquals(Veil.alphas(0f), Veil.alphas(-1f))
    }

    @Test
    fun themeSetting() {
        launch()
        rule.onNode(hasContentDescription("Settings")).performClick(); rule.waitForIdle()
        rule.waitFor("THEME")
        val other = if (this is AppDarkTest) "Light" else "Dark"
        click(other)
        rule.waitForIdle()
        shot("19c_settings_theme_switched")
        // Kept after the app restarts, and the whole app uses it.
        scenario?.close()
        launch()
        rule.waitForIdle()
        shot("19d_new_match_theme_switched")
        // The app now draws with the other palette (dark text on light, or the reverse).
        val inkIsLight = C.Ink.red + C.Ink.green + C.Ink.blue > 1.5f
        assertEquals(other == "Dark", inkIsLight)
    }

    /** Records the side track's pop frame by frame (screenshots/anim/), for checking the animation. */
    @Test
    fun pointPopAnimationFrames() {
        if (prefix.isNotEmpty()) return // once is enough
        launch()
        rule.waitFor("New match")
        pickLegend("Your legend", "Riven")
        type("Their name", "Morgan")
        pickLegend("Their legend", "Mordekaiser")
        click("START")
        rule.waitFor("WHO GOES FIRST?")
        click("YOU")
        press("You", "HOLD"); press("You", "CONQUER")
        rule.mainClock.autoAdvance = false
        var frame = 0
        fun frames(count: Int) = repeat(count) {
            rule.onRoot().captureRoboImage("screenshots/anim/pop_%03d.png".format(frame++))
            rule.mainClock.advanceTimeBy(20)
        }
        frames(4)
        // Scoring: the button squishes, the number rolls up, the point pops onto the track.
        clock.addAndGet(40_000)
        val ability = rule.onNode(hasContentDescription("You ABILITY"))
        ability.performTouchInput { down(center) }
        frames(6)
        ability.performTouchInput { up() }
        frames(26)
        clock.addAndGet(40_000)
        rule.onNode(hasContentDescription("You HOLD")).performClick()
        frames(30)
        // Taking a point off (tap the score): the number rolls down.
        clock.addAndGet(40_000)
        rule.onNode(hasContentDescription("You score 4")).performClick()
        frames(30)
        rule.mainClock.autoAdvance = true
        rule.onNode(hasContentDescription("You score 3")).assertExists()
    }

    /** Records screen changes, the score chart drawing in and win rates counting up (screenshots/anim/). */
    @Test
    fun screenAnimationFrames() {
        if (prefix.isNotEmpty()) return
        seedHistory()
        launch()
        rule.waitFor("New match")
        rule.mainClock.autoAdvance = false
        var frame = 0
        fun frames(count: Int) = repeat(count) {
            rule.onRoot().captureRoboImage("screenshots/anim/screens_%03d.png".format(frame++))
            rule.mainClock.advanceTimeBy(20)
        }
        fun tap(node: SemanticsNodeInteraction, count: Int) { node.performClick(); frames(count) }
        frames(3)
        tap(rule.onNode(hasText("Matches")), 14)          // tab: crossfade
        tap(rule.onAllNodes(hasText("vs Garen"))[0], 40)   // open a match: slide in, chart draws in
        tap(rule.onNode(hasContentDescription("Back")), 20) // back: slide out
        tap(rule.onNode(hasText("Legends")), 40)           // tab: crossfade, win rates count up
        tap(rule.onNode(hasText("Ahri")), 42)              // legend: slide in, big win rate counts up
        rule.mainClock.autoAdvance = true
        rule.waitFor("best run")
    }

    /** Card images load off the main thread; give them a moment before a screenshot. */
    private fun settleImages() { repeat(3) { Thread.sleep(250); rule.mainClock.advanceTimeBy(50); rule.waitForIdle() } }

    private fun pick(field: String, option: String) {
        rule.onNode(hasContentDescription(field)).performClick(); rule.waitForIdle()
        rule.onAllNodes(hasText(option)).onLast().performClick(); rule.waitForIdle() // the open menu's item
    }

    @Test
    fun cardLibrary() {
        launch()
        click("Cards")
        rule.waitFor("Search cards")
        settleImages()
        shot("20_cards")
        rule.onNode(hasContentDescription("Fury filter")).performClick()
        rule.onNode(hasContentDescription("Mind filter")).performClick()
        click("Units")
        // Might between 4 and 6, with Shield: two stats rows and a keyword.
        click("+ Filters")
        click("+ Add stat")
        pick("Stat 1", "Might"); pick("Comparison 1", "greater than or equal to"); pick("Value 1", "4")
        click("+ Add stat")
        pick("Stat 2", "Might"); pick("Comparison 2", "less than or equal to"); pick("Value 2", "6")
        rule.onNode(hasSetTextAction() and hasContentDescription("Add keyword")).performScrollTo().performTextInput("shi"); rule.waitForIdle()
                rule.onNode(hasContentDescription("Keyword Shield")).performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick); rule.waitForIdle()
        val q = io.github.mustypit.rifttracker.game.CardQuery(
            domains = setOf(io.github.mustypit.rifttracker.game.Domain.FURY, io.github.mustypit.rifttracker.game.Domain.MIND),
            type = io.github.mustypit.rifttracker.game.CardType.UNITS,
            stats = listOf(
                io.github.mustypit.rifttracker.game.StatFilter(io.github.mustypit.rifttracker.game.Stat.MIGHT, io.github.mustypit.rifttracker.game.Compare.GE, 4),
                io.github.mustypit.rifttracker.game.StatFilter(io.github.mustypit.rifttracker.game.Stat.MIGHT, io.github.mustypit.rifttracker.game.Compare.LE, 6),
            ),
            keywords = setOf("Shield"),
        )
        val expected = io.github.mustypit.rifttracker.game.Cards.search(q)
        assertTrue("some Fury/Mind units with might 4-6 and Shield", expected.isNotEmpty())
        assertTrue(expected.all { it.might in 4..6 && "Shield" in it.keywords })
        rule.waitFor("SHOW ${expected.size} CARDS")
        screenShot("20c_card_filters")
        click("SHOW ${expected.size} CARDS")
        rule.onNode(hasContentDescription("Remove Might ≥ 4")).assertExists()
        rule.onNode(hasContentDescription("Remove Might ≤ 6")).assertExists()
        rule.onNode(hasContentDescription("Remove Shield")).assertExists()
        rule.waitFor("${expected.size} CARDS")
        rule.onNode(hasContentDescription("Card ${expected.first().name}")).assertExists()
        settleImages()
        shot("20b_cards_filtered")
        // Removing a token widens the search again.
        rule.onNode(hasContentDescription("Remove Shield")).performClick(); rule.waitForIdle()
        rule.waitFor("${io.github.mustypit.rifttracker.game.Cards.search(q.copy(keywords = emptySet())).size} CARDS")
        rule.onNode(hasContentDescription("Card ${expected.first().name}")).performClick(); rule.waitForIdle()
        rule.waitFor("Cost")
        settleImages()
        shot("21_card")
        rule.onNode(hasContentDescription("Back")).performClick(); rule.waitForIdle()
        // Filters are kept after looking at a card.
        rule.onNode(hasContentDescription("Fury filter on")).assertExists()
        rule.onNode(hasContentDescription("Remove Might ≥ 4")).assertExists()
        // Rules text and tags, from the sheet; the search box itself looks at names only.
        click("+ Filters")
        click("Clear all")
        type("Rules text, e.g. draw 2", "draw 2")
        rule.onNode(hasSetTextAction() and hasContentDescription("Add tag")).performScrollTo().performTextInput("frel"); rule.waitForIdle()
        // Typing alone already searches by tag; picking from the list makes it an exact tag.
        val typedN = io.github.mustypit.rifttracker.game.Cards.search(io.github.mustypit.rifttracker.game.CardQuery(domains = q.domains, type = q.type, rulesText = "draw 2", tagText = "frel")).size
        rule.waitFor("SHOW $typedN CARDS")
        rule.onNode(hasContentDescription("Tag Freljord")).performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick); rule.waitForIdle()
        // The sheet resizes by dragging its handle.
        val topBefore = rule.onNode(hasText("Clear all")).fetchSemanticsNode().boundsInWindow.top
        rule.onNode(hasText("Filters")).performTouchInput { swipeUp(startY = centerY, endY = centerY - 300f) }; rule.waitForIdle()
        val topAfter = rule.onNode(hasText("Clear all")).fetchSemanticsNode().boundsInWindow.top
        assertTrue("dragging up makes the sheet taller ($topBefore -> $topAfter)", topAfter < topBefore - 100)
        screenShot("20d_card_text_tags")
        val tq = io.github.mustypit.rifttracker.game.CardQuery(
            domains = q.domains, type = q.type, rulesText = "draw 2", tags = setOf("Freljord"),
        )
        val n = io.github.mustypit.rifttracker.game.Cards.search(tq).size
        click("SHOW $n CARDS")
        rule.onNode(hasContentDescription("Remove Text: “draw 2”")).assertExists()
        rule.onNode(hasContentDescription("Remove Freljord")).assertExists()
    }

    /** Records browsing the card library (screenshots/anim/cards_*). */
    @Test
    fun cardLibraryFrames() {
        if (prefix.isNotEmpty()) return
        launch()
        click("Cards")
        rule.waitFor("Search cards")
        settleImages()
        rule.mainClock.autoAdvance = false
        var frame = 0
        fun frames(count: Int, settle: Boolean = false) = repeat(count) {
            if (settle && it % 4 == 0) Thread.sleep(120)
            rule.onRoot().captureRoboImage("screenshots/anim/cards_%03d.png".format(frame++))
            rule.mainClock.advanceTimeBy(20)
        }
        frames(10)
        rule.onNode(hasContentDescription("Calm filter")).performClick(); frames(8, true)
        rule.onNode(hasText("Units")).performClick(); frames(14, true)
        rule.onNode(hasContentDescription("Card Entrenched Boar")).performClick(); frames(34, true)
        rule.onNode(hasContentDescription("Back")).performClick(); frames(24, true)
        rule.mainClock.autoAdvance = true
    }

    @Test
    fun accentColour() {
        launch()
        rule.onNode(hasContentDescription("Settings")).performClick(); rule.waitForIdle()
        rule.waitFor("ACCENT")
        // A preset, then a custom hue behind "+".
        rule.onNode(hasContentDescription("Accent Lavender")).performScrollTo().performClick(); rule.waitForIdle()
        assertEquals(Accents.presets.first { it.first == "Lavender" }.second, C.Accent)
        rule.onNode(hasContentDescription("Accent Lavender on")).assertExists()
        shot("19e_settings_accent")
        rule.onAllNodes(hasContentDescription("Accent hue")).assertCountEquals(0)
        rule.onNode(hasContentDescription("Custom accent")).performScrollTo().performClick(); rule.waitForIdle()
        rule.onNode(hasContentDescription("Accent hue")).performScrollTo()
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(200f / 360f) }
        rule.waitForIdle()
        assertEquals(Accents.fromHue(200f), C.Accent)
        shot("19g_settings_accent_custom")
        // Kept after a restart, and used across the app (the tab bar's pill).
        scenario?.close()
        launch()
        rule.waitFor("New match")
        assertEquals(Accents.fromHue(200f).toArgb(), C.Accent.toArgb())
        shot("19f_new_match_accent")
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
                    battlefields = mapOf(
                        Side.ME to listOf("Altar of Blood", "Risen Altar", "Ravenbloom Conservatory")[(i + s.daysAgo) % 3],
                        Side.OPP to listOf("Sunken Temple", "Ravenbloom Conservatory")[(i + s.hour) % 2],
                    ),
                )
                t += 17 * 60_000
                g
            }
            store.save(Match(format = Format.BO3, myLegend = s.mine, oppName = s.name, oppLegend = s.opp, startedAt = start, endedAt = t, games = games))
        }
    }
}
