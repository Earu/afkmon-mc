package gg.earu.afk.neoforge

import gg.earu.afk.Afk
import gg.earu.afk.client.AfkClient
import gg.earu.afk.client.AfkOverlay
import gg.earu.afk.client.RawInput
import gg.earu.afk.client.render.AfkRingsRenderer
import gg.earu.afk.client.render.PlayerRenderPose
import net.minecraft.client.Minecraft
import net.minecraftforge.client.event.ClientPlayerNetworkEvent
import net.minecraftforge.client.event.InputEvent
import net.minecraftforge.client.event.RenderGuiEvent
import net.minecraftforge.client.event.RenderLevelStageEvent
import net.minecraftforge.client.event.RenderPlayerEvent
import net.minecraftforge.client.event.ScreenEvent
import net.minecraftforge.event.TickEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import org.joml.Matrix4f
import org.lwjgl.glfw.GLFW

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

    // Events instead of the keyboard mixin, for the same reason as the keepalive probe above: a
    // production 1.20.1 Forge mixin would need a refmap MDG legacy cannot produce.
    @SubscribeEvent
    fun onKeyInput(event: InputEvent.Key) {
        // Repeats are ignored so a taped-down key still ages into away, like the key binding poll.
        if (event.action != GLFW.GLFW_REPEAT) RawInput.record()
    }

    // A screen swallows the key before InputEvent.Key fires, so typing in chat only shows up here.
    @SubscribeEvent
    fun onScreenKey(@Suppress("UNUSED_PARAMETER") event: ScreenEvent.KeyPressed.Pre) {
        RawInput.record()
    }

    @SubscribeEvent
    fun onScreenChar(@Suppress("UNUSED_PARAMETER") event: ScreenEvent.CharacterTyped.Pre) {
        RawInput.record()
    }

    // The event equivalent of the Fabric-only EntityRenderDispatcherMixin. Fires with the
    // dispatcher's camera-relative translation already on the stack, so the matrix is used as is.
    @SubscribeEvent
    fun onRenderPlayer(event: RenderPlayerEvent.Pre) {
        PlayerRenderPose.record(event.entity.id, Matrix4f(event.poseStack.last().pose()))
    }
}
