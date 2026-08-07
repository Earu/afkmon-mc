package gg.earu.afk.neoforge

import gg.earu.afk.client.AfkClient
import gg.earu.afk.net.AfkPayloads

/**
 * Client-only payload handling. This class must never load on a dedicated server, so it is only
 * referenced from inside the handler lambdas in [Payloads].
 */
object ClientPayloadHandler {
    fun handleState(payload: AfkPayloads.StatePayload) = AfkClient.onState(payload)

    fun handleConfig(payload: AfkPayloads.ConfigPayload) = AfkClient.onConfig(payload)
}
