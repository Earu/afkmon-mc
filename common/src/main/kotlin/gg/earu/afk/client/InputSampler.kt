package gg.earu.afk.client

import gg.earu.afk.core.AfkDetector
import net.minecraft.client.Minecraft

/**
 * Reads the same things afkmon.lua watched, using only public client API so no mixin is needed.
 * The Lua polled a hand-picked subset of keys; the bound movement and action keys are the
 * equivalent here.
 */
object InputSampler {

    fun sample(mc: Minecraft): AfkDetector.Sample {
        val player = mc.player
        val options = mc.options
        // GLFW keeps advancing the virtual cursor while it is grabbed, so this covers both
        // looking around in game and moving the pointer in a menu.
        return AfkDetector.Sample(
            mouseX = mc.mouseHandler.xpos(),
            mouseY = mc.mouseHandler.ypos(),
            anyKeyDown = options.keyUp.isDown ||
                options.keyDown.isDown ||
                options.keyLeft.isDown ||
                options.keyRight.isDown ||
                options.keyJump.isDown ||
                options.keyShift.isDown ||
                options.keySprint.isDown ||
                options.keyAttack.isDown ||
                options.keyUse.isDown ||
                options.keyInventory.isDown ||
                options.keyChat.isDown,
            yaw = player?.yRot ?: 0f,
            pitch = player?.xRot ?: 0f,
            windowFocused = mc.isWindowActive,
        )
    }
}
