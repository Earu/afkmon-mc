package gg.earu.afk.api

import net.neoforged.bus.api.Event
import java.util.UUID

/** Posted on the game bus for every change [Afkmon.addListener] would report. Not cancellable. */
class AfkStateChangedEvent(val change: StateChange) : Event() {
    val playerId: UUID get() = change.playerId
    val previous: PlayerState get() = change.previous
    val current: PlayerState get() = change.current
    val isClientSide: Boolean get() = change.clientSide
}
