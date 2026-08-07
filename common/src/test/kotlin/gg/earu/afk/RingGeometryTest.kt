package gg.earu.afk

import gg.earu.afk.client.render.RingGeometry
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RingGeometryTest {

    private fun radius(v: RingGeometry.Vert) = hypot(v.x.toDouble(), v.z.toDouble())

    @Test
    fun `cylinder emits one quad per side`() {
        val verts = RingGeometry.cylinder(sides = 35, radius = 2f, height = 0.5f, yOffset = 0f)
        assertEquals(35 * 4, verts.size)
    }

    @Test
    fun `cylinder vertices sit on the radius`() {
        val verts = RingGeometry.cylinder(sides = 16, radius = 3f, height = 1f, yOffset = 0f)
        for (v in verts) {
            assertTrue(abs(radius(v) - 3.0) < 1e-4, "vertex off the circle: $v")
        }
    }

    @Test
    fun `cylinder spans the height`() {
        val verts = RingGeometry.cylinder(sides = 8, radius = 1f, height = 2f, yOffset = -5f)
        assertEquals(-5f, verts.minOf { it.y })
        assertEquals(-3f, verts.maxOf { it.y })
    }

    @Test
    fun `cylinder closes the loop`() {
        val sides = 12
        val verts = RingGeometry.cylinder(sides, radius = 1f, height = 1f, yOffset = 0f)
        val first = verts.first()
        // Last quad's trailing edge must land back on the first vertex.
        val last = verts[verts.size - 1]
        assertTrue(abs(first.x - last.x) < 1e-5 && abs(first.z - last.z) < 1e-5)
    }

    @Test
    fun `flip reverses winding`() {
        val normal = RingGeometry.cylinder(4, 1f, 1f, 0f, flip = false)
        val flipped = RingGeometry.cylinder(4, 1f, 1f, 0f, flip = true)
        assertEquals(normal.size, flipped.size)
        // Each quad is reversed in place, not the whole list.
        assertEquals(normal.subList(0, 4).reversed(), flipped.subList(0, 4))
    }

    @Test
    fun `hollow circle is flat and spans both radii`() {
        val verts = RingGeometry.hollowCircle(sides = 20, radiusInner = 1f, radiusOuter = 2f, yOffset = 0.25f)
        assertEquals(20 * 4, verts.size)
        assertTrue(verts.all { it.y == 0.25f })

        val radii = verts.map { radius(it) }
        assertTrue(radii.any { abs(it - 1.0) < 1e-4 }, "no inner-radius vertices")
        assertTrue(radii.any { abs(it - 2.0) < 1e-4 }, "no outer-radius vertices")
        assertTrue(radii.all { it >= 1.0 - 1e-4 && it <= 2.0 + 1e-4 })
    }

    @Test
    fun `ring is two walls plus two caps`() {
        val sides = 35
        val verts = RingGeometry.ring(sides, radiusInner = 1.9f, radiusOuter = 2f, height = 0.05f, yOffset = 0f)
        assertEquals(sides * 4 * 4, verts.size)
        assertEquals(0f, verts.minOf { it.y })
        assertEquals(0.05f, verts.maxOf { it.y })
        assertTrue(verts.all { radius(it) >= 1.9f - 1e-4 && radius(it) <= 2f + 1e-4 })
    }
}
