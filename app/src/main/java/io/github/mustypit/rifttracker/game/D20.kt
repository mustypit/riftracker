package io.github.mustypit.rifttracker.game

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** A 3-vector (x right, y down the screen, z up towards the viewer). */
data class V3(val x: Double, val y: Double, val z: Double) {
    operator fun plus(o: V3) = V3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: V3) = V3(x - o.x, y - o.y, z - o.z)
    operator fun times(k: Double) = V3(x * k, y * k, z * k)
    infix fun dot(o: V3) = x * o.x + y * o.y + z * o.z
    infix fun cross(o: V3) = V3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    val length: Double get() = sqrt(this dot this)
    fun unit(): V3 = this * (1.0 / length)
}

/** A rotation as a unit quaternion. */
data class Quat(val w: Double, val x: Double, val y: Double, val z: Double) {
    operator fun times(q: Quat) = Quat(
        w * q.w - x * q.x - y * q.y - z * q.z,
        w * q.x + x * q.w + y * q.z - z * q.y,
        w * q.y - x * q.z + y * q.w + z * q.x,
        w * q.z + x * q.y - y * q.x + z * q.w,
    )
    fun conj() = Quat(w, -x, -y, -z)
    fun rotate(v: V3): V3 { val r = this * Quat(0.0, v.x, v.y, v.z) * conj(); return V3(r.x, r.y, r.z) }
    fun normalized(): Quat { val n = sqrt(w * w + x * x + y * y + z * z); return Quat(w / n, x / n, y / n, z / n) }
    fun dot(q: Quat) = w * q.w + x * q.x + y * q.y + z * q.z

    companion object {
        val I = Quat(1.0, 0.0, 0.0, 0.0)
        fun axisAngle(axis: V3, angle: Double): Quat { val a = axis.unit(); val s = sin(angle / 2); return Quat(cos(angle / 2), a.x * s, a.y * s, a.z * s) }
        /** The shortest rotation taking unit vector [from] to unit vector [to]. */
        fun between(from: V3, to: V3): Quat {
            val d = (from dot to).coerceIn(-1.0, 1.0)
            if (d > 0.999999) return I
            if (d < -0.999999) return axisAngle(if (abs(from.x) < 0.9) V3(1.0, 0.0, 0.0) cross from else V3(0.0, 1.0, 0.0) cross from, Math.PI)
            return axisAngle(from cross to, acos(d))
        }
        fun slerp(a: Quat, b0: Quat, t: Double): Quat {
            var b = b0
            var d = a.dot(b)
            if (d < 0) { b = Quat(-b.w, -b.x, -b.y, -b.z); d = -d }
            if (d > 0.9995) return Quat(a.w + (b.w - a.w) * t, a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t).normalized()
            val th = acos(d); val s = sin(th)
            val ka = sin((1 - t) * th) / s; val kb = sin(t * th) / s
            return Quat(a.w * ka + b.w * kb, a.x * ka + b.x * kb, a.y * ka + b.y * kb, a.z * ka + b.z * kb)
        }
        fun random(r: Random): Quat {
            // Uniform random rotation (Shoemake).
            val u1 = r.nextDouble(); val u2 = r.nextDouble() * 2 * Math.PI; val u3 = r.nextDouble() * 2 * Math.PI
            return Quat(sqrt(1 - u1) * sin(u2), sqrt(1 - u1) * cos(u2), sqrt(u1) * sin(u3), sqrt(u1) * cos(u3))
        }
    }
}

/**
 * A d20: a regular icosahedron (circumradius 1) with numbers on its faces, opposite faces adding
 * up to 21, plus a throw simulation for who goes first.
 */
object D20 {
    private val phi = (1 + sqrt(5.0)) / 2
    val vertices: List<V3> = listOf(
        V3(-1.0, phi, 0.0), V3(1.0, phi, 0.0), V3(-1.0, -phi, 0.0), V3(1.0, -phi, 0.0),
        V3(0.0, -1.0, phi), V3(0.0, 1.0, phi), V3(0.0, -1.0, -phi), V3(0.0, 1.0, -phi),
        V3(phi, 0.0, -1.0), V3(phi, 0.0, 1.0), V3(-phi, 0.0, -1.0), V3(-phi, 0.0, 1.0),
    ).map { it.unit() }

    /** Faces as vertex indices, wound so the normal points outwards. */
    val faces: List<IntArray> = listOf(
        intArrayOf(0, 11, 5), intArrayOf(0, 5, 1), intArrayOf(0, 1, 7), intArrayOf(0, 7, 10), intArrayOf(0, 10, 11),
        intArrayOf(1, 5, 9), intArrayOf(5, 11, 4), intArrayOf(11, 10, 2), intArrayOf(10, 7, 6), intArrayOf(7, 1, 8),
        intArrayOf(3, 9, 4), intArrayOf(3, 4, 2), intArrayOf(3, 2, 6), intArrayOf(3, 6, 8), intArrayOf(3, 8, 9),
        intArrayOf(4, 9, 5), intArrayOf(2, 4, 11), intArrayOf(6, 2, 10), intArrayOf(8, 6, 7), intArrayOf(9, 8, 1),
    )
    fun center(f: Int): V3 = faces[f].map { vertices[it] }.reduce(V3::plus) * (1.0 / 3)
    val normals: List<V3> = faces.indices.map { center(it).unit() }
    /** Distance from the centre to a face (the die rests at this height). */
    val inradius: Double = center(0).length

    /** The number on each face: opposite faces add up to 21. */
    val numbers: IntArray = IntArray(20).also { n ->
        var next = 1
        for (f in faces.indices) {
            if (n[f] != 0) continue
            val opposite = faces.indices.first { normals[it] dot normals[f] < -0.999 }
            n[f] = next; n[opposite] = 21 - next; next++
        }
    }
    fun faceOf(number: Int): Int = numbers.indexOfFirst { it == number }

    /** The face whose outward normal points most nearly along [dir] under [q]. */
    fun faceTowards(q: Quat, dir: V3): Int = faces.indices.maxBy { q.rotate(normals[it]) dot dir }

    /**
     * A rotation of the die onto itself (one of its 60 symmetries) taking face [from] to face [to]:
     * it relabels the faces without changing the shape.
     */
    fun symmetry(from: Int, to: Int): Quat {
        val a = faces[from].map { vertices[it] }
        val b = faces[to].map { vertices[it] }
        for (k in 0 until 3) {
            // Align normals, then the first vertex with the k-th of the target.
            val q1 = Quat.between(normals[from], normals[to])
            val c = center(to)
            val v = q1.rotate(a[0]) - c
            val target = b[k] - c
            val angle = Math.atan2((v cross target) dot normals[to], v dot target)
            val q = (Quat.axisAngle(normals[to], angle) * q1).normalized()
            if (vertices.all { p -> vertices.any { (q.rotate(p) - it).length < 1e-6 } }) return q
        }
        error("no symmetry from $from to $to")
    }

    /** One moment of a throw: centre (dp, in the half's coordinates), height above the table (dp), orientation. */
    data class Frame(val x: Float, val y: Float, val z: Float, val q: Quat)

    class Track(val frames: List<Frame>, val stepMs: Float, val radius: Float) {
        val durationMs: Float get() = (frames.size - 1) * stepMs
        fun at(ms: Float): Frame = frames[(ms / stepMs).toInt().coerceIn(0, frames.lastIndex)]
        /** The number facing up (towards the viewer) at the end. */
        val result: Int get() = numbers[faceTowards(frames.last().q, V3(0.0, 0.0, 1.0))]
    }

    private val up = V3(0.0, 0.0, 1.0)

    /**
     * A d20 of circumradius [radius] thrown in from the bottom edge of a [width] × [height] area, ending
     * with [result] facing up. The throw is simulated freely (its motion doesn't depend on which way
     * the die is turned), then relabelled by a symmetry of the die so [result] is the face that landed
     * on top: the number is decided before the throw, never by it.
     */
    fun simulate(random: Random, width: Float, height: Float, radius: Float, result: Int): Track {
        val dt = 1.0 / 120
        val r = radius.toDouble()
        val rest = r * inradius
        var x = width * (0.3 + 0.4 * random.nextDouble())
        var y = height + r
        var z = rest + 50
        val speed = 800 + 600 * random.nextDouble()
        val angle = Math.toRadians(-90.0 + (random.nextDouble() - 0.5) * 70.0)
        var vx = speed * cos(angle); var vy = speed * sin(angle)
        var vz = 240 + 160 * random.nextDouble()
        var q = Quat.random(random)
        var w = V3(random.nextDouble() - 0.5, random.nextDouble() - 0.5, random.nextDouble() - 0.5).unit() * (14 + 10 * random.nextDouble())
        var inside = false
        val frames = mutableListOf<Frame>()
        var t = 0.0
        var settleFrom: Quat? = null
        var settleTo: Quat? = null
        var settleT = 0.0
        val settleDur = 0.35
        while (true) {
            frames += Frame(x.toFloat(), y.toFloat(), (z - rest).toFloat(), q)
            if (settleTo != null && settleT >= settleDur) break
            if (t > 5.0) break
            x += vx * dt; y += vy * dt; z += vz * dt; vz -= 1500 * dt
            val onTable = z <= rest
            if (onTable) {
                z = rest
                if (vz < -70) {
                    vz = -vz * 0.38
                    vx *= 0.82; vy *= 0.82
                    // A knock on landing: the spin moves towards rolling along the travel, plus a jolt.
                    val roll = V3(-vy / r, vx / r, w.z * 0.6)
                    w = w * 0.45 + roll * 0.55 + V3(random.nextDouble() - 0.5, random.nextDouble() - 0.5, random.nextDouble() - 0.5) * 4.0
                } else vz = 0.0
            }
            if (y < height - r) inside = true
            if (x < r) { x = r; vx = abs(vx) * 0.7; w = V3(w.x, -w.y, -w.z) * 0.85 }
            if (x > width - r) { x = width - r; vx = -abs(vx) * 0.7; w = V3(w.x, -w.y, -w.z) * 0.85 }
            if (y < r) { y = r; vy = abs(vy) * 0.7; w = V3(-w.x, w.y, -w.z) * 0.85 }
            if (inside && y > height - r) { y = height - r; vy = -abs(vy) * 0.7; w = V3(-w.x, w.y, -w.z) * 0.85 }
            val v = hypot(vx, vy)
            if (onTable && vz == 0.0) {
                // Rolling: friction slows it; the spin follows the rolling motion.
                val k = exp(-2.4 * dt)
                val slow = (v - 160 * dt).coerceAtLeast(0.0) / v.coerceAtLeast(1e-6)
                vx *= k * slow; vy *= k * slow
                val roll = V3(-vy / r, vx / r, w.z * exp(-3.0 * dt))
                w = w + (roll - w) * (1 - exp(-10 * dt))
            }
            if (settleTo == null && onTable && vz == 0.0 && hypot(vx, vy) < 45) {
                // Falls onto the face nearest the table.
                settleFrom = q
                val top = faceTowards(q, up)
                settleTo = (Quat.between(q.rotate(normals[top]), up) * q).normalized()
            }
            if (settleTo != null) {
                settleT += dt
                val s = (settleT / settleDur).coerceIn(0.0, 1.0)
                val e = 1 - (1 - s) * (1 - s) * (1 - s)
                q = Quat.slerp(settleFrom!!, settleTo, e)
                vx *= exp(-6 * dt); vy *= exp(-6 * dt)
            } else {
                val wl = w.length
                if (wl > 1e-9) q = (Quat.axisAngle(w, wl * dt) * q).normalized()
            }
            t += dt
        }
        // Relabel: the face that landed on top becomes [result].
        val landed = faceTowards(frames.last().q, up)
        val c = symmetry(faceOf(result), landed)
        return Track(frames.map { it.copy(q = (it.q * c).normalized()) }, (dt * 1000).toFloat(), radius)
    }
}
