package gg.earu.afk.server

import gg.earu.afk.Afk
import gg.earu.afk.core.AfkConfig
import gg.earu.afk.core.NiceTime
import gg.earu.afk.core.PlayerAfkState
import gg.earu.afk.core.ServerConfig
import gg.earu.afk.net.AfkPayloads
import gg.earu.afk.platform.Platform
import net.minecraft.ChatFormatting
import net.minecraft.Util
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Loader-agnostic half of the server logic: holds every player's status, announces transitions,
 * and detects stalled connections. Loaders inject the two send functions.
 */
object AfkServer {

    var sendToPlayer: (ServerPlayer, AfkPayloads.Message) -> Unit = { _, _ -> }
    var canSendTo: (ServerPlayer) -> Boolean = { _ -> false }

    /**
     * Ms since the player's pending keepalive was sent, or -1 when none is pending. Injected per
     * loader: Fabric reads it through the accessor mixin, Forge through SRG reflection (this
     * toolchain cannot ship refmapped mixins on 1.20.1).
     */
    var keepAliveAge: (ServerPlayer, Long) -> Long = { _, _ -> -1L }

    lateinit var config: ServerConfig
        private set

    private val states = ConcurrentHashMap<UUID, PlayerAfkState>()

    /** When each player last changed status, for the "present for X" half of the announcements. */
    private val lastTransitionMs = ConcurrentHashMap<UUID, Long>()

    /** Joiners awaiting their config/state sync, mapped to the give-up deadline. */
    private val pendingSync = ConcurrentHashMap<UUID, Long>()

    private var tickCounter = 0

    fun init(platform: Platform) {
        config = AfkConfig.loadServer(platform.configDir)
    }

    fun onPlayerJoin(player: ServerPlayer) {
        lastTransitionMs[player.uuid] = System.currentTimeMillis()
        // Channel negotiation may still be in flight at this point, so the sync is retried from
        // the tick loop until the channel shows up (or the deadline passes for vanilla clients).
        pendingSync[player.uuid] = System.currentTimeMillis() + SYNC_DEADLINE_MS
    }

    private fun trySync(player: ServerPlayer) {
        val deadline = pendingSync[player.uuid] ?: return
        if (!canSendTo(player)) {
            if (System.currentTimeMillis() > deadline) pendingSync.remove(player.uuid)
            return
        }

        pendingSync.remove(player.uuid)
        sendToPlayer(player, AfkPayloads.ConfigPayload(config.afkTimeSeconds))
        // Catch the joiner up on everyone already flagged.
        for ((uuid, state) in states) {
            if (state.flagged) sendToPlayer(player, state.toPayload(uuid))
        }
    }

    fun onPlayerLeave(player: ServerPlayer) {
        states.remove(player.uuid)
        lastTransitionMs.remove(player.uuid)
        pendingSync.remove(player.uuid)
        // Clear the rings everywhere: the entity can linger on clients for a moment.
        broadcast(player.server, PlayerAfkState().toPayload(player.uuid))
    }

    fun onReport(player: ServerPlayer, payload: AfkPayloads.ReportPayload) {
        val previous = states[player.uuid] ?: PlayerAfkState()
        if (payload.afk == previous.afk && payload.tabbedOut == previous.tabbedOut) return

        val now = System.currentTimeMillis()
        val next = previous.copy(
            afk = payload.afk,
            tabbedOut = payload.tabbedOut,
            // Backdate so the readout counts from the last real input, not from when we were told.
            sinceEpochMs = if (payload.afk) now - config.afkTimeSeconds * 1000L else now,
        )
        states[player.uuid] = next

        val announceSeconds =
            if (payload.afk != previous.afk) announce(player, payload.afk, now) else AfkPayloads.NO_ANNOUNCEMENT
        broadcast(player.server, next.toPayload(player.uuid, announceSeconds))
    }

    fun onTick(server: MinecraftServer) {
        // Keepalives are second-scale; polling every tick would be wasted work.
        if (++tickCounter < TICKS_PER_CHECK) return
        tickCounter = 0

        val thresholdMs = (config.timingOutThresholdSeconds * 1000.0).toLong()
        val now = Util.getMillis()
        for (player in server.playerList.players) {
            trySync(player)

            val timingOut = keepAliveAge(player, now) > thresholdMs

            val previous = states[player.uuid] ?: PlayerAfkState()
            if (timingOut == previous.timingOut) continue

            Afk.LOGGER.info("{} {}", player.name.string, if (timingOut) "is timing out" else "is no longer timing out")
            val next = previous.copy(
                timingOut = timingOut,
                sinceEpochMs = if (previous.flagged) previous.sinceEpochMs else System.currentTimeMillis(),
            )
            states[player.uuid] = next
            broadcast(server, next.toPayload(player.uuid))
        }
    }

    /** Returns the duration clients should quote, or [AfkPayloads.NO_ANNOUNCEMENT] when announcements are off. */
    private fun announce(player: ServerPlayer, afk: Boolean, now: Long): Int {
        val since = lastTransitionMs.put(player.uuid, now) ?: now
        val elapsed = ((now - since) / 1000L).coerceAtLeast(0)
        // The away duration started one threshold before the client noticed.
        val duration = if (afk) elapsed else elapsed + config.afkTimeSeconds
        val transition = if (afk) {
            " away (present for ${NiceTime.format(duration)})"
        } else {
            " back (away for ${NiceTime.format(duration)})"
        }

        Afk.LOGGER.info("{}{}", player.name.string, transition)
        if (!config.announceEnabled) return AfkPayloads.NO_ANNOUNCEMENT

        // Modded clients print this themselves once the state packet lands, so that each can apply
        // its own maxDistance. Vanilla ones have no way to, so they still get it over chat. The
        // display name keeps whatever colour the server gave the player.
        val line = Component.empty()
            .append(player.displayName)
            .append(Component.literal(transition).withStyle(ChatFormatting.DARK_GRAY))
        for (listener in player.server.playerList.players) {
            if (!canSendTo(listener)) listener.sendSystemMessage(line)
        }

        if (!afk) welcomeBack(player, duration)
        if (config.soundsEnabled) {
            val sound = (if (afk) SoundEvents.NOTE_BLOCK_CHIME else SoundEvents.NOTE_BLOCK_PLING).value()
            player.serverLevel().playSound(null, player.x, player.y, player.z, sound, SoundSource.PLAYERS, 0.6f, 1f)
        }
        return duration.toInt()
    }

    /** The coloured greeting from afkmon.lua, sent only to whoever came back. */
    private fun welcomeBack(player: ServerPlayer, awaySeconds: Long) {
        val message = Component.literal("Welcome back").withStyle(style(100, 255, 100))
            .append(Component.literal("!").withStyle(style(50, 200, 50)))
            .append(Component.literal(" You were away for ").withStyle(ChatFormatting.WHITE))
            .append(Component.literal(NiceTime.format(awaySeconds)).withStyle(style(200, 200, 255)))
            .append(Component.literal(".").withStyle(style(100, 255, 100)))
        player.sendSystemMessage(message)
    }

    private fun style(r: Int, g: Int, b: Int): Style =
        Style.EMPTY.withColor(TextColor.fromRgb((r shl 16) or (g shl 8) or b))

    private fun broadcast(server: MinecraftServer, payload: AfkPayloads.StatePayload) {
        for (listener in server.playerList.players) {
            if (canSendTo(listener)) sendToPlayer(listener, payload)
        }
    }

    private fun PlayerAfkState.toPayload(uuid: UUID, announceSeconds: Int = AfkPayloads.NO_ANNOUNCEMENT) =
        AfkPayloads.StatePayload(uuid, afk, tabbedOut, timingOut, sinceEpochMs, announceSeconds)

    private const val TICKS_PER_CHECK = 20

    /** How long to keep retrying the join sync before assuming a vanilla client. */
    private const val SYNC_DEADLINE_MS = 10_000L
}
