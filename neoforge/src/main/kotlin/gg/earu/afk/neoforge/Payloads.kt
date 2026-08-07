package gg.earu.afk.neoforge

import gg.earu.afk.net.AfkPayloads
import gg.earu.afk.server.AfkServer
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent

/**
 * NeoForge channel registration for the shared payloads. Client-side handling lives in
 * [ClientPayloadHandler], whose class must only load when a handler actually runs, never
 * on a dedicated server.
 */
object Payloads {
    fun register(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar("1").optional()

        registrar.playToServer(AfkPayloads.ReportPayload.TYPE, AfkPayloads.ReportPayload.CODEC) { payload, context ->
            val player = context.player() as? ServerPlayer ?: return@playToServer
            context.enqueueWork { AfkServer.onReport(player, payload) }
        }

        registrar.playToClient(AfkPayloads.StatePayload.TYPE, AfkPayloads.StatePayload.CODEC) { payload, context ->
            context.enqueueWork { ClientPayloadHandler.handleState(payload) }
        }

        registrar.playToClient(AfkPayloads.ConfigPayload.TYPE, AfkPayloads.ConfigPayload.CODEC) { payload, context ->
            context.enqueueWork { ClientPayloadHandler.handleConfig(payload) }
        }
    }
}
