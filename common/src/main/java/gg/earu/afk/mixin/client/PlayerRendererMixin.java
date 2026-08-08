package gg.earu.afk.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import gg.earu.afk.client.render.PlayerRenderPose;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Captures the matrix every player model is rendered with, so the rings renderer can place the
 * halo where the player is actually drawn instead of where the entity claims to stand. The two
 * only disagree under physics mods that render players through an extra contraption transform.
 *
 * Hooked here rather than at the dispatcher because that is where those mods add their transform:
 * Sable's own dispatcher mixin rotates the pose stack, and capturing at the dispatcher entry
 * raced it. This method runs downstream of every dispatcher-level transform, whoever added it.
 */
@Mixin(PlayerRenderer.class)
public class PlayerRendererMixin {

    @Inject(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"))
    private void afk$captureRender(
        AbstractClientPlayer player,
        float entityYaw,
        float partialTick,
        PoseStack poseStack,
        MultiBufferSource buffer,
        int packedLight,
        CallbackInfo ci
    ) {
        // The dispatcher already translated to the player, so the matrix is complete as is.
        PlayerRenderPose.record(player.getId(), new Matrix4f(poseStack.last().pose()));
    }
}
