# Smash Vanilla MVP 0.3.0 — Minecraft Java 26.2

The new [container network](NETWORK.md) runs a shared lobby and multiple arenas behind Velocity, with global matchmaking and draining for rolling arena updates. **PLAY.cmd remains the standalone gameplay test.**

Double-click **PLAY.cmd** in the project root or this folder. It starts an isolated local server and a checksum-verified official Minecraft client, with **no client mods or resource pack**. The older `smash_arena/PLAY.cmd` and `TRY VANILLA.cmd` also start this version. Closing the game stops the local server.

You arrive in floating **Mythical Garden** at **0.549 / 101 / -105.631, facing south**. Falling returns you to the middle. Right-click the **Play** compass or **Practice** hotbar item to open the five-character menu. Hover for a name and click to choose. You enter the queue only after selecting; Esc cancels.

Four queued players start a three-stock, eight-minute match. Practice runs a stock match against a sparring dummy. `/smash sandbox` gives unlimited training with a stationary dummy on the right. Eliminated players keep watching until results, then everyone returns to the garden.

| Input | Action |
|---|---|
| A / D | Move left / right |
| Sprint key while moving | Faster movement |
| Space | Jump; press again for one air jump |
| S on a platform | Drop through |
| S while falling | Fast fall |
| Shift on the ground | Shield; holding and hits spend shield energy |
| Left click | Light attack |
| W + left click | Up light |
| S + left click | Down light; a short chord window takes priority over dropping |
| Right click | Class special, also usable in the air |
| W + right click | Class recovery; spends remaining air options until landing |

Skeleton holds right click to draw, slows while drawing, and releases to fire a normal arrow with charge-dependent speed and gravity. Villager throws a bell that lands and arms; right-click again to ring it, or let it ring automatically. Zombie uses a ground shockwave or an aerial Grave Slam. Steve has sword/tool strikes and a pickaxe special; Alex is faster and uses a dash strike. Up/down lights and recovery motion differ across the roster.

Arena jumps clear the next platform with a quicker rise and heavier fall. Up lights sweep visibly overhead and can catch a fighter on the platform above. Damage increases melee launch speed and stun, so powerful hits can send opponents through the side or upper blast zones before they can recover. Strong launches leave a spark trail; crossing a blast zone plays an expanding burst and KO sound, then spends a stock and starts the protected respawn. There is no automatic KO at a fixed percentage: move choice, weight and position still matter. Arrows remain a weaker spacing tool.

Use **first-person perspective (F5)** and about **70 FOV** for the intended battle view. `/smash camera 24` sets the side-camera distance (18–40); increase it for more room. Minecraft controls perspective and FOV locally, so the server cannot enforce them. GUI scale 2 fits the current HUD well. Native menus, particles, item animations and the action bar replace the modded UI; custom portraits, cosmetic browsing and the live 3D picker are deferred.

| Command | Purpose |
|---|---|
| `/smash join` | Pick a class, then queue |
| `/smash practice` | Pick a class for a stock match against the dummy |
| `/smash sandbox` | Pick a class for unlimited training |
| `/smash leave` | Leave queue/match and return to the garden |
| `/smash unqueue` | Cancel queue entry |
| `/smash reset` | Reset sandbox; spend a stock in a normal round |
| `/smash dummy` / `/smash dummy spar` | Stationary / attacking sandbox dummy |
| `/smash dummy percent 100` | Set sandbox dummy damage |

Standalone mode hosts **one battle at a time**. New players can wait in the garden queue during a match. Standalone practice requires an empty arena and queue. The Docker network hosts independent simultaneous matches and assigns training to a free worker. Parties, persistent progression, cosmetics and crossplay are future work. The server owns inventories and arrivals: install it in a dedicated minigame server, not an existing survival server.

The local launcher binds to `127.0.0.1:25576` and uses local test identities. It requires Python at `C:\Python312\python.exe`, Java 25 (`JAVA_HOME`, otherwise `C:\Program Files\Java\jdk-25`), and this workspace's sibling build cache. It verifies client and library SHA-1 values against Mojang's cached manifest. Its world is `runtime/server/smash-vanilla-mvp`; it does not open the modded PLAY save or old feasibility world. This launcher is a developer convenience. Public players would use their usual launcher and a server address.

The host uses Fabric Loader **0.19.5**, Fabric API **0.159.0+26.2**, and the server JAR. This is a server mod with vanilla clients, not a Paper plugin. See [SERVER.md](SERVER.md) for installation. No client gameplay mixins, custom entity types, resource pack or Smash network channels are included. The test harness loads common initialization in both environments, but gameplay acts only on the server.

Developer checks in PowerShell:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25'
./gradlew.bat --gradle-user-home ../smash_arena/.gradle-user-home build runClientGameTest -PdedicatedTests
C:/Python312/python.exe launch_local.py --players 4 --smoke-seconds 65
```

See [MIGRATION.md](MIGRATION.md) for the backup, port scope, validation and remaining limits. [REPORT.md](REPORT.md) retains the historical v0.1.0 feasibility findings, including the measured local movement delay. Remote gameplay feel and public capacity still need real playtests.
