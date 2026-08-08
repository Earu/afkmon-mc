package gg.earu.afk.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import gg.earu.afk.client.render.PlayerRenderPose;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
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
 */
@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {

    @Inject(method = "submit", at = @At("HEAD"))
    private void afk$captureSubmit(
        EntityRenderState state,
        CameraRenderState camera,
        double x,
        double y,
        double z,
        PoseStack poseStack,
        SubmitNodeCollector collector,
        CallbackInfo ci
    ) {
        if (!(state instanceof AvatarRenderState avatar)) return;
        // Bake in the camera-relative translation the dispatcher applies right after this point.
        PlayerRenderPose.record(avatar.id, new Matrix4f(poseStack.last().pose()).translate((float) x, (float) y, (float) z));
    }
}
