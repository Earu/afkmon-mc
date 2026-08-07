package gg.earu.afk.neoforge

import gg.earu.afk.Afk
import gg.earu.afk.client.AfkClient
import gg.earu.afk.client.render.AfkRingsRenderer
import net.minecraft.client.Minecraft
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket
import net.neoforged.bus.api.SubscribeEvent
import gg.earu.afk.client.AfkOverlay
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.RenderGuiEvent
import net.neoforged.neoforge.client.event.RenderLevelStageEvent

object ClientEvents {
    fun wire() {
        AfkClient.init(Afk.platform)
        AfkClient.sendPayload = { payload ->
            Minecraft.getInstance().connection?.send(ServerboundCustomPayloadPacket(payload))
        }
        AfkClient.canSend = {
            Minecraft.getInstance().connection?.hasChannel(gg.earu.afk.net.AfkPayloads.ReportPayload.TYPE) == true
        }
    }

    @SubscribeEvent
    fun onClientTick(@Suppress("UNUSED_PARAMETER") event: ClientTickEvent.Post) {
        AfkClient.tick(Minecraft.getInstance())
    }

    @SubscribeEvent
    fun onRenderLevel(event: RenderLevelStageEvent) {
        if (event.stage != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return
        AfkRingsRenderer.render(
            event.poseStack,
            Minecraft.getInstance().renderBuffers().bufferSource(),
            event.camera,
            event.partialTick.getGameTimeDeltaPartialTick(false),
        )
    }

    @SubscribeEvent
    fun onRenderGui(event: RenderGuiEvent.Post) {
        AfkOverlay.render(event.guiGraphics)
    }

    @SubscribeEvent
    fun onLoggingOut(@Suppress("UNUSED_PARAMETER") event: ClientPlayerNetworkEvent.LoggingOut) {
        AfkClient.onDisconnect()
    }
}
