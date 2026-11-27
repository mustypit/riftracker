package io.github.mustypit.rifttracker.game

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** OpenRift's catalog turned into the app's libraries, picking each card's original normal printing. */
class CatalogImportTest {
    private val sample = javaClass.getResource("/openrift/catalog_sample.json")!!.readText()

    @After fun reset() { Cards.resetForTests(); Legends.resetForTests(); Battlefields.resetForTests() }

    @Test
    fun showsTheNewestStandardPrintingAndKeepsEveryPrinting() {
        val r = CatalogImport.parse(sample)
        Cards.install(r.cardsJson)
        val unit = Cards.bySlug("chemtech-enforcer")!!
        // The newest standard printing (a reprint in a later set), not the overnumbered foil, the
        // alternate art or the promo with a marker and a distribution channel.
        assertEquals("NXT-024", unit.code)
        assertEquals("Nexus", unit.set)
        assertEquals("Newer", unit.artist)
        assertEquals("img-unit-new", unit.imageId)
        assertEquals("chemtech-enforcer.webp", unit.image)
        // Every other printing's code still finds the card (variant suffix dropped).
        assertEquals(listOf("OGN-024", "OGN-324", "PRO-007"), unit.codes)
        assertEquals(unit, Cards.byCode("PRO-007"))
        assertEquals(unit, Cards.byCode("OGN-024"))
        assertEquals(2, unit.energy); assertEquals(listOf("Assault"), unit.keywords); assertEquals("[Assault 2]", unit.text)
        // The carousel: English printings with a picture, one per picture, the shown one first.
        assertEquals(listOf("NXT-024", "OGN-024", "OGN-024a", "OGN-324*", "PRO-007"), unit.printings.map { it.code })
        assertEquals(listOf("normal", "normal", "altart", "overnumbered", "promo"), unit.printings.map { it.kind })
        assertEquals("img-unit-alt", unit.printing("OGN-024a")!!.imageId)
        // Each printing keeps OpenRift's printing id (prices and store links are keyed by it).
        assertEquals("p7", unit.printings.first().id)
        assertEquals("p8", unit.printing("OGN-024a")!!.id)
    }

    @Test
    fun deckCodesKeepTheChosenPrinting() {
        val r = CatalogImport.parse(sample)
        Cards.install(r.cardsJson); Legends.install(r.legendsJson)
        val deck = Deck(name = "x", legend = "loose-cannon", main = mapOf("chemtech-enforcer" to 3), arts = mapOf("chemtech-enforcer" to "OGN-024a"))
        val code = DeckCodes.fromDeck(deck)
        val back = DeckCodes.toDeck(code, "x").first!!
        assertEquals(mapOf("chemtech-enforcer" to 3), back.main)
        assertEquals(mapOf("chemtech-enforcer" to "OGN-024a"), back.arts)
        // Without a choice, a printing the format can't carry (a set it doesn't know) falls back to one it can.
        val plain = DeckCodes.toDeck(DeckCodes.fromDeck(deck.copy(arts = emptyMap())), "x").first!!
        assertEquals(mapOf("chemtech-enforcer" to 3), plain.main)
        assertEquals(emptyMap<String, String>(), plain.arts)
        assertEquals(null, DeckCodeFormat.canEncode("NXT-024").takeIf { it })
    }

    @Test
    fun bansNamesAndCounts() {
        val r = CatalogImport.parse(sample)
        Cards.install(r.cardsJson)
        val axe = Cards.bySlug("draven-s-axe")!!
        assertTrue(axe.banned)
        assertEquals("Draven's Axe", axe.name) // straight apostrophe
        assertFalse(Cards.bySlug("chemtech-enforcer")!!.banned)
        assertEquals(5, r.cardCount)
        assertEquals(2, r.legendCount)
        assertEquals(1, r.battlefieldCount)
    }

    @Test
    fun legendsAreNamedForTheirChampion() {
        val r = CatalogImport.parse(sample)
        Legends.install(r.legendsJson)
        val jinx = Legends.byName("Jinx")!!
        assertEquals("Loose Cannon", jinx.title)
        assertEquals("Kudos", jinx.artist) // the normal printing's artist, not the alt art's
        assertEquals(listOf(Domain.FURY, Domain.CHAOS), jinx.domains) // only real domains
        // A legend the app doesn't know yet is added, in its set.
        val nova = Legends.byName("Nova")
        assertNotNull(nova)
        assertEquals(mapOf("jinx" to "img-jinx", "nova" to "img-vi"), r.legendArt)
    }

    @Test
    fun battlefields() {
        val r = CatalogImport.parse(sample)
        Battlefields.install(r.battlefieldsJson)
        assertEquals(listOf("Cosmic Vista"), Battlefields.all.map { it.name })
        assertEquals(mapOf("cosmic_vista" to "img-vista"), r.battlefieldArt)
    }

    @Test
    fun pricesAreCardmarketCentsByPrinting() {
        val prices = Prices.parse("""{"prices":{"p7":{"tcgplayer":452,"cardmarket":380},"p8":{"tcgplayer":99}},"currencies":{}}""")
        assertEquals(mapOf("p7" to CardPrice(380)), prices)
        Cards.install(CatalogImport.parse(sample).cardsJson)
        Prices.byPrinting = prices
        val unit = Cards.bySlug("chemtech-enforcer")!!
        assertEquals(CardPrice(380), Prices.of(unit))
        assertEquals(null, Prices.of(unit, unit.printing("OGN-024a"))) // no Cardmarket price for that printing
        assertEquals("€3.80", Prices.format(Prices.of(unit)!!.eur))
        Prices.byPrinting = emptyMap()
    }

    @Test
    fun theBundledFileShapeStillLoads() {
        // The app reads its bundled cards.json with the same Card class, with or without imageId.
        Cards.install("""[{"slug":"a","name":"A","type":"unit","image":"a.webp"}]""")
        assertEquals(null, Cards.bySlug("a")!!.imageId)
    }
}
