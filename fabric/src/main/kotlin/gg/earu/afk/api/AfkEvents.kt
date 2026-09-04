package gg.earu.afk.api

import net.fabricmc.fabric.api.event.Event
import net.fabricmc.fabric.api.event.EventFactory

/** Fabric events mirroring [Afkmon.addListener]. Same payload, loader-native registration. */
object AfkEvents {
    @JvmField
    val STATE_CHANGE: Event<StateListener> = EventFactory.createArrayBacked(StateListener::class.java) { listeners ->
        StateListener { change -> for (listener in listeners) listener.onStateChange(change) }
    }
}
