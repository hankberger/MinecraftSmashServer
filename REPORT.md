**Vanilla Java feasibility report — 13 September 2026**

**Historical report for v0.1.0.** The user approved the vanilla direction after this experiment. Active development is now the [v0.2.0 MVP](README.md); see [MIGRATION.md](MIGRATION.md) for what has since been ported and what remains limited. Statements below about missing classes, garden and match flow describe the earlier experiment.

**Recommendation: continue with vanilla Java clients as the public distribution target.** Joining through a server address removes the mod-install step and makes an invitation much easier to act on. That is a strong fit for the audience goal. It does not establish that the game will go viral, or that this camera/control approach will feel good enough over the internet.

I built a separate working experiment in `smash_vanilla`. The server contains custom Fabric code; players use ordinary Minecraft Java **26.2**. No client mod or resource pack is needed for this experiment. This is a compatibility prototype, not a completed migration of the existing game to Paper.

**What works now**

| Area | Result |
|---|---|
| Unmodified-client connection | The official Mojang client connected and entered practice. Its client JAR and libraries matched Mojang's manifest checksums; the server received `brand=vanilla`. No Fabric or Smash code was on that client's classpath. |
| Side camera | A vanilla camera packet attaches the view to an invisible armor stand. The real player remains outside the stage and controls a separate visible fighter. The test caught and fixed a stale camera entity after empty-server chunk unloading. |
| Movement | A/D, jump, double jump, S platform drop and fast fall work through ordinary key-state packets. Sprint is wired into movement. Movement and collision decisions stay on the server. |
| Basic combat | Light hits, a short heavy windup, aerial heavy attacks, damage percentages, knockback, ground shielding, and directional launch variants are implemented. Input tests confirmed light damage and airborne heavy activation. |
| Ring-outs | Leaving the arena removes a stock and returns the fighter from above the center with brief flashing protection. Match entry has no protection flash. Practice has unlimited retries. |
| Five appearances | Steve and Alex use vanilla mannequins with their built-in skins. Zombie, Skeleton and Villager use their ordinary mob entities. All five selections rendered successfully in the instrumented client. |
| Selection and queue entry | A native inventory menu offers five icons with hover names. Selection happens before queue entry. Canceling or disconnecting does not leave a queued player behind. |
| Four-client match start | Four separate official Minecraft processes connected with distinct identities and `brand=vanilla`. All four received fighter assignments and the server started a four-player match. This checks connection and queue flow, not human PvP quality or server capacity. |
| Minimal battle UI | Name, class, damage and stocks appear in the action bar. Routine prototype UI does not use chat messages. The hidden input item uses Minecraft's existing air model, so the hand-held prop needs no resource pack. |

![Vanilla rendering of the side-view arena](A:/Coding/Projects/minecraf/smash_vanilla/evidence/screenshots/0004_04b-clean-arena.png)

The image is from the Minecraft test harness using vanilla rendering and input behavior, with no original Smash client code. It is not a screenshot from the separate stock-client connection test.

**What needs rethinking**

| Issue | Evidence and proposed direction |
|---|---|
| Movement response — highest priority | Six local instrumented samples measured **149–150 ms** from the start of a client tick with the movement key held to the first client entity motion. The earlier implementation measured **199–200 ms**. Moving simulation before entity replication and requesting a tracker update each tick saved about one tick in this setup. This is a coarse, 20 Hz, TCP-loopback probe; it excludes test-harness wait overhead but is not physical key-to-screen latency or a remote-play benchmark. The fighter lacks normal local-player movement prediction. Test real players at several network delays before promising precise combos or tight defensive windows. |
| F5 and FOV | Third-person back changes framing; third-person front looks away from the stage. The native client retains control of perspective and FOV, and those settings are not transmitted for this server to lock. The current test requires first person and roughly 70 FOV. Provide a brief onboarding instruction and a camera-distance calibration option; test common resolutions and alternative client settings. |
| Character picker | A functional five-icon picker is straightforward. The current native menu includes inventory chrome and obscures the world. It does not reproduce the existing grid-and-live-3D-preview screen. Explore a world-based selection area with a displayed character, or a designed inventory/dialog menu. |
| Battle HUD | The action bar is constrained by vanilla placement and GUI scale. A crosshair and an empty experience bar remain. Portrait panels, exact HUD positioning and the existing clean top timer are not migrated. A server-offered resource pack could improve fonts, icons and textures, but would not add arbitrary Java UI code or local movement prediction. Prototype that presentation separately. |
| Class animation and movesets | Native models are available immediately; their built-in animation vocabulary differs. The five classes here share the same combat test. Skeleton bow charging/arrows, Villager bell behavior, Zombie Grave Slam and the rest of the differentiated movesets are **not ported**. Preserve their gameplay ideas, then adapt the animation and input feedback to what vanilla can show clearly. |
| Click semantics | Vanilla use-item and swing packets can drive combat. Rapid-click testing accepted six attacks across seven click attempts including the initial strike; server cooldowns remain authoritative. This is not a guarantee of every tap timing. Held clicks, native item behavior and input buffering need deliberate design and further tests. |
| Versions and audience | This implementation targets 26.2. Being mod-free does not mean every Java version, third-party client, or Bedrock player can use it. Older clients would need a tested compatibility strategy, including replacements for newer entity features. Crossplay remains a separate experiment. |
| Capacity and operations | The experiment has one arena. It does not establish players-per-machine capacity, match quality with human opponents, or resilience under load. A public version needs independent matches, matchmaking and party handling, authenticated entry, reconnect rules, abuse controls and load testing. |

![F5 front view illustrates a native camera limitation](A:/Coding/Projects/minecraf/smash_vanilla/evidence/screenshots/0008_08-native-front-camera-limitation.png)

**Verification and practical limits**

The build and dedicated-server client suite passed. The suite checks native menu selection, no queue before selection, camera attachment/orientation, movement, double jump, aerial heavy input, shielding without camera detachment, S dropping, light-hit damage, ring-out protection, all five rendered models, camera restoration, queue disconnect cleanup and reconnect behavior. It saved 14 screenshots. The suite runs inside Fabric's client test harness; the original Smash client mod is explicitly checked to be absent.

The separate launcher runs the actual official client against a separate local server. Both the single-client practice smoke test and the four-client matchmaking smoke test passed. The four-client run automatically chose classes to exercise the queue; manual menu selection was tested separately in the client suite. Its manifest is saved in `evidence/vanilla-client-manifest.json`. Test output and screenshots are retained in `evidence`. The launcher never reads launcher credentials and binds its offline test server to loopback. Public deployment and account authentication were not tested.

This is a small test arena. The Mythical Garden lobby, full five-class gameplay, polished matchmaking/results UI, spectators, production persistence and multi-arena hosting have not been migrated. The existing modded game and its PLAY save remain separate.

**Recommended next steps**

1. Have several people play the vanilla movement/combat prototype over real connections, including roughly 50, 100 and 150 ms network RTT. Judge jump timing, stopping, platform drops and attack feedback first. Compare against the existing modded prototype. The local 150 ms observation cannot predict those results by itself.
2. If movement is enjoyable, build a small resource pack and one representative class to test a complete match's visual feedback. Keep attacks readable and allow enough input buffering for the measured delay. Avoid committing the full roster and UI migration before that test.
3. For public growth, separate the lobby/queue from many four-player matches. Evaluate Paper for its server-plugin tooling and a proxy such as Velocity for routing players between match servers. This is a proposed production architecture, not a claim that this Fabric prototype is already a Paper plugin or scales to a particular player count. Paper and Velocity's own documentation describe those platforms: [Paper setup](https://docs.papermc.io/paper/getting-started/), [Velocity](https://docs.papermc.io/velocity/).

My decision is **yes to mod-free Java distribution, with a real-network gameplay test before committing to this exact side-camera implementation**. A fast invitation flow helps a public server reach people; enjoyable movement and matches that fill promptly determine whether those people stay.

For a hands-on local test, double-click [TRY VANILLA.cmd](<A:/Coding/Projects/minecraf/smash_vanilla/TRY VANILLA.cmd>), then use `/smash practice`. The development launcher is only for this workspace; public players would use their usual Minecraft launcher and server list.
