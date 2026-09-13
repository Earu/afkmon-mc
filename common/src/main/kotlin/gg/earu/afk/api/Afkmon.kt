package gg.earu.afk.api

import gg.earu.afk.Afk
import gg.earu.afk.core.StateTracker
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor
import net.minecraft.util.StringRepresentable
import net.minecraft.world.entity.player.Player
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

    /** The bracketed, coloured tag the tab list appends to a player's name. Null for ACTIVE. */
    fun tag(): Component? = when (this) {
        TIMING_OUT -> TIMING_OUT_TAG
        AFK -> AFK_TAG
        TABBED_OUT -> TABBED_OUT_TAG
        ACTIVE -> null
    }

    private companion object {
        /** The red the halo paints TIMING OUT with, so the tag and the ring read as one status. */
        val TIMING_OUT_TAG: Component = Component.literal("[Timing Out]")
            .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFF5731)))
        val AFK_TAG: Component = Component.literal("[AFK]").withStyle(ChatFormatting.GRAY)
        val TABBED_OUT_TAG: Component = Component.literal("[Tabbed Out]").withStyle(ChatFormatting.GRAY)
    }
}

/**
 * One state transition. [clientSide] says which side saw it: on an integrated server both do,
 * once each. The player is a UUID because the client hears about players it has not loaded.
 * [previousSeconds] is how long the player spent in [previous], 0 when this side never saw them
 * enter it. A flagged player logging out is reported as a change to ACTIVE.
 */
data class StateChange(
    val playerId: UUID,
    val previous: PlayerState,
    val current: PlayerState,
    val clientSide: Boolean,
    val previousSeconds: Long = 0L,
)

/** Fires only when the reduced [PlayerState] changes. For every raw flag flip use [FlagsListener]. */
fun interface StateListener {
    fun onStateChange(change: StateChange)
}

/** One raw flag change: any of the three flags flipped, whether or not the reduced state moved. */
data class FlagsChange(
    val playerId: UUID,
    val previous: AfkFlags,
    val current: AfkFlags,
    val clientSide: Boolean,
)

fun interface FlagsListener {
    fun onFlagsChange(change: FlagsChange)
}

/**
 * Public API for other mods, same shape on every loader. Each side keeps its own states: ask
 * [server] or [client] for an [AfkStateView] keyed by UUID, or pass a player entity to the
 * convenience getters and the side is picked from its level. Listeners fire on whichever side saw
 * the change, on that side's main thread, flags first then state. Fabric also exposes them on
 * `AfkEvents.STATE_CHANGE` and `AfkEvents.FLAGS_CHANGE`, NeoForge posts `AfkStateChangedEvent`
 * and `AfkFlagsChangedEvent` to the game bus. Client-only helpers live on [AfkmonClient].
 */
object Afkmon {

    private val listeners = CopyOnWriteArrayList<StateListener>()
    private val flagsListeners = CopyOnWriteArrayList<FlagsListener>()

    internal val serverTracker = tracker(clientSide = false)
    internal val clientTracker = tracker(clientSide = true)

    /** States as the server sees them. Empty on a client that is not hosting. */
    @JvmStatic
    fun server(): AfkStateView = serverTracker

    /** States as this client was told them, for every player on the server, loaded or not. */
    @JvmStatic
    fun client(): AfkStateView = clientTracker

    @JvmStatic
    fun side(clientSide: Boolean): AfkStateView = if (clientSide) clientTracker else serverTracker

    @JvmStatic
    fun getPlayerState(player: Player): PlayerState = view(player).stateOf(player.uuid)

    /** Seconds the player has been in their current state, 0 if unknown. */
    @JvmStatic
    fun getPlayerStateTime(player: Player): Long = view(player).secondsInStateOf(player.uuid)

    /** True whenever the away flag is set, even if the player is also timing out. */
    @JvmStatic
    fun isAfk(player: Player): Boolean = view(player).isAfk(player.uuid)

    /** True whenever the tabbed-out flag is set, even if the player is also away or timing out. */
    @JvmStatic
    fun isTabbedOut(player: Player): Boolean = view(player).isTabbedOut(player.uuid)

    @JvmStatic
    fun isTimingOut(player: Player): Boolean = view(player).isTimingOut(player.uuid)

    @JvmStatic
    fun isActive(player: Player): Boolean = view(player).isActive(player.uuid)

    @JvmStatic
    fun addListener(listener: StateListener) {
        listeners.add(listener)
    }

    @JvmStatic
    fun removeListener(listener: StateListener) {
        listeners.remove(listener)
    }

    @JvmStatic
    fun addFlagsListener(listener: FlagsListener) {
        flagsListeners.add(listener)
    }

    @JvmStatic
    fun removeFlagsListener(listener: FlagsListener) {
        flagsListeners.remove(listener)
    }

    private fun view(player: Player): AfkStateView = side(player.level().isClientSide)

    private fun tracker(clientSide: Boolean) = StateTracker(
        onChange = { uuid, previous, current, seconds ->
            dispatch(listeners) { it.onStateChange(StateChange(uuid, previous, current, clientSide, seconds)) }
        },
        onFlagsChange = { uuid, previous, current ->
            dispatch(flagsListeners) { it.onFlagsChange(FlagsChange(uuid, previous, current, clientSide)) }
        },
    )

    private inline fun <L> dispatch(listeners: List<L>, call: (L) -> Unit) {
        for (listener in listeners) {
            try {
                call(listener)
            } catch (e: Exception) {
                Afk.LOGGER.error("Afkmon listener threw", e)
            }
        }
    }
}
