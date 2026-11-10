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

    private fun find(q: CardQuery) = Cards.search(q)

    @Test
    fun filtersAndSearch() {
        val units = find(CardQuery(type = CardType.UNITS))
        assertTrue(units.isNotEmpty() && units.all { it.type == "unit" })
        val furyMind = find(CardQuery(domains = setOf(Domain.FURY, Domain.MIND)))
        assertTrue(furyMind.all { c -> c.domainList.any { it == Domain.FURY || it == Domain.MIND } })
        assertTrue("a two-domain legend counts for either", furyMind.any { it.name == "Boy Who Shattered Time" })
        assertTrue("search by name", find(CardQuery("kai'sa, surv")).any { it.name == "Kai'Sa, Survivor" })
        val shadow = find(CardQuery("shadow"))
        assertTrue("the search box looks at names only", shadow.isNotEmpty() && shadow.all { it.name.contains("shadow", ignoreCase = true) })
        assertTrue("so a legend isn't found by its champion's name", find(CardQuery("ekko", type = CardType.LEGENDS)).isEmpty())
        assertTrue("tags find it", find(CardQuery(tags = setOf("Ekko"), type = CardType.LEGENDS)).any { it.name == "Boy Who Shattered Time" })
        assertTrue("nothing for nonsense", find(CardQuery("zzzzqq")).isEmpty())
        val bySet = find(CardQuery(sort = CardSort.SET))
        assertEquals("Radiance first when sorted by set", "Radiance", bySet.first().set)
        val byEnergy = find(CardQuery())
        assertEquals("cheapest first", byEnergy.mapNotNull { it.energy }, byEnergy.mapNotNull { it.energy }.sorted())
        assertTrue("cards without a cost come last", byEnergy.dropWhile { it.energy != null }.all { it.energy == null })
    }

    @Test
    fun rulesTextAndTags() {
        val draw2 = find(CardQuery(rulesText = "draw 2"))
        assertTrue(draw2.isNotEmpty())
        assertTrue("all words, any order", draw2.all { Cards.plainText(it).lowercase().let { t -> "draw" in t && "2" in t } })
        assertEquals("word order doesn't matter", draw2.toSet(), find(CardQuery(rulesText = "2 DRAW")).toSet())
        assertTrue("symbols count as words", find(CardQuery(rulesText = "might")).any { ":rb_might:" in it.text })
        assertTrue("markup isn't searchable", find(CardQuery(rulesText = "rb_")).isEmpty())
        val poroFreljord = find(CardQuery(tags = setOf("Poro")))
        assertTrue(poroFreljord.isNotEmpty() && poroFreljord.all { "Poro" in it.tags })
        assertTrue(Cards.tags.any { it.first == "Freljord" })
    }

    @Test
    fun statFiltersCombine() {
        // Might between 4 and 6: two rows, both must hold.
        val between = find(CardQuery(stats = listOf(StatFilter(Stat.MIGHT, Compare.GE, 4), StatFilter(Stat.MIGHT, Compare.LE, 6))))
        assertTrue(between.isNotEmpty())
        assertTrue(between.all { it.might in 4..6 })
        assertEquals("every card with might 4, 5 or 6 is found", Cards.all.count { it.might in 4..6 }, between.size)
        // Each comparison.
        fun count(c: Compare, v: Int) = find(CardQuery(stats = listOf(StatFilter(Stat.ENERGY, c, v)))).size
        val withEnergy = Cards.all.filter { it.energy != null }
        assertEquals(withEnergy.count { it.energy == 3 }, count(Compare.EQ, 3))
        assertEquals(withEnergy.count { it.energy!! < 3 }, count(Compare.LT, 3))
        assertEquals(withEnergy.count { it.energy!! > 3 }, count(Compare.GT, 3))
        assertEquals(withEnergy.count { it.energy!! <= 3 }, count(Compare.LE, 3))
        assertEquals(withEnergy.count { it.energy!! >= 3 }, count(Compare.GE, 3))
        assertEquals("cards without energy never match, not even ≠", withEnergy.count { it.energy != 3 }, count(Compare.NE, 3))
        // A card with an energy cost but no power cost has power 0; legends have neither.
        val power0 = find(CardQuery(stats = listOf(StatFilter(Stat.POWER, Compare.EQ, 0))))
        assertTrue(power0.isNotEmpty() && power0.all { it.energy != null && (it.power ?: 0) == 0 })
        assertTrue(power0.none { it.type == "legend" })
        // Stats with other filters, and keywords: a card needs every keyword picked.
        val shieldTank = find(CardQuery(keywords = setOf("Shield", "Tank")))
        assertTrue(shieldTank.all { "Shield" in it.keywords && "Tank" in it.keywords })
        assertTrue(find(CardQuery(keywords = setOf("Shield"))).size >= shieldTank.size)
        val cheapFury = find(CardQuery(domains = setOf(Domain.FURY), type = CardType.UNITS, stats = listOf(StatFilter(Stat.ENERGY, Compare.LE, 2))))
        assertTrue(cheapFury.isNotEmpty() && cheapFury.all { it.type == "unit" && Domain.FURY in it.domainList && it.energy!! <= 2 })
        assertEquals("Might ≥ 4", StatFilter(Stat.MIGHT, Compare.GE, 4).text)
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
