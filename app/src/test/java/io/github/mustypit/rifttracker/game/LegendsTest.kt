package io.github.mustypit.rifttracker.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LegendsTest {
    @Test
    fun libraryIsConsistent() {
        assertEquals("names are unique", Legends.all.size, Legends.all.map { it.name.lowercase() }.toSet().size)
        assertEquals("image ids are unique", Legends.all.size, Legends.all.map { it.id }.toSet().size)
        assertTrue(Legends.all.all { it.set in Legends.sets })
        assertTrue(Legends.all.all { it.id.matches(Regex("[a-z_]+")) })
    }

    @Test
    fun domainsComeFromTheCards() {
        assertEquals(listOf(Domain.CALM, Domain.CHAOS), Legends.byName("Riven")!!.domains)
        assertEquals(listOf(Domain.FURY, Domain.ORDER), Legends.byName("Mordekaiser")!!.domains)
        assertTrue("every listed legend has two domains or none", Legends.all.all { it.domains.size == 0 || it.domains.size == 2 })
    }

    @Test
    fun idsAndInitials() {
        assertEquals("kaisa", Legends.idOf("Kai'Sa"))
        assertEquals("lee_sin", Legends.idOf("Lee Sin"))
        assertEquals("MY", Legends.initials("Master Yi"))
        assertEquals("KA", Legends.initials("Kai'Sa"))
        assertEquals("AH", Legends.initials("Ahri"))
        assertEquals("JI", Legends.initials("Jarvan IV"))
        assertEquals("ksante", Legends.idOf("K'Sante"))
    }

    @Test
    fun lookupIgnoresCaseAndSpaces() {
        assertEquals("Rek'Sai", Legends.byName(" rek'sai ")?.name)
        assertNull(Legends.byName("Not A Legend"))
    }

    @Test
    fun pickerGroupsNewestSetFirstAndFilters() {
        val all = Legends.grouped("")
        assertEquals(Legends.sets, all.map { it.first })
        assertEquals(listOf("Ekko", "Evelynn"), all.first().second.take(2).map { it.name })
        val ri = Legends.grouped("ri")
        assertTrue(ri.flatMap { it.second }.all { it.name.lowercase().contains("ri") })
        assertTrue(ri.flatMap { it.second }.any { it.name == "Ahri" })
        assertTrue(Legends.grouped("zzz").isEmpty())
    }

    @Test
    fun recentKeepsOrderDropsDuplicatesAndUnknowns() {
        val r = Legends.recent(listOf("Jinx", "Ahri", "jinx", "Homebrew", "Zed"))
        assertEquals(listOf("Jinx", "Ahri", "Zed"), r.map { it.name })
    }
}
