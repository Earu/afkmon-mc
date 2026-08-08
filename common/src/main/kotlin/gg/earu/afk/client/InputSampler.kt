package gg.earu.afk.client

import gg.earu.afk.core.AfkDetector
import net.minecraft.client.Minecraft

/**
 * Reads the same things afkmon.lua watched. The Lua polled a hand-picked subset of keys; the bound
 * movement and action keys are the equivalent here, plus [RawInput] for everything the bindings
 * cannot see.
 */
object InputSampler {

    fun sample(mc: Minecraft): AfkDetector.Sample {
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
            // Key bindings go quiet as soon as a screen opens, so typing only shows up here.
            inputEvents = RawInput.events,
            windowFocused = mc.isWindowActive,
        )
    }
}
