package io.github.mustypit.rifttracker.game

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.random.Random

/**
 * Dice for who goes first: each player throws a d6, higher goes first, ties are thrown again.
 *
 * The results are drawn first, uniformly, from the random source (in the app, SecureRandom); the
 * physics below only animates a throw that ends on that face, so the animation can never bias the
 * outcome. The throw itself (angle, speed, spin) comes from its own random seed.
 */
object Dice {
    /** All throws of one roll: tied throws, then the deciding one. */
    data class Roll(val throws: List<Pair<Int, Int>>, val seed: Long) {
        val me: Int get() = throws.last().first
        val opp: Int get() = throws.last().second
        val winner: Side get() = if (me > opp) Side.ME else Side.OPP
        fun of(side: Side) = if (side == Side.ME) me else opp
    }

    fun roll(random: Random): Roll {
        val throws = mutableListOf<Pair<Int, Int>>()
        do {
            val t = random.nextInt(1, 7) to random.nextInt(1, 7)
            throws += t
        } while (t.first == t.second && throws.size < 50)
        // 50 ties in a row has a probability of 1 in 10^39; keep it finite anyway.
        if (throws.last().first == throws.last().second) throws[throws.lastIndex] = throws.last().first to (throws.last().first % 6) + 1
        return Roll(throws, random.nextLong())
    }

    /** Faces next to [f] on a die (opposite faces add up to 7). */
    fun neighbours(f: Int): List<Int> = (1..6).filter { it != f && it != 7 - f }

    /**
     * One moment of a throw, in the half's own coordinates (dp; y = 0 is the far edge, the thrower
     * sits at y = height). [quarter] counts quarter turns of tumbling, [flip] is progress through the
     * current one (0..1) along the direction of travel ([heading], radians), [spin] the turn about
     * the vertical (degrees), [z] the height above the table.
     */
    data class Frame(val x: Float, val y: Float, val z: Float, val spin: Float, val heading: Float, val quarter: Int, val flip: Float)

    class Track(val frames: List<Frame>, val faces: List<Int>, val stepMs: Float) {
        val durationMs: Float get() = (frames.size - 1) * stepMs
        fun at(ms: Float): Frame = frames[(ms / stepMs).toInt().coerceIn(0, frames.lastIndex)]
        /** The face up at [f] (before or after the middle of a quarter turn). */
        fun face(f: Frame): Int = faces[(if (f.flip < 0.5f) f.quarter else f.quarter + 1).coerceAtMost(faces.lastIndex)]
        val result: Int get() = face(frames.last())
    }

    /**
     * Simulates a die of [size] thrown in from the bottom edge of a [width] × [height] area, ending
     * with [result] up. Deterministic for a given [random] (pass one seeded from [Roll.seed]).
     */
    fun simulate(random: Random, width: Float, height: Float, size: Float, result: Int): Track {
        val dt = 1f / 120f
        val half = size / 2
        var x = width * (0.3f + 0.4f * random.nextFloat())
        var y = height + half
        var z = 30f
        val speed = 850f + 650f * random.nextFloat()
        val angle = Math.toRadians(-90.0 + (random.nextDouble() - 0.5) * 70.0)
        var vx = (speed * kotlin.math.cos(angle)).toFloat()
        var vy = (speed * kotlin.math.sin(angle)).toFloat()
        var vz = 260f + 160f * random.nextFloat()
        var spin = random.nextFloat() * 360f
        var omega = (300f + 500f * random.nextFloat()) * if (random.nextBoolean()) 1 else -1 // deg/s
        var quarter = 0
        var flip = 0f
        val quarterLen = size * 0.85f
        var inside = false
        var heading = atan2(vy, vx)
        val frames = mutableListOf<Frame>()
        var t = 0f
        while (true) {
            val v = hypot(vx, vy)
            if (v > 1f) heading = atan2(vy, vx)
            frames += Frame(x, y, z, spin, heading, quarter, flip)
            val resting = z == 0f && vz == 0f && v < 4f
            if ((resting && flip == 0f) || t > 4.5f) break
            // Move.
            x += vx * dt; y += vy * dt
            z += vz * dt; vz -= 1500f * dt
            if (z <= 0f) {
                z = 0f
                if (vz < -60f) { vz = -vz * 0.42f; vx *= 0.86f; vy *= 0.86f; omega += (random.nextFloat() - 0.5f) * 400f } else vz = 0f
            }
            // Walls (the near edge only once the die is in).
            if (y < height - half) inside = true
            if (x < half) { x = half; vx = abs(vx) * 0.72f; omega = -omega * 0.8f }
            if (x > width - half) { x = width - half; vx = -abs(vx) * 0.72f; omega = -omega * 0.8f }
            if (y < half) { y = half; vy = abs(vy) * 0.72f; omega = -omega * 0.8f }
            if (inside && y > height - half) { y = height - half; vy = -abs(vy) * 0.72f; omega = -omega * 0.8f }
            // Friction on the table; the die tumbles as it travels and stops on a face.
            val vNow = hypot(vx, vy)
            if (z == 0f) {
                val k = exp(-2.2f * dt)
                val slow = (vNow - 140f * dt).coerceAtLeast(0f) / vNow.coerceAtLeast(0.0001f)
                vx *= k * slow; vy *= k * slow
                omega *= exp(-3f * dt)
            }
            if (hypot(vx, vy) < 30f && z == 0f) {
                // Nearly stopped: finish the quarter turn it's in (fall onto a face).
                if (flip > 0f) { flip += 3f * dt; if (flip >= 1f) { quarter++; flip = 0f } }
            } else {
                flip += hypot(vx, vy) * dt / quarterLen
                while (flip >= 1f) { quarter++; flip -= 1f }
            }
            spin += omega * dt
            t += dt
        }
        // Faces: a chain of neighbours that ends on the result (each quarter turn shows an adjacent face).
        val end = frames.last()
        val last = end.quarter + if (end.flip >= 0.5f) 1 else 0
        val faces = MutableList(end.quarter + 2) { 0 }
        faces[last] = result
        for (i in last - 1 downTo 0) faces[i] = neighbours(faces[i + 1]).random(random)
        for (i in last + 1..faces.lastIndex) faces[i] = neighbours(faces[i - 1]).random(random)
        return Track(frames, faces, dt * 1000f)
    }
}
