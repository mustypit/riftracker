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
    fun picksTheOriginalNormalPrinting() {
        val r = CatalogImport.parse(sample)
        Cards.install(r.cardsJson)
        val unit = Cards.bySlug("chemtech-enforcer")!!
        // Not the overnumbered foil, not the promo with a marker and a distribution channel.
        assertEquals("OGN-024", unit.code)
        assertEquals("Origins", unit.set)
        assertEquals("A", unit.artist)
        assertEquals("img-unit", unit.imageId)
        assertEquals("chemtech-enforcer.webp", unit.image)
        // Every other printing's code still finds the card (variant suffix dropped).
        assertEquals(listOf("OGN-324", "PRO-007"), unit.codes)
        assertEquals(unit, Cards.byCode("PRO-007"))
        assertEquals(2, unit.energy); assertEquals(listOf("Assault"), unit.keywords); assertEquals("[Assault 2]", unit.text)
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
    fun theBundledFileShapeStillLoads() {
        // The app reads its bundled cards.json with the same Card class, with or without imageId.
        Cards.install("""[{"slug":"a","name":"A","type":"unit","image":"a.webp"}]""")
        assertEquals(null, Cards.bySlug("a")!!.imageId)
    }
}
