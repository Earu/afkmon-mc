package gg.earu.afk.client.render

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.math.Axis
import gg.earu.afk.client.AfkClient
import gg.earu.afk.core.PlayerAfkState
import net.minecraft.client.Camera
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.world.entity.player.Player
import kotlin.math.PI

/**
 * Draws the spinning halo from afkrings.lua: two thin rings with a text band filling the gap,
 * lying flat around the player's feet and turning slowly. "Flat" means the drawn player's local
 * up, taken from [PlayerRenderPose], so a halo on a tilted contraption deck tilts with it.
 *
 * Everything GPU-facing lives here. [RingGeometry] holds the maths and never needs porting.
 */
object AfkRingsRenderer {

    /** A 32-unit-wide GMod player is 0.6 blocks in Minecraft; every constant below is GMod units. */
    private const val UNITS_PER_BLOCK = 32f / 0.6f

    private const val RING_SIDES = 35
    private const val BAND_SIDES = 40
    private const val OUTER_RING_INNER = 34.35f
    private const val OUTER_RING_OUTER = 34.65f
    private const val INNER_RING_INNER = 29.35f
    private const val INNER_RING_OUTER = 29.65f
    private const val RING_HEIGHT = 0.15f
    private const val RING_Y = -5.075f
    private const val BAND_INNER = 29.5f
    private const val BAND_OUTER = 34.5f
    private const val BAND_Y = -5f

    /** The Lua lifts the mesh by 5 + 3 units before applying the mesh's own offset. */
    private const val UP_MOVE = 8f

    /**
     * Blocks to lift the halo on a seated player (mount, boat, chair from a sitting mod). Their
     * origin sits under the seat, so a feet-height halo hides inside it; this is the hip pivot of
     * the player model, 12 pixels at the 0.9375 model scale, so the halo circles the waist.
     */
    private const val SEATED_LIFT = 0.703125

    /** The RT texture tiled five times around the ring, so the label repeats five times. */
    private const val REPEATS = 5

    /** Degrees per second, from the Lua's -RealTime() * 5. */
    private const val SPIN_DEGREES_PER_SECOND = 5f

    private val outerRing = RingGeometry.ring(
        RING_SIDES, OUTER_RING_INNER.blocks(), OUTER_RING_OUTER.blocks(), RING_HEIGHT.blocks(), RING_Y.blocks(),
    )
    private val innerRing = RingGeometry.ring(
        RING_SIDES, INNER_RING_INNER.blocks(), INNER_RING_OUTER.blocks(), RING_HEIGHT.blocks(), RING_Y.blocks(),
    )
    private val band = RingGeometry.hollowCircle(
        BAND_SIDES, BAND_INNER.blocks(), BAND_OUTER.blocks(), BAND_Y.blocks(),
    )

    private val bandRadius = ((BAND_INNER + BAND_OUTER) / 2f).blocks()
    private val bandWidth = (BAND_OUTER - BAND_INNER).blocks()
    private val bandCircumference = (2.0 * PI).toFloat() * bandRadius

    /** Keeps the text off the band surface so the two do not z-fight. */
    private const val TEXT_LIFT = 0.0015f

    /** Up-facing and down-facing copies of the text, since text render types cull. */
    private val FACES = floatArrayOf(1f, -1f)

    /** The Lua dims the ring material to 0.4 grey. */
    private val RING_COLOR = argb(255, 102, 102, 102)

    private class Label(text: String, val background: Int, val textColor: Int) {
        val chars: List<String> = text.map(Char::toString)
    }

    // Colours straight from the render targets the Lua built.
    private val AFK = Label("AFK", argb(150, 60, 60, 60), argb(255, 255, 255, 255))
    private val TIMING_OUT = Label("TIMING OUT", argb(150, 50, 0, 0), argb(255, 255, 87, 49))
    private val TABBED_OUT = Label("TABBED OUT", argb(150, 60, 60, 60), argb(255, 255, 255, 255))

    fun render(pose: PoseStack, buffers: MultiBufferSource.BufferSource, camera: Camera, partialTick: Float) {
        // Drained every frame, even when nothing draws, so captures never go stale.
        val renderedPoses = PlayerRenderPose.drain()
        val config = AfkClient.config
        if (!config.ringsEnabled || AfkClient.states.isEmpty()) return

        val mc = Minecraft.getInstance()
        val level = mc.level ?: return
        if (mc.player?.isSpectator == true) return

        // Wrapping at an hour keeps float precision usable; 3600s is a whole number of both
        // spins (50) and blink cycles (1200), so nothing jumps at the wrap.
        val worldSeconds = ((level.gameTime % 72_000L).toFloat() + partialTick) / 20f
        val cameraPos = camera.position
        val minDistanceSq = config.minDistance * config.minDistance
        val maxDistanceSq = config.maxDistance * config.maxDistance

        val ringType = if (config.seeThroughWalls) RenderType.textBackgroundSeeThrough() else RenderType.debugQuads()
        val displayMode = if (config.seeThroughWalls) Font.DisplayMode.SEE_THROUGH else Font.DisplayMode.NORMAL
        var drewAnything = false

        for (player in level.players()) {
            val state = AfkClient.states[player.uuid] ?: continue
            val label = labelFor(state, worldSeconds) ?: continue
            if (player.isSpectator || player.isDeadOrDying) continue

            val position = player.getPosition(partialTick)
            val distanceSq = position.distanceToSqr(cameraPos)
            if (distanceSq > maxDistanceSq) continue
            // Your own halo always draws, like the Lua's ply ~= me guard.
            if (distanceSq < minDistanceSq && player !== mc.player) continue

            pose.pushPose()
            val rendered = renderedPoses[player.id]
            if (rendered != null) {
                // The matrix the player model was actually submitted with this frame. Under
                // contraption physics mods it carries the deck tilt, so the halo lies on the
                // deck instead of cutting through it at world-up.
                pose.last().pose().set(rendered)
            } else {
                // Not rendered this frame (first person, culled): world position, world-up.
                pose.translate(position.x - cameraPos.x, position.y - cameraPos.y, position.z - cameraPos.z)
            }
            val lift = UP_MOVE.blocks().toDouble() + (if (player.isPassenger) SEATED_LIFT else 0.0)
            pose.translate(0.0, lift, 0.0)
            pose.mulPose(Axis.YP.rotationDegrees(-worldSeconds * SPIN_DEGREES_PER_SECOND))
            // The Lua normalises to a 32-unit-wide player, so sneaking and mounts scale the halo.
            val scale = player.bbWidth / 0.6f
            pose.scale(scale, scale, scale)

            drawQuads(buffers.getBuffer(ringType), pose, outerRing, RING_COLOR, config.seeThroughWalls)
            drawQuads(buffers.getBuffer(ringType), pose, innerRing, RING_COLOR, config.seeThroughWalls)
            drawQuads(buffers.getBuffer(ringType), pose, band, label.background, config.seeThroughWalls)
            drawLabel(pose, buffers, mc.font, label, displayMode)

            pose.popPose()
            drewAnything = true
        }

        if (drewAnything) {
            buffers.endBatch(ringType)
            buffers.endBatch()
        }
    }

    private fun labelFor(state: PlayerAfkState, worldSeconds: Float): Label? = when {
        state.timingOut -> TIMING_OUT
        // Both at once alternates, exactly like the Lua's now % 3 > 2 check.
        state.afk && state.tabbedOut -> if (worldSeconds % 3f > 2f) AFK else TABBED_OUT
        state.afk -> AFK
        state.tabbedOut -> TABBED_OUT
        else -> null
    }

    private fun drawQuads(
        buffer: VertexConsumer,
        pose: PoseStack,
        verts: List<RingGeometry.Vert>,
        color: Int,
        lit: Boolean,
    ) {
        val matrix = pose.last().pose()
        val a = (color ushr 24) and 0xFF
        val r = (color ushr 16) and 0xFF
        val g = (color ushr 8) and 0xFF
        val b = color and 0xFF
        for (v in verts) {
            val vertex = buffer.vertex(matrix, v.x, v.y, v.z).color(r, g, b, a)
            // The see-through type carries a lightmap the depth-tested one does not.
            if (lit) vertex.uv2(LightTexture.FULL_BRIGHT)
            vertex.endVertex()
        }
    }

    /**
     * Lays each glyph flat on the band at its own angle so the text curves around the ring.
     * Drawn twice, front face up and front face down, so the halo reads from both sides; the
     * scale stays positive throughout because a mirroring transform would flip the glyph winding
     * and make the culled back faces the visible ones.
     */
    private fun drawLabel(
        pose: PoseStack,
        buffers: MultiBufferSource,
        font: Font,
        label: Label,
        displayMode: Font.DisplayMode,
    ) {
        // Caps at ~60% of the band width, the proportion the GMod render target used, so the
        // letters keep clear margin to both rings.
        val glyphScale = bandWidth * 0.6f / CAP_HEIGHT
        val widths = label.chars.map { font.width(it).toFloat() }
        val totalWidth = widths.sum() * glyphScale
        // Centre the label inside the arc it owns.
        val startDegrees = (totalWidth / bandCircumference) * 360f / 2f
        val degreesPerRepeat = 360f / REPEATS

        for (repeat in 0 until REPEATS) {
            val base = repeat * degreesPerRepeat + startDegrees
            var cursor = 0f
            for ((index, glyph) in label.chars.withIndex()) {
                val width = widths[index]
                val centre = (cursor + width / 2f) * glyphScale
                // Negative because increasing YP rotation walks the glyph the other way around
                // the circle than the reading direction.
                val degrees = base - (centre / bandCircumference) * 360f
                cursor += width

                for (face in FACES) {
                    pose.pushPose()
                    // The down-facing copy lays the label out at mirrored angles, which is what a
                    // reader underneath sees as the correct order.
                    pose.mulPose(Axis.YP.rotationDegrees(-degrees * face))
                    // Same plane as the band mesh, or perspective slides the text off the ring.
                    pose.translate(0f, BAND_Y.blocks() + TEXT_LIFT * face, bandRadius)
                    // Tip the vertical glyph plane flat onto the band; the sign picks which side
                    // its front face ends up on, keeping letter tops toward the ring centre.
                    pose.mulPose(Axis.XP.rotationDegrees(90f * face))
                    pose.scale(glyphScale, glyphScale, glyphScale)
                    font.drawInBatch(
                        glyph,
                        -width / 2f,
                        // Cell top sits half the visible cap height up, centring rows 0..7.
                        -CAP_HEIGHT / 2f,
                        label.textColor,
                        false,
                        pose.last().pose(),
                        buffers,
                        displayMode,
                        0,
                        LightTexture.FULL_BRIGHT,
                    )
                    pose.popPose()
                }
            }
        }
    }

    /** Rows a capital glyph actually covers in the 9-row font cell. */
    private const val CAP_HEIGHT = 7f

    private fun Float.blocks(): Float = this / UNITS_PER_BLOCK

    private fun argb(a: Int, r: Int, g: Int, b: Int): Int =
        (a shl 24) or (r shl 16) or (g shl 8) or b
}
