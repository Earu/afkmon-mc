package gg.earu.afk.client.render

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Port of the mesh generators in afkrings.lua. Pure math, no Minecraft types, so it is unit
 * testable and immune to render API churn.
 *
 * The Lua built Z-up triangle lists; these emit Y-up quads (4 vertices each, matching
 * VertexFormat.Mode.QUADS) since every face in the original was already a quad split in two.
 * Radii and heights are whatever unit the caller wants, the renderer scales once at draw time.
 */
object RingGeometry {

    data class Vert(val x: Float, val y: Float, val z: Float)

    /** Open tube wall. [flip] reverses winding so the surface faces inward. */
    fun cylinder(sides: Int, radius: Float, height: Float, yOffset: Float, flip: Boolean = false): List<Vert> {
        val out = ArrayList<Vert>(sides * 4)
        val step = 2.0 * PI / sides
        for (i in 0 until sides) {
            val a0 = i * step
            val a1 = (i + 1) * step
            val x0 = (cos(a0) * radius).toFloat()
            val z0 = (sin(a0) * radius).toFloat()
            val x1 = (cos(a1) * radius).toFloat()
            val z1 = (sin(a1) * radius).toFloat()
            val bottom = yOffset
            val top = yOffset + height
            val quad = listOf(
                Vert(x0, bottom, z0),
                Vert(x0, top, z0),
                Vert(x1, top, z1),
                Vert(x1, bottom, z1),
            )
            out += if (flip) quad.reversed() else quad
        }
        return out
    }

    /** Flat annulus (a disc with the middle cut out). */
    fun hollowCircle(
        sides: Int,
        radiusInner: Float,
        radiusOuter: Float,
        yOffset: Float,
        flip: Boolean = false,
    ): List<Vert> {
        val out = ArrayList<Vert>(sides * 4)
        val step = 2.0 * PI / sides
        for (i in 0 until sides) {
            val a0 = i * step
            val a1 = (i + 1) * step
            val quad = listOf(
                Vert((cos(a0) * radiusOuter).toFloat(), yOffset, (sin(a0) * radiusOuter).toFloat()),
                Vert((cos(a0) * radiusInner).toFloat(), yOffset, (sin(a0) * radiusInner).toFloat()),
                Vert((cos(a1) * radiusInner).toFloat(), yOffset, (sin(a1) * radiusInner).toFloat()),
                Vert((cos(a1) * radiusOuter).toFloat(), yOffset, (sin(a1) * radiusOuter).toFloat()),
            )
            out += if (flip) quad.reversed() else quad
        }
        return out
    }

    /** Closed ring: inner and outer walls capped top and bottom. */
    fun ring(
        sides: Int,
        radiusInner: Float,
        radiusOuter: Float,
        height: Float,
        yOffset: Float,
    ): List<Vert> = buildList {
        addAll(cylinder(sides, radiusInner, height, yOffset, flip = true))
        addAll(cylinder(sides, radiusOuter, height, yOffset))
        addAll(hollowCircle(sides, radiusInner, radiusOuter, yOffset, flip = true))
        addAll(hollowCircle(sides, radiusInner, radiusOuter, yOffset + height))
    }
}
