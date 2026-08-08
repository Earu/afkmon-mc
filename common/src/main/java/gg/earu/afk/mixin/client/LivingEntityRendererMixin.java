package gg.earu.afk.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import gg.earu.afk.client.render.PlayerRenderPose;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Captures the matrix every player model is submitted with, so the rings renderer can place the
 * halo where the player is actually drawn instead of where the entity claims to stand. The two
 * only disagree under physics mods that render players through an extra contraption transform.
 *
 * Hooked here rather than at the dispatcher because that is where those mods add their transform:
 * Sable's own dispatcher mixin rotates the pose stack, and capturing at the dispatcher entry
 * raced it. This method runs downstream of every dispatcher-level transform, whoever added it.
 * AvatarRenderer does not override submit, so this is the method players go through.
 */
@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin {

    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V", at = @At("HEAD"))
    private void afk$captureSubmit(
        LivingEntityRenderState state,
        PoseStack poseStack,
        SubmitNodeCollector collector,
        CameraRenderState camera,
        CallbackInfo ci
    ) {
        if (!(state instanceof AvatarRenderState avatar)) return;
        // The dispatcher already translated to the player, so the matrix is complete as is.
        PlayerRenderPose.record(avatar.id, new Matrix4f(poseStack.last().pose()));
    }
}
