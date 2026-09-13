# afkmon-mc

AFK detection with rotating status rings around away players.

NeoForge and Fabric, the popular cube game 1.21.11. Other versions of the popular cube game live on their own branches.

- Clients watch their own input and window focus and report when they go away, so being flagged means actually idle, not just standing still.
- A spinning halo of two rings and a curved label appears around flagged players: `AFK`, `TABBED OUT`, or `TIMING OUT`.
- While you are away, an on-screen `Away HH:MM:SS` timer counts up; it lingers a moment after you return so you can see how long you were gone.
- Chat announcements on both transitions, plus a greeting telling you how long you were gone. You only hear about players within your own `maxDistance`.
- The tab list tags flagged players with `[Timing Out]`, `[AFK]` or `[Tabbed Out]`, worst news first. Timing out is red, the rest grey.
- Sound cue when someone goes away or comes back.
- `TIMING OUT` shows on players whose connection has stalled, before the server drops them.

## How it works

The client decides it is away after `afkTimeSeconds` with no mouse movement, key edges, or look input, or with the window unfocused for that long. It tells the server, the server tells everyone else.

All network channels are optional, so vanilla clients and servers connect normally. A vanilla client is never flagged and draws no rings; a modded client on a vanilla server simply never reports.

## Config

`config/afk/server.json`

| Key | Default | Meaning |
| --- | --- | --- |
| `afkTimeSeconds` | 90 | Idle seconds before a client flags itself away. Sent to clients on join. |
| `soundsEnabled` | true | Sound cue on going away and coming back. |
| `announceEnabled` | true | Chat announcements and the welcome-back message. |
| `timingOutThresholdSeconds` | 5.0 | Unanswered keepalive age before `TIMING OUT` shows. Vanilla drops the player at 15. |

`config/afk/client.json`

| Key | Default | Meaning |
| --- | --- | --- |
| `ringsEnabled` | true | Master toggle for the halo. |
| `minDistance` | 2.0 | Hide other players' halos closer than this. Your own always draws. |
| `maxDistance` | 64.0 | Skip halos past this, and the chat announcements about those players too. |
| `seeThroughWalls` | false | Draw halos through terrain. |
| `awayOverlayEnabled` | true | The on-screen away timer. |
| `soundsEnabled` | true | The away/back cues. Off mutes them on your client only. |

## Testing tabbed-out

Vanilla opens the pause menu whenever the window loses focus, which hides the world just as your `TABBED OUT` ring appears. Press F3+P once to toggle `pauseOnLostFocus` off while testing. Note that in singleplayer the game also freezes while the pause menu is open, so states only progress on a dedicated server or with that toggle off; other players see your ring regardless.

## Building

JDK 21.

```
./gradlew build
./gradlew :neoforge:runClient
./gradlew :fabric:runClient
```

Jars land in `neoforge/build/libs` and `fabric/build/libs`. `common` holds all the logic and is compiled into both loader jars; the loader modules are thin wiring.

## API

Add the jar to your compile classpath and use `gg.earu.afk.api.Afkmon`. Same shape on every loader. Each side keeps its own states: `server()` on the server, `client()` on the client, or `side(isClientSide)`. All keyed by UUID, so the client can ask about players it has not loaded.

```kotlin
val view = Afkmon.client()          // or Afkmon.server(), Afkmon.side(level.isClientSide)
view.stateOf(uuid)                  // PlayerState: TIMING_OUT, AFK, TABBED_OUT or ACTIVE
view.secondsInStateOf(uuid)         // seconds in that state, 0 if unknown
view.isAfk(uuid)                    // also isTabbedOut, isTimingOut, isActive
view.flagsOf(uuid)                  // AfkFlags: all three at once, plus sinceEpochMs
view.secondsAfk(uuid)               // per flag clocks, also secondsTabbedOut, secondsTimingOut
view.flaggedPlayers()               // Map<UUID, PlayerState> of everyone not ACTIVE
view.afkTimeSeconds()               // the threshold, 0 on a client before the server sent it
view.hasMod(uuid)                   // server only, vanilla players are never AFK or TABBED_OUT
Afkmon.getPlayerState(player)       // entity conveniences pick the side from the entity's level
Afkmon.addListener { change -> change.playerId; change.previous; change.current; change.previousSeconds }
Afkmon.addFlagsListener { change -> change.previous.afk; change.current.tabbedOut }
```

When several flags are set the state is the highest of TIMING OUT > AFK > TABBED OUT > ACTIVE. The `is*` getters read the raw flags, so `isAfk` stays true while an away player is timing out. `PlayerState` is a `StringRepresentable`, `displayName()` gives the localised label and `tag()` the coloured `[AFK]` the tab list appends (null for ACTIVE).

State listeners fire when the reduced state changes, flags listeners on every flag flip, so tabbing out while away only reaches the second. Both fire on the side that saw the change, on its main thread; on an integrated server both sides fire once each, `clientSide` tells them apart. A flagged player logging out is reported as a change to ACTIVE. The same changes go through the loader's own pipeline: `AfkEvents.STATE_CHANGE` and `AfkEvents.FLAGS_CHANGE` on Fabric, `AfkStateChangedEvent` and `AfkFlagsChangedEvent` on the NeoForge game bus.

`AfkmonClient` is the client-only half, never load it on a dedicated server. The tab list tag is applied by rewriting the entry's display name, so a UI reading `PlayerInfo.getTabListDisplayName` gets the tag too: `undecoratedName(info)` or `undecoratedName(uuid)` gives the name without it, as the server set it or with vanilla team formatting (null only when the player has no entry). `secondsSinceInput()` and `secondsUnfocused()` read the local detector, the local player goes away once either reaches the threshold.

## Porting to a new version of the popular cube game

Two places need attention, in this order:

1. `common/src/main/kotlin/gg/earu/afk/client/render/AfkRingsRenderer.kt` and the render hooks in each loader module. Render types, buffer flushing, and the font batch API move around between major versions. `RingGeometry.kt` is pure maths and never changes.
2. `common/src/main/java/gg/earu/afk/mixin/ServerConnectionAccessor.java`. It reads two private vanilla fields. Confirm they still exist before trusting a build:

```
javap -p -cp <minecraft.jar> net.minecraft.server.network.ServerCommonPacketListenerImpl | grep keepAlive
```

On 1.20.1 those fields live on `ServerGamePacketListenerImpl` instead, and the whole payload layer needs rewriting since `CustomPacketPayload` does not exist there.

Everything else (detection, durations, config, codecs, input sampling) is version-agnostic.

