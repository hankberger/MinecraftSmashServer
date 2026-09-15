# Vanilla MVP migration — 13 September 2026

Vanilla Java clients are now the development target. The host remains custom Fabric server code. No client mod, custom client entity registration or Smash payload is required. The newer fighter menu uses a small required server resource pack, offered automatically by Minecraft; see [UI_PACK.md](UI_PACK.md). The port evidence below predates that menu.

## Modded checkpoint

Before redirecting launchers, the complete relevant modded source, map assets, launch scripts, releases, screenshots and current PLAY save were archived to:

`backups/smash-modded-0.14.1-2026-09-13_123420.zip`

- 931 files; 546,786,473 bytes.
- SHA-256: `dc3224aa8b9074e0d371e569894fce193dbc447b54b2e8c9400a293ada1f5c72`.
- Every archived file was compared with its source hash. `BACKUP_MANIFEST.json` is inside the ZIP; `backups/MODDED_BACKUP.json` records the verified checkpoint.
- The save is `smash_arena/run/saves/Smash Arena Prototype`. Generated build caches and transient logs/locks were excluded; release binaries were included.
- The original source and save also remain in place. `smash_arena/PLAY-MODDED.cmd` runs the preserved `PLAY.ps1`. To restore the exact checkpoint, extract the archive into a separate directory first; do not overwrite an open game/save. Its original launchers are included.

Root `PLAY.cmd`, `smash_arena/PLAY.cmd`, `smash_vanilla/PLAY.cmd` and `TRY VANILLA.cmd` now all start the vanilla MVP. The vanilla save uses `smash_vanilla/runtime/server/smash-vanilla-mvp`, separate from both the modded save and the earlier feasibility world.

## Ported

| Area | MVP behavior |
|---|---|
| Maps | Original floating Mythical Garden and original arena, separate dimensions. Exact south-facing arrival and fall rescue to the middle; rescue preserves queue entry. |
| Selection | Native five-icon menu: Steve, Alex, Zombie, Skeleton, Villager. Select before queuing; cancel leaves no queue entry. Play/Practice hotbar shortcuts. |
| Match flow | FIFO four-player queue, five-second countdown, three stocks, eight-minute limit, tie handling, results and lobby return. Eliminated players retain their camera. Disconnect/leave cleanup and countdown cancellation preserve surviving queued humans. |
| Training | Practice stock match with a sparring dummy; unlimited sandbox with passive/sparring toggle, reset and dummy percentage controls. Dummy starts on the right. |
| Shared combat | Damage percentages, hitstun, knockback, weight, short attack buffering, fast lights, up/down/aerial lights, shield energy, guard breaks, jump/double jump, sprint, drops, fast fall and limited recoveries. |
| Steve | Balanced tool attacks, pickaxe special with stronger tip, vertical sword recovery. |
| Alex | Fast movement/light attacks, ground or aerial dash special, angled recovery and limited aerial burst. |
| Zombie | Heavy/slower attacks, ground shockwave or aerial Grave Slam, aerial down spike and rising recovery. |
| Skeleton | Native bow hold/release, draw slowdown, charge-dependent arrow speed/damage, gravity/drag, bone lights and recovery. Arrow rendering uses the ordinary arrow entity. |
| Villager | Bell toss, landing/arming, manual/automatic ring, counterplay through destroying bells, no short airborne expiry, and floating recovery. The held placeholder prop is gone. |
| Respawns | Actual KOs float in from above center with flashing protection, then a brief grace period. Initial match entry and GO do not grant protection. Attacking or guarding ends landing protection. |
| Presentation | Vanilla mannequins for Steve/Alex, native mobs for the others, native shields/particles, action-bar damage/stocks/names and a transparent world timer. Routine game UI uses the screen rather than chat. |

The class/combat/queue rules were copied into this project and adapted to server-owned actors. Runtime gameplay has no dependency on the archived project; only the local development launcher reuses its download cache.

## Deliberately deferred

- Exact modded picker, live 3D preview, skin browsing, portrait HUD and custom animations. The MVP uses ordinary inventory icons, built-in entity animations and the action bar. Minecraft chrome and occasional native recipe toasts remain.
- Camera enforcement: third-person front looks away from the stage. First person / roughly 70 FOV is required for the intended view. `/smash camera 18..40` adjusts distance; a server cannot lock unreported local perspective/FOV settings.
- Local movement prediction. The v0.1.0 probe measured 149–150 ms using a coarse local 20 Hz client-tick method. No new internet latency or human PvP claim is made by this port.
- Multiple simultaneous arenas, parties, progression, persistence, polished onboarding, public capacity/abuse testing and crossplay. There is one battle at a time; queued players can wait in the garden.
- Paper migration. Vanilla-client compatibility is the delivered goal; this artifact uses Fabric on the server.

## Verification

The build and 41 retained rule tests passed. The dedicated-server client GameTest suite passed native menu entry/cancel, queue/fall rescue, initial countdown without flashing, five class light attacks and specials, movement/double jump/recoveries, real bow hold/release and arrow type, aerial bell persistence/damage, Grave Slam landing damage, shield absorption, platform drop/down chord, protected KO descent, inventory-drop rejection, results/lobby return, disconnect cleanup and reconnect. It saved 22 screenshots. This test uses Fabric's input/render instrumentation with the original Smash client absent; it is not an unmodified-client process.

A separate smoke test used the packaged MVP JAR and four official Mojang client processes with checksum-verified libraries, no Fabric/client mod classpath, and four `brand=vanilla` reports. It reached an active four-human round with four actors and four cameras. Automatic class choices exercised queue entry there; native menu clicks were checked by the GameTest suite. This is local compatibility evidence, not a human multiplayer or capacity benchmark.

A second official-client smoke test reopened the saved world and entered sandbox successfully after the four-player server stopped. Both bounded runs closed their own clients and server. The packaged ZIP was checked for corruption, exact JAR contents and checksums; it contains only the Smash server mod and Fabric API as executable dependencies.

Evidence is retained under `evidence/mvp-0.2.0`. Original feasibility evidence and `REPORT.md` remain historical. On this Windows host, Minecraft/OSHI emitted performance-counter/system-information warnings during startup, and the initial garden build caused a short catch-up warning. Both test types continued to completion; no registry changes or blanket log suppression were made.
