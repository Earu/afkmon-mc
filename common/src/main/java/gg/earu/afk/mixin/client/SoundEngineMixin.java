package gg.earu.afk.mixin.client;

import gg.earu.afk.Afk;
import gg.earu.afk.client.AfkClient;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The cues arrive as regular server sound packets, so the client-side kill switch has to sit in
 * the sound engine rather than where they are sent.
 */
@Mixin(SoundEngine.class)
public class SoundEngineMixin {

    @Inject(method = "play", at = @At("HEAD"), cancellable = true)
    private void afk$muteCues(SoundInstance sound, CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
        if (AfkClient.INSTANCE.getConfig().getSoundsEnabled()) return;
        if (sound.getIdentifier().getNamespace().equals(Afk.MOD_ID)) {
            cir.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
        }
    }
}
