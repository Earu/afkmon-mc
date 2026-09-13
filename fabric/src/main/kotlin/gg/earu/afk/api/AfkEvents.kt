package gg.earu.afk.api

import net.fabricmc.fabric.api.event.Event
import net.fabricmc.fabric.api.event.EventFactory

/** Fabric events mirroring [Afkmon.addListener] and [Afkmon.addFlagsListener]. Same payloads, loader-native registration. */
object AfkEvents {
    @JvmField
    val STATE_CHANGE: Event<StateListener> = EventFactory.createArrayBacked(StateListener::class.java) { listeners ->
        StateListener { change -> for (listener in listeners) listener.onStateChange(change) }
    }

    @JvmField
    val FLAGS_CHANGE: Event<FlagsListener> = EventFactory.createArrayBacked(FlagsListener::class.java) { listeners ->
        FlagsListener { change -> for (listener in listeners) listener.onFlagsChange(change) }
    }
}
