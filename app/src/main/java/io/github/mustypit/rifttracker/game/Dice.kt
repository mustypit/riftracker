package io.github.mustypit.rifttracker.game

import kotlin.random.Random

/**
 * Dice for who goes first: each player throws a d20 ([D20]), higher goes first, ties are thrown again.
 *
 * The results are drawn here, uniformly, from the random source (in the app, SecureRandom); the
 * throw on screen only shows them ([D20.simulate] lands on the given number), so the animation can
 * never bias the outcome.
 */
object Dice {
    const val SIDES = 20

    /** All throws of one roll: tied throws, then the deciding one; [seed] drives how the dice fly. */
    data class Roll(val throws: List<Pair<Int, Int>>, val seed: Long) {
        val me: Int get() = throws.last().first
        val opp: Int get() = throws.last().second
        val winner: Side get() = if (me > opp) Side.ME else Side.OPP
        fun of(side: Side) = if (side == Side.ME) me else opp
    }

    fun roll(random: Random): Roll {
        val throws = mutableListOf<Pair<Int, Int>>()
        do {
            val t = random.nextInt(1, SIDES + 1) to random.nextInt(1, SIDES + 1)
            throws += t
        } while (t.first == t.second && throws.size < 30)
        // 30 ties in a row: about 1 in 10^39. Keep it finite anyway.
        if (throws.last().first == throws.last().second) throws[throws.lastIndex] = throws.last().first to (throws.last().first % SIDES) + 1
        return Roll(throws, random.nextLong())
    }
}
