package gg.earu.afk.mixin.client;

import gg.earu.afk.client.RawInput;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The only place raw typing is visible. Key bindings are not set while a screen is open, so
 * without this the tracker cannot tell "typing in chat" from "away from the keyboard".
 */
@Mixin(KeyboardHandler.class)
public class KeyboardInputMixin {

    @Inject(method = "keyPress", at = @At("HEAD"))
    private void afk$onKeyPress(long window, int action, KeyEvent event, CallbackInfo ci) {
        // Repeats are ignored so a taped-down key still ages into away, like the key binding poll.
        if (action != GLFW.GLFW_REPEAT) RawInput.record();
    }

    @Inject(method = "charTyped", at = @At("HEAD"))
    private void afk$onCharTyped(long window, CharacterEvent event, CallbackInfo ci) {
        RawInput.record();
    }
}
