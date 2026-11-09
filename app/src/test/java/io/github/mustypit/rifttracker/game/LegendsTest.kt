package io.github.mustypit.rifttracker.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LegendsTest {
    @org.junit.After fun reset() = Legends.resetForTests()

    @Test
    fun importedDataUpdatesKnownLegendsAndAddsNewOnes() {
        Legends.install(
            """[
              {"name": "Vi", "title": "Piltover Enforcer", "set": "Unleashed", "domains": ["Fury", "Order"], "artist": "J. Santoro", "image": "vi.jpg"},
              {"name": "Garen", "title": "Might of Demacia", "set": "Proving Grounds", "domains": ["Body", "Order"]},
              {"name": "Newcomer", "title": "Fresh Face", "set": "Some Future Set", "domains": ["Mind", "Colorless"]}
            ]""",
        )
        val vi = Legends.byName("Vi")!!
        assertEquals(listOf(Domain.FURY, Domain.ORDER), vi.domains)
        assertEquals("Piltover Enforcer", vi.title)
        assertEquals("Unleashed", vi.set)
        assertEquals(listOf(Domain.BODY, Domain.ORDER), Legends.byName("Garen")!!.domains)
        assertEquals("Origins", Legends.byName("Garen")!!.set)
        val n = Legends.byName("Newcomer")!!
        assertEquals("Some Future Set", n.set)
        assertEquals("unknown domains are dropped", listOf(Domain.MIND), n.domains)
        assertTrue(Legends.grouped("").any { it.first == "Some Future Set" })
        assertEquals("built-in legends missing from the file stay", listOf(Domain.FURY, Domain.ORDER), Legends.byName("Mordekaiser")!!.domains)
    }

    /** The files the app ships: every Radiance legend and battlefield has art and data. */
    @Test
    fun bundledFilesCoverRadiance() {
        val dir = java.io.File("src/main/assets/legends")
        Legends.install(java.io.File(dir, "legends.json").readText())
        for (l in Legends.all.filter { it.set == "Radiance" }) {
            assertTrue("${l.name} has domains", l.domains.size == 2)
            assertTrue("${l.name} has a title", l.title.isNotBlank())
            assertTrue("${l.name} has art", java.io.File(dir, "${l.id}.jpg").exists())
        }
        val bfDir = java.io.File("src/main/assets/battlefields")
        Battlefields.install(java.io.File(bfDir, "battlefields.json").readText())
        val radiance = Battlefields.grouped("").firstOrNull { it.first == "Radiance" }?.second.orEmpty()
        assertEquals(10, radiance.size)
        assertTrue(radiance.all { java.io.File(bfDir, "${it.id}.jpg").exists() })
        Battlefields.resetForTests()
    }

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

class BattlefieldsTest {
    @org.junit.After fun reset() = Battlefields.resetForTests()

    @Test
    fun libraryLoadsFromImporterJson() {
        Battlefields.install(
            """[{"name": "Altar of Blood", "set": "Vendetta", "image": "altar_of_blood.jpg"},
                {"name": "Black Market", "set": "Spiritforged"},
                {"name": "Sunken Temple", "set": "Origins"},
                {"name": "Future Field", "set": "Next Set"}]""",
        )
        assertEquals("altar_of_blood", Battlefields.byName("altar of blood")!!.id)
        val groups = Battlefields.grouped("")
        assertEquals(listOf("Vendetta", "Spiritforged", "Origins", "Next Set"), groups.map { it.first })
        assertEquals(listOf("Black Market"), Battlefields.grouped("mark").flatMap { it.second }.map { it.name })
        assertEquals(listOf("Sunken Temple", "Altar of Blood"), Battlefields.recent(listOf("Sunken Temple", null, "Nope", "Altar of Blood", "sunken temple")).map { it.name })
    }
}
