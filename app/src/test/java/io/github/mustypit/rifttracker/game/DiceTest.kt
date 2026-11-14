package io.github.mustypit.rifttracker.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random
import kotlin.random.asKotlinRandom

/** The d20 for who goes first: a proper die, fair results, and throws that land on the drawn number. */
class DiceTest {
    @Test
    fun aProperD20() {
        assertEquals(12, D20.vertices.size)
        assertEquals(20, D20.faces.size)
        assertEquals((1..20).toSet(), D20.numbers.toSet())
        for (f in D20.faces.indices) {
            val opposite = D20.faces.indices.first { D20.normals[it] dot D20.normals[f] < -0.999 }
            assertEquals("opposite faces add up to 21", 21, D20.numbers[f] + D20.numbers[opposite])
            // Equilateral faces.
            val (a, b, c) = D20.faces[f].map { D20.vertices[it] }
            assertEquals((a - b).length, (b - c).length, 1e-9); assertEquals((a - b).length, (c - a).length, 1e-9)
        }
        // A symmetry relabels faces without moving the shape.
        val q = D20.symmetry(D20.faceOf(20), D20.faceOf(1))
        assertTrue((q.rotate(D20.normals[D20.faceOf(20)]) - D20.normals[D20.faceOf(1)]).length < 1e-9)
    }

    @Test
    fun resultsAreFair() {
        for (random in listOf(Random(1), java.security.SecureRandom().asKotlinRandom())) {
            val n = 100_000
            val faces = IntArray(21)
            var meFirst = 0
            repeat(n) {
                val r = Dice.roll(random)
                assertTrue("never a tie at the end", r.me != r.opp)
                assertTrue("only ties before it", r.throws.dropLast(1).all { it.first == it.second })
                r.throws.forEach { (a, b) -> faces[a]++; faces[b]++ }
                if (r.winner == Side.ME) meFirst++
            }
            // Every number about 1/20 of the time (chi-square, 19 degrees of freedom; p = 0.001 at 43.8).
            val total = faces.sum().toDouble()
            val chi = (1..20).sumOf { f -> (faces[f] - total / 20).let { it * it } / (total / 20) }
            assertTrue("numbers uniform, chi² = $chi", chi < 43.8)
            assertEquals("either player first half the time", 0.5, meFirst.toDouble() / n, 0.01)
        }
    }

    @Test
    fun throwsLandOnTheResult() {
        val radius = 40f
        repeat(200) { seed ->
            val random = Random(seed)
            val w = 300f + random.nextFloat() * 150f
            val h = 300f + random.nextFloat() * 120f
            val result = 1 + seed % 20
            val track = D20.simulate(Random(seed * 7L), w, h, radius, result)
            assertEquals("seed $seed", result, track.result)
            val end = track.frames.last()
            val top = end.q.rotate(D20.normals[D20.faceOf(result)])
            assertTrue("lies flat with the result straight up (seed $seed, ${top.z})", top.z > 0.999)
            assertEquals("on the table", 0f, end.z, 0.5f)
            assertTrue("comes to rest in time (${track.durationMs} ms)", track.durationMs in 700f..5100f)
            val inside = track.frames.dropWhile { it.y > h - radius }
            assertTrue(inside.isNotEmpty())
            assertTrue("seed $seed stays within the half", inside.all { it.x in radius - 0.5f..w - radius + 0.5f && it.y in radius - 0.5f..h - radius + 0.5f })
        }
    }
}
