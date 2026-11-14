package io.github.mustypit.rifttracker.game

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/** Deck codes: the format against Piltover Archive's own fixtures, then codes to and from our decks. */
class DeckCodesTest {
    @Before fun load() = Cards.install(File("src/main/assets/cards/cards.json").readText())
    @After fun reset() = Cards.resetForTests()

    private fun fixtures(name: String): List<JsonObject> =
        Json.parseToJsonElement(File("src/test/resources/deckcodes/$name").readText()).jsonArray.map { it.jsonObject }

    private fun entries(a: JsonArray?) = a.orEmpty().map { DeckCodeFormat.Entry(it.jsonObject["cardCode"]!!.jsonPrimitive.content, it.jsonObject["count"]!!.jsonPrimitive.int) }
    private fun JsonObject.str(k: String) = this[k]?.jsonPrimitive?.content

    @Test
    fun matchesTheReferenceImplementation() {
        for (g in fixtures("golden.json") + fixtures("golden-v6.json")) {
            val name = g.str("name")
            val input = g["input"]!!.jsonObject
            val legends = input["additionalLegends"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
            val code = DeckCodeFormat.encode(entries(input["mainDeck"]?.jsonArray), entries(input["sideboard"]?.jsonArray), input.str("chosenChampion"), legends)
            assertEquals("encode: $name", g.str("code"), code)
            val want = g["decoded"]!!.jsonObject
            val got = DeckCodeFormat.decode(g.str("code")!!)
            assertEquals("main: $name", entries(want["mainDeck"]?.jsonArray), got.main)
            assertEquals("side: $name", entries(want["sideboard"]?.jsonArray), got.sideboard)
            assertEquals("champion: $name", want.str("chosenChampion"), got.chosenChampion)
            assertEquals("legends: $name", want["additionalLegends"]?.jsonArray?.map { it.jsonPrimitive.content }, got.additionalLegends)
        }
    }

    /** The README's example: a Kai'Sa deck with runes, battlefields, a sideboard and a chosen champion. */
    private val readme = "CIAAAAAAAAAQCAAAA4AACAIAABMQAAILAAAAICIMDMOVOX3AM5UHIAIDAAACO6XYAEAQKAAABX3QDGACUABKIAQAAEBQAAAWDBOQCAQAABMHE"

    @Test
    fun readsADeckFromASite() {
        assertTrue(DeckCodeFormat.looksLikeCode(readme))
        assertFalse("a pasted list isn't a code", DeckCodeFormat.looksLikeCode("3 Cleave\n2 Disintegrate"))
        val (deck, unknown) = DeckCodes.toDeck(readme, "Imported")
        deck!!
        assertEquals(emptyList<String>(), unknown)
        assertEquals("daughter-of-the-void", deck.legend)
        assertEquals(mapOf("FURY" to 7, "MIND" to 5), deck.runes)
        assertEquals(listOf("Grove of the God-Willow", "Startipped Peak", "The Dreaming Tree"), deck.battlefields.sorted())
        assertEquals(8, deck.sideSize)
        assertEquals("the chosen champion", "kai-sa-survivor", deck.champion)
        // Legend, runes and battlefields come out of the code's main list; the 40 cards are left.
        assertEquals(40, deck.mainSize)
        assertEquals(3, deck.count(DeckCodes.cardFor("OGN-004")!!.slug))
    }

    @Test
    fun ourDecksRoundTrip() {
        val legend = Cards.all.first { it.name == "Daughter of the Void" }
        val champion = Cards.all.first { it.name == "Kai'Sa, Survivor" }
        var d = DeckRules.newDeck(legend, champion, "Kai'Sa")
        repeat(2) { d = DeckRules.add(d, champion) }
        val pool = Cards.all.filter { DeckRules.allowed(d, it) && !it.isChampion && !DeckRules.isSignature(it) && it.code?.contains('-') == true }
        pool.take(12).forEach { c -> repeat(3) { d = DeckRules.add(d, c) } }
        pool.drop(12).take(4).forEach { c -> repeat(2) { d = DeckRules.add(d, Board.SIDE, c) } }
        d = DeckRules.setRunes(d, 7).copy(battlefields = listOf("Cosmic Vista", "Targon's Peak", "Black Market"), maybe = mapOf(pool[20].slug to 1))
        val code = DeckCodes.fromDeck(d)
        val (back, unknown) = DeckCodes.toDeck(code, "Kai'Sa")
        back!!
        assertEquals(emptyList<String>(), unknown)
        assertEquals(d.legend, back.legend)
        assertEquals(d.champion, back.champion)
        assertEquals(d.main, back.main)
        assertEquals(d.runes, back.runes)
        assertEquals(d.battlefields.sorted(), back.battlefields.sorted())
        assertEquals(d.side, back.side)
        assertTrue("the maybeboard isn't in codes", back.maybe.isEmpty())
        // A code made elsewhere names alternate art or signed printings: the card is still found.
        assertEquals(champion, DeckCodes.cardFor(champion.code + "a"))
        assertEquals(champion, DeckCodes.cardFor(champion.code + "s"))
        assertNull(DeckCodes.cardFor("OGN-999"))
    }

    @Test
    fun badCodesAreRejected() {
        for (bad in listOf("hello", "AAAA", readme.dropLast(30))) {
            assertTrue(bad, runCatching { DeckCodes.toDeck(bad, "x") }.isFailure || DeckCodes.toDeck(bad, "x").first == null)
        }
    }
}
