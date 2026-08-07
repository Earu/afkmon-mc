package gg.earu.afk.neoforge

import gg.earu.afk.Afk
import gg.earu.afk.client.AfkClient
import gg.earu.afk.client.AfkOverlay
import gg.earu.afk.client.render.AfkRingsRenderer
import net.minecraft.client.Minecraft
import net.minecraftforge.client.event.ClientPlayerNetworkEvent
import net.minecraftforge.client.event.RenderGuiEvent
import net.minecraftforge.client.event.RenderLevelStageEvent
import net.minecraftforge.event.TickEvent
import net.minecraftforge.eventbus.api.SubscribeEvent

object ClientEvents {
    fun wire() {
        AfkClient.init(Afk.platform)
        AfkClient.sendPayload = { message -> Payloads.channel.sendToServer(message) }
        AfkClient.canSend = {
            val connection = Minecraft.getInstance().connection?.connection
            connection != null && Payloads.channel.isRemotePresent(connection)
        }
    }

    @SubscribeEvent
    fun onClientTick(event: TickEvent.ClientTickEvent) {
        if (event.phase != TickEvent.Phase.END) return
        AfkClient.tick(Minecraft.getInstance())
    }

    @SubscribeEvent
    fun onRenderLevel(event: RenderLevelStageEvent) {
        if (event.stage != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return
        AfkRingsRenderer.render(
            event.poseStack,
            Minecraft.getInstance().renderBuffers().bufferSource(),
            event.camera,
            event.partialTick,
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
