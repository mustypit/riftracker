package io.github.mustypit.rifttracker.game

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/** The card library, against the cards.json the app ships. */
class CardsTest {
    private val dir = File("src/main/assets/cards")

    @Before fun load() = Cards.install(File(dir, "cards.json").readText())
    @After fun reset() = Cards.resetForTests()

    @Test
    fun shippedLibraryIsComplete() {
        assertTrue("over a thousand cards", Cards.all.size > 1000)
        assertEquals("slugs are unique", Cards.all.size, Cards.all.map { it.slug }.toSet().size)
        assertFalse("rules reminders and tokens aren't cards", Cards.all.any { it.type == "other" })
        val missing = Cards.all.filter { it.image == null || !File(dir, it.image!!).exists() }
        assertTrue("every card has its picture: missing ${missing.take(5).map { it.slug }}", missing.isEmpty())
        assertEquals("newest set first", "Radiance", Cards.sets.first())
    }

    @Test
    fun filtersAndSearch() {
        val units = Cards.browse(type = CardType.UNITS).flatMap { it.second }
        assertTrue(units.isNotEmpty() && units.all { it.type == "unit" })
        val furyMind = Cards.browse(domains = setOf(Domain.FURY, Domain.MIND)).flatMap { it.second }
        assertTrue(furyMind.all { c -> c.domainList.any { it == Domain.FURY || it == Domain.MIND } })
        assertTrue("a two-domain legend counts for either", furyMind.any { it.name == "Boy Who Shattered Time" })
        assertTrue("search by name", Cards.browse("kai'sa, surv").flatMap { it.second }.any { it.name == "Kai'Sa, Survivor" })
        assertTrue("search by champion tag", Cards.browse("ekko", type = CardType.LEGENDS).flatMap { it.second }.any { it.name == "Boy Who Shattered Time" })
        assertTrue("nothing for nonsense", Cards.browse("zzzzqq").isEmpty())
        val radiance = Cards.browse().first()
        assertEquals("Radiance", radiance.first)
        assertEquals("collector order inside a set", radiance.second.map { it.code }, radiance.second.map { it.code }.sortedBy { it })
    }

    @Test
    fun rulesTextBecomesWordsAndSymbols() {
        val parts = RulesText.parse("[Accelerate] _(You may pay :rb_energy_1: :rb_rune_fury: as an additional cost.)_ :rb_exhaust:: +2 :rb_might:, :rb_rune_rainbow: :rb_unknown:")
        assertEquals(RulesText.Words("Accelerate", keyword = true), parts[0])
        assertTrue((parts[2] as RulesText.Words).reminder)
        assertTrue(RulesText.Energy(1) in parts)
        assertTrue(RulesText.Rune(Domain.FURY) in parts)
        assertTrue(RulesText.Rune(null) in parts)
        assertTrue(RulesText.Exhaust in parts && RulesText.Might in parts)
        assertFalse("no raw codes left", parts.filterIsInstance<RulesText.Words>().any { ":rb_" in it.text })
        // Every symbol on every shipped card is one we can draw.
        val leftovers = Cards.all.filter { c -> RulesText.parse(c.text).filterIsInstance<RulesText.Words>().any { ":rb_" in it.text } }
        assertTrue(leftovers.map { it.slug }.toString(), leftovers.isEmpty())
    }
}
