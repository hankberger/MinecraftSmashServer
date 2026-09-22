# Smash Vanilla MVP 0.3.0 — Minecraft Java 26.2–26.3

The new [container network](NETWORK.md) runs a shared lobby and multiple arenas behind Velocity, with global matchmaking and draining for rolling arena updates. **PLAY.cmd remains the standalone gameplay test.**

Public-network players can use ordinary **Java 26.2 or 26.3** with no client mods. The gateway translates 26.3 connections to the 26.2 game servers. The required menu pack automatically selects the appropriate graphics assets. Standalone PLAY.cmd still launches its matching 26.2 client/server pair.

Double-click **PLAY.cmd** in the project root or this folder. It starts an isolated local server and a checksum-verified official Minecraft client, with **no client mods**. Accept the small **Smash menu resource pack** when Minecraft prompts; no manual download is needed. The older `smash_arena/PLAY.cmd` and `TRY VANILLA.cmd` also start this version. Closing the game stops the local server.

You arrive in floating **Mythical Garden** at **0.5 / 101 / -78.5, facing south between the guardian statues**. Falling returns you to the middle. Click the **PLAY fighter** on the quartz podium ahead on the left, right-click the podium, use the **Play** compass, or enter **`/smash join`**. Each opens the same fighter menu: clickable portrait tiles on the left and your rotating character near the center of a timber-and-copper garden pavilion. Choose **1v1** (the default), **4 Player**, or **Practice** beneath the portraits. A party panel directly to the left of the portraits shows who is ready. While matchmaking, an **In Queue** panel shows elapsed wait time and animated searching dots. Use the **Party** item in lobby hotbar slot 9 or **`/smash party`** for party creation, invitations and management. Invitations require acceptance and expire after two minutes. Parties hold up to four players; the leader chooses the mode and can promote or remove members.

Click a portrait to preview **Steve, Alex, Zombie, Skeleton or Villager**, then **PLAY** (solo) or **READY** (party). Choosing a mode opens every party member's own private stage. Browsing never queues you. When everyone is ready, the button becomes **CANCEL** and the menu shows the search status. You stay on the same stage until the match starts. Cancelling a search or choosing another portrait keeps the menu open; **BACK** or **Esc** returns to spawn. Your last fighter is remembered for this server session. The roster shows three larger portraits per row and supports six per page, with arrows appearing automatically when more classes are added.

Two ready players start a duel; four start a free-for-all. Both use three stocks and an eight-minute timer. A two-person party choosing 1v1 fights each other. Smaller free-for-all parties stay together and fill the remaining places from the public queue. Changing a queued fighter withdraws the whole party until that player is ready again. Backing out or a disconnect cancels a pending ready round; a departing leader passes leadership to the next member. Parties remain together after matches. Practice runs a stock match against a sparring dummy and is available when playing alone. `/smash sandbox` gives unlimited training with a stationary dummy on the right. Eliminated players keep watching until results, then everyone returns to the garden.

Each round assigns distinct **P1–P4 colors**, independent of character. A small marker identifies each fighter; the bottom HUD shows everyone's damage and stocks with your own row emphasized. Jump/recovery indicators show your remaining air options. The HUD follows your camera zoom. Jump presses buffer for 150 ms, with 100 ms of grace after walking off an edge; intentional drops, hit stun and recovery do not grant an extra ground jump. Light hits, special hits, blocks and misses have distinct feedback audible from the side camera.

After a duel or free-for-all, a short, steady camera pullback reveals the winning fighter on a private garden podium, with a victory sound, particles, their name and the round's KOs and damage dealt. Draws display all fighters. Replay choices appear as mouse-clickable buttons beside the model after the reveal. **Lobby** or **Escape** returns to spawn. **Rematch** offers the same opponents and fighters, requiring every player's consent within 60 seconds. **Play again** keeps the party and fighter choices, with each party member readying before returning to public matchmaking. **Change fighter** opens the character garden for the party. The leader starts those party replay choices. **RESULTS** in `/smash join` reopens the victory stage until you begin another selection (or ten minutes pass). Cancelling a queued rematch withdraws the whole rematch; original parties remain separate. Results and votes are held in lobby memory, and never keep an arena occupied.

The garden's tree library stands beyond an open arrival courtyard with seats and planted edges. The giant lotus has a separate side garden connected by a branching walk. Battles take place on **Skybound Grove**, a floating Minecraft island with timber platforms, an exposed mineshaft and amethyst geode. See [MAPS.md](MAPS.md) for the layouts and map authoring workflow.

| Input | Action |
|---|---|
| A / D | Full combat speed left / right; no sprint key needed |
| Space | Jump → double jump → class recovery on fresh presses; tap/hold the jumps for short/full height |
| Hold S | Crouch the fighter model; release to stand (Villager dips and bows) |
| S on a platform | Drop through |
| S while falling | Fast fall |
| Shift | Shield; in the air, one brief guard per landing that preserves drift and gravity |
| Left click | Facing-direction light on the ground; neutral aerial with no direction held |
| A / D + left click | Forward light toward that direction, including in the air |
| W + left click | Up light |
| S + left click | Down light; a short chord window takes priority over dropping |
| Hold / release right click | Charge / fire the primary special; quick taps work too, including in the air |
| F | Secondary class special, on the ground or in the air; follows your vanilla Swap Item With Offhand binding |
| S + right click | Alternate secondary-special input; the short chord window takes priority over dropping |
| W + right click | Class recovery; spends remaining air options until landing |
| At a ledge | Hold toward the stage to climb, Space to jump, S to drop |

Holding a primary special visibly winds up its weapon: Steve raises his held pickaxe, Alex braces her held sword, Zombie readies a compact chunk of earth between its hands, and Villager winds back a hand-sized bell. Skeleton keeps its native bow draw. Weapons retain their normal size; the earth and bell follow the model's movement and turning, with the bell rotating about its handle. A small charge meter and one full-charge chime support the animation. Charges fire only on release and remain vulnerable to hits. You can turn to aim, but a grounded charge locks walking, jumping and platform drops. Air charges brake horizontal drift and disable steering while gravity continues. An air jump or recovery discards the charge; Shift cancels with eight ticks before shielding.

The controls stay consistent across the five kits; each rewards a different habit:

| Fighter | Identity and depth | Visual language |
|---|---|---|
| Steve | Mid-range sword tips deal 9%, the blade deals 7%, and point-blank hilt hits deal only 4%. Shovel into pickaxe rewards a confirmed hit. Toss bouncing TNT to force movement or drop an anvil from above. Either player can bat TNT; its fuse keeps running. | Cyan tool arcs, sword/shovel/pickaxe, actual TNT and anvil blocks, fuse smoke and an explosion warning ring |
| Alex | Connected ground clicks chain into Cross Cut and Launch Kick. Any light can instead confirm into Dash Cut. Ground down-light slides; air down-light dives and bounces on a hit. Wind Step retreats with a small hop, creating another approach angle. | Golden sword, paired orange/white arcs, crouched slide, visible dive and rebound |
| Zombie | Heavy claws trade speed for damage. Ground down-light sends a low fissure across the floor. Grave Slam absorbs one weak light during grounded startup; its aerial version must land. Hungry Grab bypasses shields, throws behind Zombie and restores 6% on contact, but is short and punishable on a miss. | Three green claw marks, travelling dirt, ground shockwaves, bite anticipation and small healing hearts |
| Skeleton | Ordinary forward lights fire quick native arrows. Up and air-down lights shoot diagonally; ground down-light retreats with a bone sweep. Scatter Shot covers a short fan, while a full charged bow shot can finish at high damage. | Native arrows with gravity, bow draw pose, three-arrow scatter, bone spin, full-draw critical arrow |
| Villager | Forward lights throw emerald parcels. Plant a growing sapling trap, drop flowerpots from above, or summon a golem to punch at a fixed distance. Parcels can bat the bell from range; ringing the bell grows nearby saplings early. Neutral air reflects incoming arrows. | Emerald parcels, growing sapling/leaves, falling flowerpots, native iron golem with punch animation, gold bell pulses |

See [KITS.md](KITS.md) for every move, damage, setup and counter. Steve's shovel and Alex's lights give a brief tool glint and soft chime when a follow-up is available. The window lasts 400 ms plus hit pause; shielding and getting hit still prevent cancels. Hit confirms never refresh aerial movement options. Blocking ordinary melee recoils the attacker and leaves recovery; ranged attacks do not freeze or recoil their distant owner. No combat chat is added.

Neutral aerials cover a short area around the fighter and push opponents outward: Steve's Sword Spin, Alex's fast Twisting Cut, Zombie's heavier Flailing Claws, Skeleton's Bone Spin, and Villager's Parcel Twirl. Grounded directionless clicks remain ordinary forward attacks. W/S take priority over A/D when aiming a light. Forward attacks control space, up attacks catch fighters overhead, and down attacks cover low or descending targets; Zombie's centered aerial stomp can spike an opponent below.

Releasing A/D in the air preserves horizontal drift with light drag, so a running jump can carry into a directionless nair. Hold the opposite direction to brake or reverse; landing restores the quick ground stop. Holding any primary charge brakes that drift without suspending gravity.

Descending near either end of the main deck automatically catches the ledge, including after a spent recovery. Hold toward the stage to climb, press Space to jump off, or hold S to drop/bypass a catch. Grabs cannot cancel hit stun or a committed slam. The first grab grants 0.5 seconds of protection; leaving ends it. Hanging lasts at most two seconds, recatching has a 0.6-second delay, and only two grabs are available before landing. A second grab has no protection. A new arrival displaces an existing hanger. Hanging and ledge jumps never refill air resources; climbing onto the deck or landing does. The side blast zones sit 22 blocks beyond the deck and the bottom zone is 28 blocks below it, leaving space to recover without changing jump height, gravity or recovery strength.

Steve, Alex and Zombie's recoveries attack during the rise; Skeleton and Villager focus on escape. Fresh Space presses use the ground jump, then the air jump, then the existing class recovery. Holding Space never chains actions, and holding W does not skip the double jump. Walking off an edge uses whichever air options remain. Recovery spends the remaining jump/recovery budget until landing; further presses cannot add lift while it is spent. W + right-click remains a direct recovery shortcut, including before using the double jump. Jump heights, recovery strength, gravity and air budgets are unchanged. Space + right-click remains an ordinary special, so holding a full jump does not interfere with bow or bell use.

Attacks buffer for up to 150 ms near the end of an action or during shield release. One pending attack remembers its aim and facing; another click replaces it, getting hit cancels it, and stale clicks expire. A buffered air attack that starts after landing uses its grounded counterpart. Air shield shares ground shield energy, drains faster, lasts at most 350 ms excluding hit pause, and cannot restart until landing. It neither refreshes nor spends a jump or recovery, and cannot cancel hit stun.

Arena jumps clear the next platform with a quicker rise and heavier fall. Melee up lights sweep about 3.15–3.45 blocks above the fighter's feet, with the visible arc matching the hit area. They catch nearby jumpers; reaching an opponent on the platform four blocks above requires jumping first. Skeleton's Sky Shot follows an upward arc and stops at terrain. Damage increases finishing launch speed and stun, so powerful hits can send opponents through the side or upper blast zones before they can recover. Strong launches leave a spark trail; crossing a blast zone plays an expanding burst and KO sound, then spends a stock and starts the protected respawn. There is no automatic KO at a fixed percentage: move choice, weight and position still matter. Quick arrows, partial bow shots, parcels and fissures have limited launch; fully drawn bow shots can finish.

Melee strikes use curved sweeps with a brief, tapered blade trail describing their reach. Forward, overhead, low and downward attacks occupy different spaces; the corners of the old rectangular hit areas no longer deal damage. Attacks keep their facing through startup and contact, while movement remains available. Bow drawing can still turn before release. Contact sparks appear where the strike connects. Heavy hits hold their participants for 100 ms, and strong light launches for 50 ms, without shortening the following stun or recovery. Ordinary light hits and misses do not pause movement.

Combat hit positions account for the stock client's three-tick entity interpolation; relative movement is sampled during active melee strikes to catch fast crossings. This corrects the built-in display offset, not network latency. There is no client-side prediction or per-player latency rewind. Strike trails use native, short-lived display packets and work without a new resource pack.

Use **first-person perspective (F5)** and about **70 FOV** for the intended stage and battle views. The side camera gently frames your fighter together with active opponents, including offstage. Small movements and short hops leave it steady; larger moves ease into a pan with vanilla display interpolation. When fighters spread apart, the camera eases outward from the default distance of 18, capped at 26, then closes in again. Distant finishing launches do not pull opponents' cameras away from the fight. Heading and FOV stay fixed. The timer and bottom HUD ride with the camera. `/smash camera 18` restores the default minimum distance (14–40); increase it for more room. Minecraft controls perspective, FOV and GUI scale locally. The fighter picker uses a centered native menu with custom portrait artwork, a party panel directly on its left and the live character stage on its right. The resource pack keeps the stage sharp and undimmed. **GUI Scale: Auto** is recommended for fullscreen; a fixed low scale keeps the menu small on larger displays, but it stays vertically centered. Each class currently has its Default appearance. Skin browsing and mouse-drag rotation remain future work. See [UI_PACK.md](UI_PACK.md) for pack delivery and development.

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

`PLAY.cmd` checks for an existing local session before building. Close its earlier Minecraft window before relaunching to load new changes. Running servers use immutable JAR copies in `runtime/server-libs`, so ordinary builds no longer overwrite their open files. For an independent bounded launch test, use `PLAY.cmd --runtime-dir evidence/play-smoke --port 25577 --smoke-seconds 35` from this folder.

Developer checks in PowerShell:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25'
./gradlew.bat --gradle-user-home ../smash_arena/.gradle-user-home build runClientGameTest -PdedicatedTests
./gradlew.bat --gradle-user-home ../smash_arena/.gradle-user-home runClientGameTest -PdedicatedTests -PpackedTests
./gradlew.bat --gradle-user-home ../smash_arena/.gradle-user-home runClientGameTest -PdedicatedTests -PcombatTests
./gradlew.bat --gradle-user-home ../smash_arena/.gradle-user-home runClientGameTest -PdedicatedTests -PcombatTests -PmovementTests
./gradlew.bat --gradle-user-home ../smash_arena/.gradle-user-home runClientGameTest -PdedicatedTests -PledgeTests
./gradlew.bat --gradle-user-home ../smash_arena/.gradle-user-home runClientGameTest -PdedicatedTests -PcameraTests
./gradlew.bat --gradle-user-home ../smash_arena/.gradle-user-home runClientGameTest -PdedicatedTests -PentryTests
./gradlew.bat --gradle-user-home ../smash_arena/.gradle-user-home runClientGameTest -PdedicatedTests -PchargeTests
./gradlew.bat --gradle-user-home ../smash_arena/.gradle-user-home runClientGameTest -PdedicatedTests -PcombatTests -PmovementTests -PkitTests
C:/Python312/python.exe launch_local.py --players 4 --smoke-seconds 65
```

See [MIGRATION.md](MIGRATION.md) for the backup, port scope, validation and remaining limits. [REPORT.md](REPORT.md) retains the historical v0.1.0 feasibility findings, including the measured local movement delay. Remote gameplay feel and public capacity still need real playtests.
