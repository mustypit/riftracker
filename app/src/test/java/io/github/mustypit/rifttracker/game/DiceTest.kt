package io.github.mustypit.rifttracker.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random
import kotlin.random.asKotlinRandom

/** Dice for who goes first: fair results, and throws that always land on the drawn face. */
class DiceTest {
    @Test
    fun resultsAreFair() {
        for (random in listOf(Random(1), java.security.SecureRandom().asKotlinRandom())) {
            val n = 60_000
            val faces = IntArray(7)
            var meFirst = 0
            repeat(n) {
                val r = Dice.roll(random)
                assertTrue("never a tie at the end", r.me != r.opp)
                assertTrue("ties before it", r.throws.dropLast(1).all { it.first == it.second })
                r.throws.forEach { (a, b) -> faces[a]++; faces[b]++ }
                if (r.winner == Side.ME) meFirst++
            }
            // Each face about 1/6 of all dice thrown (chi-square, 5 degrees of freedom, p ≈ 0.001 at 20.5).
            val total = faces.sum().toDouble()
            val chi = (1..6).sumOf { f -> (faces[f] - total / 6).let { it * it } / (total / 6) }
            assertTrue("faces uniform, chi² = $chi", chi < 20.5)
            // Either player first about half the time (±1% at 60,000 rolls is over 4 sigma).
            assertEquals(0.5, meFirst.toDouble() / n, 0.01)
        }
    }

    @Test
    fun throwsLandOnTheResult() {
        val size = 64f
        repeat(300) { seed ->
            val random = Random(seed)
            val w = 300f + random.nextFloat() * 150f
            val h = 300f + random.nextFloat() * 120f
            val result = 1 + seed % 6
            val track = Dice.simulate(Random(seed * 7L), w, h, size, result)
            assertEquals("seed $seed", result, track.result)
            assertTrue("comes to rest in time (${track.durationMs} ms)", track.durationMs in 700f..4500f)
            val end = track.frames.last()
            assertEquals("lies flat", 0f, end.z, 0.01f)
            assertEquals("not mid-tumble", 0f, end.flip, 0.0001f)
            // Once in, it stays on the table.
            val inside = track.frames.dropWhile { it.y > h - size / 2 }
            assertTrue(inside.isNotEmpty())
            assertTrue("seed $seed stays within the half", inside.all { it.x in size / 2 - 0.5f..w - size / 2 + 0.5f && it.y in size / 2 - 0.5f..h - size / 2 + 0.5f })
            // Each quarter turn shows a neighbouring face (never the same or the opposite one).
            assertTrue(track.faces.zipWithNext().all { (a, b) -> b in Dice.neighbours(a) })
        }
    }
}
