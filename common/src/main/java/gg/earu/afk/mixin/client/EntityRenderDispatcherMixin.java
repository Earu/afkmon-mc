package gg.earu.afk.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import gg.earu.afk.client.render.PlayerRenderPose;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Captures the matrix every player model is rendered with, so the rings renderer can place the
 * halo where the player is actually drawn instead of where the entity claims to stand. The two
 * only disagree under physics mods that render players through an extra contraption transform.
 */
@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {

    @Inject(method = "render", at = @At("HEAD"))
    private void afk$captureRender(
        Entity entity,
        double x,
        double y,
        double z,
        float rotationYaw,
        float partialTick,
        PoseStack poseStack,
        MultiBufferSource buffer,
        int packedLight,
        CallbackInfo ci
    ) {
        if (!(entity instanceof Player)) return;
        // Bake in the camera-relative translation the dispatcher applies right after this point.
        PlayerRenderPose.record(entity.getId(), new Matrix4f(poseStack.last().pose()).translate((float) x, (float) y, (float) z));
    }
}
