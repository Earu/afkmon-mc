# afkmon-mc

AFK detection with rotating status rings around away players.

NeoForge and Fabric, the popular cube game 1.21.1. Other versions of the popular cube game live on their own branches.

- Clients watch their own input and window focus and report when they go away, so being flagged means actually idle, not just standing still.
- A spinning halo of two rings and a curved label appears around flagged players: `AFK`, `TABBED OUT`, or `TIMING OUT`.
- While you are away, an on-screen `Away HH:MM:SS` timer counts up; it lingers a moment after you return so you can see how long you were gone.
- Chat announcements on both transitions, plus a greeting telling you how long you were gone.
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
| `maxDistance` | 64.0 | Skip halos past this. |
| `seeThroughWalls` | false | Draw halos through terrain. |
| `heightOffset` | 0.0 | Blocks to raise the halo. It sits at the feet. |
| `awayOverlayEnabled` | true | The on-screen away timer. |

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

## Porting to a new version of the popular cube game

Two places need attention, in this order:

1. `common/src/main/kotlin/gg/earu/afk/client/render/AfkRingsRenderer.kt` and the render hooks in each loader module. Render types, buffer flushing, and the font batch API move around between major versions. `RingGeometry.kt` is pure maths and never changes.
2. `common/src/main/java/gg/earu/afk/mixin/ServerConnectionAccessor.java`. It reads two private vanilla fields. Confirm they still exist before trusting a build:

```
javap -p -cp <minecraft.jar> net.minecraft.server.network.ServerCommonPacketListenerImpl | grep keepAlive
```

On 1.20.1 those fields live on `ServerGamePacketListenerImpl` instead, and the whole payload layer needs rewriting since `CustomPacketPayload` does not exist there.

Everything else (detection, durations, config, codecs, input sampling) is version-agnostic.

