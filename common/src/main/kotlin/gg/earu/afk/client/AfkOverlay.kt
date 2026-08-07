package gg.earu.afk.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics

/**
 * Port of afkui.lua: while you are away, a live "Away HH:MM:SS" timer sits near the top of the
 * screen. When you come back the last reading lingers for a moment in a warmer tint, so you can
 * see how long you were gone before it fades.
 */
object AfkOverlay {

    private const val SCALE = 2.5f

    /** Vertical position as a fraction of the screen, from the Lua's sh * 0.1. */
    private const val TOP_FRACTION = 0.1f

    private const val FADE_IN_SECONDS = 0.5f

    /** After coming back the text holds full alpha for 2s, fades over 0.5s, and is gone at 3s. */
    private const val HOLD_SECONDS = 2.0f
    private const val FADE_OUT_SECONDS = 0.5f
    private const val REMOVE_SECONDS = 3.0f

    private var wasAfk = false
    private var shownSinceMs = 0L
    private var outSinceMs = 0L
    private var lastText = ""

    fun render(graphics: GuiGraphics) {
        if (!AfkClient.config.awayOverlayEnabled) return
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return

        val state = AfkClient.states[player.uuid]
        val afk = state?.afk == true
        val now = System.currentTimeMillis()
        if (afk && !wasAfk) shownSinceMs = now
        if (!afk && wasAfk) outSinceMs = now
        wasAfk = afk

        val frac: Float
        if (afk) {
            frac = (((now - shownSinceMs) / 1000f) / FADE_IN_SECONDS).coerceIn(0f, 1f)
            // The server backdates the timestamp by the afk threshold, so like GMod the timer
            // starts at that threshold rather than at zero.
            val seconds = ((now - (state?.sinceEpochMs ?: now)) / 1000L).coerceAtLeast(0)
            lastText = "Away %02d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
        } else {
            if (outSinceMs == 0L || lastText.isEmpty()) return
            val elapsed = (now - outSinceMs) / 1000f
            if (elapsed > REMOVE_SECONDS) return
            frac = ((HOLD_SECONDS + FADE_OUT_SECONDS - elapsed) / FADE_OUT_SECONDS).coerceIn(0f, 1f)
        }
        if (frac <= 0f) return

        val text = lastText
        val font = mc.font
        val width = font.width(text)
        val pose = graphics.pose()
        pose.pushPose()
        pose.translate(
            graphics.guiWidth() / 2f - width * SCALE / 2f,
            graphics.guiHeight() * TOP_FRACTION,
            0f,
        )
        pose.scale(SCALE, SCALE, 1f)
        // Colours from the Lua: dark offset shadow, cool white while away, warm tint once back.
        graphics.drawString(font, text, 1, 1, color(30, 30, 30, frac * 100f), false)
        val main = if (afk) color(244, 254, 255, frac * 200f) else color(236, 253, 154, frac * 200f)
        graphics.drawString(font, text, 0, 0, main, false)
        pose.popPose()
    }

    private fun color(r: Int, g: Int, b: Int, a: Float): Int =
        (a.toInt().coerceIn(1, 255) shl 24) or (r shl 16) or (g shl 8) or b
}
