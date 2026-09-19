# Smash Vanilla MVP 0.3.0 — Minecraft Java 26.2

The new [container network](NETWORK.md) runs a shared lobby and multiple arenas behind Velocity, with global matchmaking and draining for rolling arena updates. **PLAY.cmd remains the standalone gameplay test.**

Double-click **PLAY.cmd** in the project root or this folder. It starts an isolated local server and a checksum-verified official Minecraft client, with **no client mods**. Accept the small **Smash menu resource pack** when Minecraft prompts; no manual download is needed. The older `smash_arena/PLAY.cmd` and `TRY VANILLA.cmd` also start this version. Closing the game stops the local server.

You arrive in floating **Mythical Garden** at **0.5 / 101 / -78.5, facing south between the guardian statues**. Falling returns you to the middle. Click the **PLAY fighter** on the quartz podium ahead on the left, right-click the podium, use the **Play** compass, or enter **`/smash join`**. Each opens the same fighter menu: clickable portrait tiles on the left and your rotating character near the center of a timber-and-copper garden pavilion. Choose **1v1**, **4 Player**, or **Practice** beneath the portraits. A separate party sidebar on the right shows who is ready. While matchmaking, an **In Queue** panel shows elapsed wait time and animated searching dots. Use the **Party** item in lobby hotbar slot 9 or **`/smash party`** for party creation, invitations and management. Invitations require acceptance and expire after two minutes. Parties hold up to four players; the leader chooses the mode and can promote or remove members.

Click a portrait to preview **Steve, Alex, Zombie, Skeleton or Villager**, then **PLAY** (solo) or **READY** (party). Choosing a mode opens every party member's own private stage. Browsing never queues you. When everyone is ready, the button becomes **CANCEL** and the menu shows the search status. You stay on the same stage until the match starts. Cancelling a search or choosing another portrait keeps the menu open; **BACK** or **Esc** returns to spawn. Your last fighter is remembered for this server session. The roster shows four portraits per row and supports eight per page, with arrows appearing automatically when more classes are added.

Two ready players start a duel; four start a free-for-all. Both use three stocks and an eight-minute timer. A two-person party choosing 1v1 fights each other. Smaller free-for-all parties stay together and fill the remaining places from the public queue. Changing a queued fighter withdraws the whole party until that player is ready again. Backing out or a disconnect cancels a pending ready round; a departing leader passes leadership to the next member. Parties remain together after matches. Practice runs a stock match against a sparring dummy and is available when playing alone. `/smash sandbox` gives unlimited training with a stationary dummy on the right. Eliminated players keep watching until results, then everyone returns to the garden.

Each round assigns distinct **P1–P4 colors**, independent of character. A small marker identifies each fighter; the bottom HUD shows everyone's damage and stocks with your own row emphasized. Jump/recovery indicators show your remaining air options. The HUD follows your camera zoom. Jump presses buffer for 150 ms, with 100 ms of grace after walking off an edge; intentional drops, hit stun and recovery do not grant an extra ground jump. Light hits, special hits, blocks and misses have distinct feedback audible from the side camera.

After a duel or free-for-all, a short, steady camera pullback reveals the winning fighter on a private garden podium, with a victory sound, particles, their name and the round's KOs and damage dealt. Draws display all fighters. Replay choices appear beside the model after the reveal: **scroll or press 1–3**, then **right-click**; **Q** returns to spawn. **Rematch** offers the same opponents and fighters, requiring every player's consent within 60 seconds. **Play again** keeps the party and fighter choices, with each party member readying before returning to public matchmaking. **Change fighter** opens the character garden for the party. The leader starts those party replay choices. **RESULTS** in `/smash join` reopens the victory stage until you begin another selection (or ten minutes pass). Cancelling a queued rematch withdraws the whole rematch; original parties remain separate. Results and votes are held in lobby memory, and never keep an arena occupied.

The garden's tree library stands beyond an open arrival courtyard with seats and planted edges. The giant lotus has a separate side garden connected by a branching walk. Battles take place on **Skybound Grove**, a floating Minecraft island with timber platforms, an exposed mineshaft and amethyst geode. See [MAPS.md](MAPS.md) for the layouts and map authoring workflow.

| Input | Action |
|---|---|
| A / D | Full combat speed left / right; no sprint key needed |
| Space | Tap for a short hop, hold for a full jump; press again for one air jump |
| S on a platform | Drop through |
| S while falling | Fast fall |
| Shift | Shield; in the air, one brief guard per landing that preserves drift and gravity |
| Left click | Facing-direction light on the ground; neutral aerial with no direction held |
| A / D + left click | Forward light toward that direction, including in the air |
| W + left click | Up light |
| S + left click | Down light; a short chord window takes priority over dropping |
| Right click | Class special, also usable in the air |
| W + right click | Class recovery; spends remaining air options until landing |
| W + a fresh Space press in the air | The same recovery; ground W + Space still jumps |

Skeleton holds right click to draw, slows while drawing, and releases to fire a normal arrow with charge-dependent speed and gravity. Villager throws a bell that lands and arms; right-click again to ring it, or let it ring automatically. Zombie uses a ground shockwave or an aerial Grave Slam. Steve has sword/tool strikes and a pickaxe special; Alex is faster and uses a dash strike. Up/down lights and recovery motion differ across the roster.

Neutral aerials cover a short area around the fighter and push opponents outward: Steve's Sword Spin, Alex's fast Twisting Cut, Zombie's heavier Flailing Claws, Skeleton's Bone Spin, and Villager's Parcel Twirl. Grounded directionless clicks remain ordinary forward attacks. W/S take priority over A/D when aiming a light. Forward attacks control space, up attacks catch fighters overhead, and down attacks cover low or descending targets; Zombie's centered aerial stomp can spike an opponent below.

Steve, Alex and Zombie's recoveries attack during the rise; Skeleton and Villager focus on escape. Recovery spends the remaining jump/recovery budget until landing. The jump shortcut captures W when Space is freshly pressed in the air: holding Space from takeoff, or adding W afterward, does not trigger it. Release W for a normal double jump. Space + right-click remains an ordinary special, so holding a full jump does not interfere with bow or bell use.

Attacks buffer for up to 150 ms near the end of an action or during shield release. One pending attack remembers its aim and facing; another click replaces it, getting hit cancels it, and stale clicks expire. A buffered air attack that starts after landing uses its grounded counterpart. Air shield shares ground shield energy, drains faster, lasts at most 350 ms excluding hit pause, and cannot restart until landing. It neither refreshes nor spends a jump or recovery, and cannot cancel hit stun.

Arena jumps clear the next platform with a quicker rise and heavier fall. Up lights sweep visibly overhead and can catch a fighter on the platform above. Damage increases melee launch speed and stun, so powerful hits can send opponents through the side or upper blast zones before they can recover. Strong launches leave a spark trail; crossing a blast zone plays an expanding burst and KO sound, then spends a stock and starts the protected respawn. There is no automatic KO at a fixed percentage: move choice, weight and position still matter. Arrows remain a weaker spacing tool.

Melee strikes use curved sweeps with a brief, tapered blade trail describing their reach. Forward, overhead, low and downward attacks occupy different spaces; the corners of the old rectangular hit areas no longer deal damage. Attacks keep their facing through startup and contact, while movement remains available. Bow drawing can still turn before release. Contact sparks appear where the strike connects. Heavy hits hold their participants for 100 ms, and strong light launches for 50 ms, without shortening the following stun or recovery. Ordinary light hits and misses do not pause movement.

Combat hit positions account for the stock client's three-tick entity interpolation; relative movement is sampled during active melee strikes to catch fast crossings. This corrects the built-in display offset, not network latency. There is no client-side prediction or per-player latency rewind. Strike trails use native, short-lived display packets and work without a new resource pack.

Use **first-person perspective (F5)** and about **70 FOV** for the intended stage and battle views. `/smash camera 24` sets the battle-camera distance (18–40); increase it for more room. Minecraft controls perspective and FOV locally, so the server cannot enforce them. GUI scale 2 fits the current HUD well. The fighter picker uses a vanilla dialog with a free mouse cursor and clickable portrait artwork on the left, with real entities and scenery behind it. The resource pack keeps the live stage sharp and undimmed. Minecraft retains its native dialog warning icon and footer; GUI scale still affects the layout. Each class currently has its Default appearance. Skin browsing and mouse-drag rotation remain future work. See [UI_PACK.md](UI_PACK.md) for pack delivery and development.

| Command | Purpose |
|---|---|
| `/smash join` | Open fighters, modes and party readiness |
| `/smash party` | Open party management and invitations |
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

The host uses Fabric Loader **0.19.5**, Fabric API **0.159.0+26.2**, and the server JAR. This is a server mod with vanilla clients, not a Paper plugin. See [SERVER.md](SERVER.md) for installation. The resource pack contains menu artwork and fonts; no client gameplay mixins, custom entity types or Smash client network channels are included. The test harness loads common initialization in both environments, but gameplay acts only on the server.

Developer checks in PowerShell:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25'
./gradlew.bat --gradle-user-home ../smash_arena/.gradle-user-home build runClientGameTest -PdedicatedTests
./gradlew.bat --gradle-user-home ../smash_arena/.gradle-user-home runClientGameTest -PdedicatedTests -PpackedTests
./gradlew.bat --gradle-user-home ../smash_arena/.gradle-user-home runClientGameTest -PdedicatedTests -PcombatTests
./gradlew.bat --gradle-user-home ../smash_arena/.gradle-user-home runClientGameTest -PdedicatedTests -PcombatTests -PmovementTests
C:/Python312/python.exe launch_local.py --players 4 --smoke-seconds 65
```

See [MIGRATION.md](MIGRATION.md) for the backup, port scope, validation and remaining limits. [REPORT.md](REPORT.md) retains the historical v0.1.0 feasibility findings, including the measured local movement delay. Remote gameplay feel and public capacity still need real playtests.
