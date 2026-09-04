package gg.earu.afk.api

import gg.earu.afk.Afk
import gg.earu.afk.core.StateTracker
import net.minecraft.network.chat.Component
import net.minecraft.util.StringRepresentable
import net.minecraft.world.entity.player.Player
import org.jetbrains.annotations.ApiStatus
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A player's status, highest priority first: a timing-out player is TIMING_OUT even while away.
 */
enum class PlayerState : StringRepresentable {
    TIMING_OUT,
    AFK,
    TABBED_OUT,
    ACTIVE;

    private val serializedName = name.lowercase()

    override fun getSerializedName(): String = serializedName

    /** Localised label, from `afk.state.<name>` in the lang files. */
    fun displayName(): Component = Component.translatable("afk.state.$serializedName")
}

/**
 * One state transition. [clientSide] says which side saw it: on an integrated server both do,
 * once each. The player is a UUID because the client hears about players it has not loaded.
 */
data class StateChange(
    val playerId: UUID,
    val previous: PlayerState,
    val current: PlayerState,
    val clientSide: Boolean,
)

fun interface StateListener {
    fun onStateChange(change: StateChange)
}

/**
 * Public API for other mods, same shape on every loader. Works on both sides: pass a server
 * player on the server and a client-side player entity on the client. Listeners fire on whichever
 * side saw the change, on that side's main thread. Fabric also exposes the change on
 * `AfkEvents.STATE_CHANGE` and NeoForge posts `AfkStateChangedEvent` to the game bus.
 */
object Afkmon {

    private val listeners = CopyOnWriteArrayList<StateListener>()

    @ApiStatus.Internal
    val serverTracker = StateTracker { uuid, previous, current -> fire(StateChange(uuid, previous, current, false)) }

    @ApiStatus.Internal
    val clientTracker = StateTracker { uuid, previous, current -> fire(StateChange(uuid, previous, current, true)) }

    @JvmStatic
    fun getPlayerState(player: Player): PlayerState = tracker(player).stateOf(player.uuid)

    /** Seconds the player has been in their current state, 0 if unknown. */
    @JvmStatic
    fun getPlayerStateTime(player: Player): Long = tracker(player).secondsInStateOf(player.uuid)

    /** True whenever the away flag is set, even if the player is also timing out. */
    @JvmStatic
    fun isAfk(player: Player): Boolean = tracker(player).flagsOf(player.uuid).afk

    /** True whenever the tabbed-out flag is set, even if the player is also away or timing out. */
    @JvmStatic
    fun isTabbedOut(player: Player): Boolean = tracker(player).flagsOf(player.uuid).tabbedOut

    @JvmStatic
    fun isTimingOut(player: Player): Boolean = tracker(player).flagsOf(player.uuid).timingOut

    @JvmStatic
    fun isActive(player: Player): Boolean = !tracker(player).flagsOf(player.uuid).flagged

    @JvmStatic
    fun addListener(listener: StateListener) {
        listeners.add(listener)
    }

    @JvmStatic
    fun removeListener(listener: StateListener) {
        listeners.remove(listener)
    }

    private fun tracker(player: Player): StateTracker =
        if (player.level().isClientSide) clientTracker else serverTracker

    private fun fire(change: StateChange) {
        for (listener in listeners) {
            try {
                listener.onStateChange(change)
            } catch (e: Exception) {
                Afk.LOGGER.error("Afkmon state listener threw", e)
            }
        }
    }
}
