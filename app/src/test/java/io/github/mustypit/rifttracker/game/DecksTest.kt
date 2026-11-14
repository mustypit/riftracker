package io.github.mustypit.rifttracker.game

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/** Deck rules against the shipped card library, with a Kai'Sa (Fury · Mind) deck. */
class DecksTest {
    @Before fun load() = Cards.install(File("src/main/assets/cards/cards.json").readText())
    @After fun reset() = Cards.resetForTests()

    private fun card(name: String) = Cards.all.first { it.name == name }
    private val legend get() = card("Daughter of the Void")
    private val champion get() = card("Kai'Sa, Survivor")

    /** A legal deck: champion ×3, own signature ×3, filled with Fury/Mind cards; 7 · 5 runes; 3 fields. */
    private fun legalDeck(): Deck {
        var d = DeckRules.newDeck(legend, champion)
        repeat(2) { d = DeckRules.add(d, champion) }
        val sig = Cards.all.first { DeckRules.isSignature(it) && "Kai'Sa" in it.tags }
        repeat(3) { d = DeckRules.add(d, sig) }
        for (c in Cards.all.filter { DeckRules.allowed(d, it) && !DeckRules.isSignature(it) && !it.isChampion && !it.banned }) {
            while (d.mainSize < 40 && d.count(c.slug) < 3) d = DeckRules.add(d, c)
            if (d.mainSize == 40) break
        }
        return DeckRules.setRunes(d, 7).copy(battlefields = listOf("Cosmic Vista", "Targon's Peak", "Black Market"))
    }

    @Test
    fun newDeckAndChampions() {
        assertEquals(listOf("Kai'Sa, Evolutionary", "Kai'Sa, Rebel", "Kai'Sa, Survivor").sorted(), DeckRules.champions(legend).map { it.name }.sorted())
        val d = DeckRules.newDeck(legend, champion)
        assertEquals(1, d.count(champion.slug))
        assertEquals(listOf(Domain.FURY, Domain.MIND), DeckRules.domains(d))
        assertEquals(6, d.runesOf(Domain.FURY)); assertEquals(6, d.runesOf(Domain.MIND))
        assertEquals(listOf("1/40", "Fields 0/3"), DeckRules.problems(d))
        assertEquals(58, DeckRules.legends.size)
    }

    @Test
    fun whatMayGoIn() {
        val d = DeckRules.newDeck(legend, champion)
        assertTrue(DeckRules.allowed(d, Cards.all.first { it.type == "unit" && it.domainList == listOf(Domain.FURY) }))
        assertFalse("off-domain", DeckRules.allowed(d, Cards.all.first { it.type == "unit" && it.domainList == listOf(Domain.CALM) }))
        assertFalse("other champion's signature", DeckRules.allowed(d, Cards.all.first { DeckRules.isSignature(it) && "Kai'Sa" !in it.tags && it.domainList.all { x -> x in listOf(Domain.FURY, Domain.MIND) } }))
        assertTrue("own signature", DeckRules.allowed(d, Cards.all.first { DeckRules.isSignature(it) && "Kai'Sa" in it.tags }))
        assertFalse("legends, battlefields and runes aren't main cards", Cards.all.filter { it.type in setOf("legend", "battlefield", "rune") }.any { DeckRules.allowed(d, it) })
    }

    @Test
    fun copiesAndRemoving() {
        var d = DeckRules.newDeck(legend, champion)
        val c = Cards.all.first { DeckRules.allowed(d, it) && !it.isChampion }
        repeat(5) { d = DeckRules.add(d, c) }
        assertEquals("three at most", 3, d.count(c.slug))
        repeat(4) { d = DeckRules.remove(d, c) }
        assertEquals(0, d.count(c.slug)); assertFalse(d.main.containsKey(c.slug))
    }

    @Test
    fun legalDeckHasNoProblems() {
        val d = legalDeck()
        assertEquals(40, d.mainSize)
        assertEquals(emptyList<String>(), DeckRules.problems(d))
        // and each rule is caught
        assertEquals(listOf("No champion"), DeckRules.problems(d.copy(champion = null)))
        val off = Cards.all.first { it.type == "spell" && it.domainList == listOf(Domain.ORDER) }
        assertTrue("Off-domain" in DeckRules.problems(d.copy(main = d.main + (off.slug to 1))))
        val first = d.main.keys.first { it != d.champion }
        assertTrue("Too many copies" in DeckRules.problems(d.copy(main = d.main + (first to 4))))
        val sig = Cards.all.filter { DeckRules.isSignature(it) && "Kai'Sa" in it.tags }
        if (sig.size >= 2) assertTrue("Signatures" in DeckRules.problems(d.copy(main = d.main + (sig[1].slug to 1))))
        assertEquals(listOf("Fields 2/3"), DeckRules.problems(d.copy(battlefields = d.battlefields.take(2))))
        assertEquals(listOf("Fields 2/3"), DeckRules.problems(d.copy(battlefields = listOf("Cosmic Vista", "Cosmic Vista", "Black Market"))))
        assertEquals(listOf("Runes 13/12"), DeckRules.problems(d.copy(runes = mapOf("FURY" to 8, "MIND" to 5))))
    }

    @Test
    fun bannedCardsMakeADeckIllegal() {
        val d = legalDeck()
        assertTrue("the shipped library has a banlist", Cards.all.any { it.banned })
        assertTrue(DeckRules.bannedCards(d).isEmpty())
        // A banned main-deck card, as the first problem.
        val banned = Cards.all.first { it.banned && DeckRules.allowed(d, it) }
        val withBanned = d.copy(main = d.main + (banned.slug to 1))
        assertEquals(listOf(banned), DeckRules.bannedCards(withBanned))
        assertEquals("Banned", DeckRules.problems(withBanned).first())
        assertTrue(!DeckRules.isLegal(withBanned))
        // A banned battlefield counts too (stored by name, any case).
        val field = Cards.all.first { it.banned && it.type == "battlefield" }
        val withField = d.copy(battlefields = d.battlefields.take(2) + field.name.uppercase())
        assertEquals(listOf("Banned"), DeckRules.problems(withField))
        assertEquals(listOf(field), DeckRules.bannedCards(withField))
    }

    @Test
    fun sideboardAndMaybeboard() {
        val d = legalDeck()
        val inMain = Cards.bySlug(d.main.entries.first { (slug, n) -> n == 3 && slug != d.champion }.key)!!
        // Copies count across main and side: a card with 3 in main can't go on the sideboard.
        assertFalse(DeckRules.canAdd(d, Board.SIDE, inMain))
        assertEquals(d, DeckRules.add(d, Board.SIDE, inMain))
        // Moving between main and side keeps the total; the main deck is then 39.
        val moved = DeckRules.move(d, inMain, Board.MAIN, Board.SIDE)
        assertEquals(2, moved.count(inMain.slug)); assertEquals(1, moved.count(Board.SIDE, inMain.slug))
        assertEquals(listOf("39/40"), DeckRules.problems(moved))
        assertEquals(3, DeckRules.move(d, inMain, Board.MAIN, Board.SIDE, all = true).count(Board.SIDE, inMain.slug))
        // The maybeboard has no limits and never makes a deck illegal...
        val maybe = (1..5).fold(d) { acc, _ -> DeckRules.add(acc, Board.MAYBE, inMain) }
        assertEquals(5, maybe.count(Board.MAYBE, inMain.slug))
        assertEquals(emptyList<String>(), DeckRules.problems(maybe))
        // ...but moving out of it respects the copy limit.
        assertEquals(maybe, DeckRules.move(maybe, inMain, Board.MAYBE, Board.SIDE))
        // Up to 10 in the sideboard; more is labelled.
        val spare = Cards.all.filter { DeckRules.allowed(d, it) && !it.banned && d.copies(it.slug) == 0 && !DeckRules.isSignature(it) && !it.isChampion }
        var side = d
        spare.take(4).forEach { c -> repeat(3) { side = DeckRules.add(side, Board.SIDE, c) } }
        assertEquals(12, side.sideSize)
        assertEquals(listOf("Side 12/10"), DeckRules.problems(side))
        val ten = DeckRules.remove(DeckRules.remove(side, Board.SIDE, spare[0]), Board.SIDE, spare[0])
        assertEquals(emptyList<String>(), DeckRules.problems(ten))
        // Sideboard cards must be allowed and not banned, like the main deck.
        val off = Cards.all.first { it.type == "unit" && it.domainList == listOf(Domain.CALM) }
        assertTrue("Off-domain" in DeckRules.problems(d.copy(side = mapOf(off.slug to 1))))
        Cards.all.firstOrNull { it.banned && DeckRules.allowed(d, it) }?.let { b ->
            assertEquals("Banned", DeckRules.problems(d.copy(side = mapOf(b.slug to 1))).first())
            assertEquals(emptyList<String>(), DeckRules.problems(d.copy(maybe = mapOf(b.slug to 1))))
        }
        // Text keeps the sideboard (and reads a maybeboard section).
        val text = DeckRules.toText(ten)
        assertTrue("Sideboard:" in text)
        val (back, unknown) = DeckRules.fromText(text + "\n\nMaybeboard:\n2 ${off.name}", "x")
        assertTrue(unknown.isEmpty())
        assertEquals(ten.side, back!!.side)
        assertEquals(mapOf(off.slug to 2), back.maybe)
    }

    @Test
    fun sideboardingBetweenGames() {
        val d = legalDeck()
        val spare = Cards.all.first { DeckRules.allowed(d, it) && !it.banned && d.copies(it.slug) == 0 && !DeckRules.isSignature(it) && !it.isChampion }
        val deck = d.copy(side = mapOf(spare.slug to 2))
        val out = d.main.keys.first { it != d.champion }
        var s = Swaps()
        s = s.bringIn(deck, spare.slug).bringIn(deck, spare.slug).bringIn(deck, spare.slug)
        assertEquals("only what the sideboard holds", 2, s.inCount)
        assertFalse(s.balanced)
        s = s.takeOut(deck, out).takeOut(deck, out)
        assertTrue(s.balanced)
        val played = s.applyTo(deck)
        assertEquals(40, played.mainSize)
        assertEquals(2, played.count(spare.slug))
        assertEquals(deck.count(out) - 2, played.count(out))
        assertEquals(mapOf(out to 2), played.side)
        assertEquals(Swaps(), s.undoIn(spare.slug).undoIn(spare.slug).undoOut(out).undoOut(out))
    }

    @Test
    fun runesAlwaysTwelve() {
        var d = DeckRules.newDeck(legend, champion)
        d = DeckRules.addRune(d, Domain.FURY)
        assertEquals(7, d.runesOf(Domain.FURY)); assertEquals(5, d.runesOf(Domain.MIND))
        repeat(10) { d = DeckRules.addRune(d, Domain.FURY) }
        assertEquals(12, d.runesOf(Domain.FURY)); assertEquals(0, d.runesOf(Domain.MIND))
        d = DeckRules.addRune(d, Domain.MIND)
        assertEquals(11, d.runesOf(Domain.FURY)); assertEquals(12, d.runeCount)
        assertEquals(3, DeckRules.setRunes(d, 3).runesOf(Domain.FURY))
        assertEquals(12, DeckRules.setRunes(d, 99).runesOf(Domain.FURY))
    }

    @Test
    fun stats() {
        val d = legalDeck()
        val curve = DeckRules.energyCurve(d)
        assertEquals(DeckRules.entries(d).filter { it.first.energy != null }.sumOf { it.second }, curve.sum())
        val avg = DeckRules.averageEnergy(d)
        assertTrue(avg > 0 && avg < 8)
        val power = DeckRules.powerByDomain(d)
        assertEquals(DeckRules.entries(d).sumOf { (it.first.power ?: 0) * it.second }.toDouble(), power.values.sum(), 0.001)
        val matched = DeckRules.matchRunes(d)
        assertEquals(12, matched.runeCount)
        val fury = power[Domain.FURY] ?: 0.0; val mind = power[Domain.MIND] ?: 0.0
        assertEquals(Math.round(fury * 12 / (fury + mind)).toInt(), matched.runesOf(Domain.FURY))
    }

    @Test
    fun textRoundTrip() {
        val d = legalDeck()
        val text = DeckRules.toText(d)
        assertTrue(text.startsWith("Legend: Daughter of the Void\nChampion: Kai'Sa, Survivor"))
        val (back, unknown) = DeckRules.fromText(text, "Copy")
        assertTrue(unknown.isEmpty())
        back!!
        assertEquals(d.legend, back.legend); assertEquals(d.champion, back.champion)
        assertEquals(d.main, back.main); assertEquals(d.runes, back.runes); assertEquals(d.battlefields, back.battlefields)
        // Plain lists: card lines only, legend among them, unknown names reported.
        val (plain, missing) = DeckRules.fromText("1 Daughter of the Void\n3 Kai'Sa, Survivor\n2x Not A Card", "Plain")
        assertEquals(3, plain!!.count(champion.slug)); assertEquals(champion.slug, plain.champion)
        assertEquals(listOf("Not A Card"), missing)
        assertNull("no legend, no deck", DeckRules.fromText("3 Kai'Sa, Survivor", "x").first)
    }
}
