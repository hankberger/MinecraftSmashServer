# Smash Vanilla MVP 0.3.0 — Minecraft Java 26.2

The new [container network](NETWORK.md) runs a shared lobby and multiple arenas behind Velocity, with global matchmaking and draining for rolling arena updates. **PLAY.cmd remains the standalone gameplay test.**

Double-click **PLAY.cmd** in the project root or this folder. It starts an isolated local server and a checksum-verified official Minecraft client, with **no client mods or resource pack**. The older `smash_arena/PLAY.cmd` and `TRY VANILLA.cmd` also start this version. Closing the game stops the local server.

You arrive in floating **Mythical Garden** at **0.549 / 101 / -105.631, facing south**. Falling returns you to the middle. Walk up to the **PLAY fighter** on the small quartz podium just ahead on the left and click him, or right-click his podium, to open the match menu. This works with an empty hand. The **Play** compass and **`/smash join`** open the same menu. Choose **1v1**, **Free-for-all** (four players), or **Practice**. Party controls occupy the separate blue top row; game modes occupy the bottom row. Hover a mode for its name and player count, or hover the party head for the roster. Party actions let you create a party, invite players in the lobby, and accept invitations. Invitations require acceptance and expire after two minutes. Parties hold up to four players; the leader chooses the mode and can promote or remove members.

Choosing a mode sends everyone in the party to their own **3D character garden**. Five miniature fighters form a roster on the left; the selected fighter rotates on a larger platform on the right. **Aim with the mouse and click a fighter** to browse, then click the separate green **READY** target. **Scroll/1–5** and **right-click in empty space** remain shortcuts; **Q** returns to the match menu (default Minecraft bindings). Browsing never queues you. A waiting screen shows who is ready and lets you **Change fighter**. The entire party joins matchmaking only after everyone is ready. Holding the opening click cannot also confirm.

Two ready players start a duel; four start a free-for-all. Both use three stocks and an eight-minute timer. A two-person party choosing 1v1 fights each other. Smaller free-for-all parties stay together and fill the remaining places from the public queue. Changing a queued fighter withdraws the whole party until that player is ready again. Backing out or a disconnect cancels a pending ready round; a departing leader passes leadership to the next member. Parties remain together after matches. Practice runs a stock match against a sparring dummy and is available when playing alone. `/smash sandbox` gives unlimited training with a stationary dummy on the right. Eliminated players keep watching until results, then everyone returns to the garden.

Each round assigns distinct **P1–P4 colors**, independent of character. A small marker identifies each fighter; the bottom HUD shows everyone's damage and stocks with your own row emphasized. Jump/recovery indicators show your remaining air options. The HUD follows your camera zoom. Jump presses buffer for 150 ms, with 100 ms of grace after walking off an edge; intentional drops, hit stun and recovery do not grant an extra ground jump. Light hits, special hits, blocks and misses have distinct feedback audible from the side camera.

After a duel or free-for-all, a short camera sweep reveals the winning fighter on a private garden podium, with a victory sound, particles, their name and the round's KOs and damage dealt. Draws display all fighters. Replay choices appear beside the model after the reveal: **scroll or press 1–3**, then **right-click**; **Q** returns to spawn. **Rematch** offers the same opponents and fighters, requiring every player's consent within 60 seconds. **Play again** keeps the party and fighter choices, with each party member readying before returning to public matchmaking. **Change fighter** opens the character garden for the party. The leader starts those party replay choices. **Last match** in `/smash join` reopens the victory stage until you begin another selection (or ten minutes pass). Cancelling a queued rematch withdraws the whole rematch; original parties remain separate. Results and votes are held in lobby memory, and never keep an arena occupied.

The garden's tree library stands beyond an open arrival courtyard with seats and planted edges. The giant lotus has a separate side garden connected by a branching walk. Battles take place on **Skybound Grove**, a floating Minecraft island with timber platforms, an exposed mineshaft and amethyst geode. See [MAPS.md](MAPS.md) for the layouts and map authoring workflow.

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

Use **first-person perspective (F5)** and about **70 FOV** for the intended stage and battle views. `/smash camera 24` sets the battle-camera distance (18–40); increase it for more room. Minecraft controls perspective and FOV locally, so the server cannot enforce them. GUI scale 2 fits the current HUD well. The character picker uses real vanilla entities and world scenery. Mouse selection aims the first-person view at the fighters; it is not a free desktop cursor over a fixed view. The compact match menu uses Minecraft's normal mouse cursor. Esc opens Minecraft's pause menu; Q or `/smash leave` exits the stage. Each class currently has its Default appearance. Custom portraits, skin browsing and mouse-drag rotation remain future work.

| Command | Purpose |
|---|---|
| `/smash join` | Open modes, party roster and invitations |
| `/smash duel` / `/smash ffa` | Leader shortcuts to mode selection |
| `/smash party create` | Create a party |
| `/smash party invite NAME` | Invite someone in the lobby |
| `/smash party accept NAME` | Accept an invitation from that leader |
| `/smash party leave` | Leave your party |
| `/smash practice` | Pick a class for a stock match against the dummy |
| `/smash sandbox` | Pick a class for unlimited training |
| `/smash leave` | Leave queue/match and return to the garden |
| `/smash unqueue` | Cancel queue entry |
| `/smash reset` | Reset sandbox; spend a stock in a normal round |
| `/smash dummy` / `/smash dummy spar` | Stationary / attacking sandbox dummy |
| `/smash dummy percent 100` | Set sandbox dummy damage |

Standalone mode hosts **one battle at a time**. New players can wait in the garden queue during a match; a complete practice or public roster can use the next available arena. The Docker network hosts independent simultaneous matches and assigns training to a free worker. Parties and invitations are currently held in lobby memory, so restarting the lobby clears them. Ranked matchmaking, private-match options, persistent progression, cosmetics and crossplay are future work. The server owns inventories and arrivals: install it in a dedicated minigame server, not an existing survival server.

The local launcher binds to `127.0.0.1:25576` and uses local test identities. It requires Python at `C:\Python312\python.exe`, Java 25 (`JAVA_HOME`, otherwise `C:\Program Files\Java\jdk-25`), and this workspace's sibling build cache. It verifies client and library SHA-1 values against Mojang's cached manifest. Its world is `runtime/server/smash-vanilla-mvp`; it does not open the modded PLAY save or old feasibility world. This launcher is a developer convenience. Public players would use their usual launcher and a server address.

The host uses Fabric Loader **0.19.5**, Fabric API **0.159.0+26.2**, and the server JAR. This is a server mod with vanilla clients, not a Paper plugin. See [SERVER.md](SERVER.md) for installation. No client gameplay mixins, custom entity types, resource pack or Smash network channels are included. The test harness loads common initialization in both environments, but gameplay acts only on the server.

Developer checks in PowerShell:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25'
./gradlew.bat --gradle-user-home ../smash_arena/.gradle-user-home build runClientGameTest -PdedicatedTests
C:/Python312/python.exe launch_local.py --players 4 --smoke-seconds 65
```

See [MIGRATION.md](MIGRATION.md) for the backup, port scope, validation and remaining limits. [REPORT.md](REPORT.md) retains the historical v0.1.0 feasibility findings, including the measured local movement delay. Remote gameplay feel and public capacity still need real playtests.
