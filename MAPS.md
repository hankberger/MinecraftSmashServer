# Maps

The server builds its maps in dedicated dimensions. The scenery uses vanilla blocks; the fighter menu adds the server resource pack described in [UI_PACK.md](UI_PACK.md).

**Mythical Garden, revision 3.** The Elder Bloom tree and its crown stand at `(0, 100, -15)`, 33 blocks farther from arrival than revision 2 and 36 blocks closer than the original. An open court at `(0, 100, -62)` adds a gathering space, benches, low lighting and planted edges before the tree approach. The lotus and its lake now occupy a side garden at `(48, 100, 0)`, linked by a branching east walk. Arrival is now `0.5 / 101 / -78.5`, between the guardian statues and facing south into the courtyard, and the existing fall-return point remains clear.

The hollow contains a library, reading seats, a crafting nook, an enchanting table, a lantern chandelier, and stairs to a furnished reading loft with a round window overlooking the garden. The five-block-wide approach stays clear.

**PLAY podium.** A Steve fighter stands at `(4.5, 102, -71.5)`, at the front of the courtyard, a few steps ahead on the left from arrival. Click the fighter or right-click the quartz base to open the existing mode/party menu. It preserves leader authority, character selection and ready-up. The base is an additive 3×3 feature at `x=3..5, z=-73..-71`, outside the five-block-wide center path; the podium is reapplied after garden migration. Preparation restores the former podium footprint to the authored cherry-plank/quartz floor and clears its raised blocks, including on already-built worlds. Only the short PLAY/click label is added. The vanilla model and labels are created while players are nearby and removed when the area empties or the server stops; no chunks are forced to stay loaded.

**Skybound Grove, revision 4.** Rebuilt to match the website's floating-garden concept: a continuous grass deck above a jagged stone/root cross-section, trailing vines, planted back edge and recessed glowing amethyst core. The three spruce platforms use oxidized copper bands, front-facing chains and lanterns. A large cherry tree and oak frame the battle; smaller trees sit behind the side platforms. Layered distant islands, two contained animated waterfalls and an overgrown purple portal create depth against a bright blue sky with vanilla clouds. Scenery remains behind the fighters; the clear center keeps attack silhouettes readable. The deck width, three platform positions, beveled ledges, blast zones (`x=-38..39, y=53..105`), movement and camera distances are unchanged. The main island's collision core still blocks recovery through its underside; decorative roots are outside the fighting plane. The three upper platforms remain one-way. Water stays within barrier-lined columns and stone plunge pools, entirely outside the battle lane. Copper is waxed and foliage is persistent.

**Emberforge, revision 1.** A 25-block main deck (`x=-12..12`, feet at `y=81`) surrounds an exposed blast furnace, with blackstone, basalt, copper ribs, lit vents and an industrial flywheel backdrop. One nine-block platform (`x=-4..4`, feet at `y=86`) offers a route over projectiles while the compact floor favors close encounters and edge pressure. Furnace houses, chimney stacks and a copper gantry frame the stage. Fire-colored details use solid decorative blocks; there are no liquid hazards. Blast zones are `x=-34..35, y=53..105`.

**Cloudspire, revision 1.** A 33-block pale temple deck (`x=-16..16`, feet at `y=81`) with turquoise inlay, recessed arches, suspended amethyst foundations, broken gateways and cherry gardens on distant islands. Five platforms form a symmetrical climb: outer shelves at `x=-13..-8 / 8..13, y=85`, inner shelves at `x=-7..-3 / 3..7, y=89`, and the crown at `x=-2..2, y=92`. This creates routes for aerial pursuit, dropping attacks and movement between elevations. Blast zones are `x=-38..39, y=53..108`, retaining 16 blocks of headroom above the crown.

All three stages share the same movement rules and main-deck height. Each owns its platform geometry, spawn positions, deck edges, respawn landing and blast boundaries. Upper floors are one-way, the main island stays solid, and ledge corners are beveled to keep hanging fighters visible. Decorations above deck level stay behind the fighting plane. Existing camera following fits the actual opening roster and then tracks the fight.

For public duels, four-player matches and rematches, a fresh reservation UUID selects one stage with equal probability. The same reservation always resolves to the same stage, including retries and players arriving on an arena worker at different times. Stages have separate prebuilt vanilla dimensions, so starting a round does not rebuild terrain. The native arrival camera is created in the selected dimension before countdown and reused when the roster is ready. Local matchmaking uses the identical draw. Practice and sandbox remain on Skybound Grove.

`BattleStageTest` checks selection, spawn safety, one-way floors, respawns and ledges for all layouts. `-PstageTests` checks the constructed collision surfaces, enters each stage with a native client, drops/jumps through every platform, climbs both ledges, checks each fighter's standing collider against the underside, tests KO/respawn, and captures opening/ledge/respawn screenshots.

Use `-PgroveTests` instead of `-PstageTests` for the focused Grove check. It also migrates revision-3 and revision-2 rollback footprints, checks waterfall containment, and verifies that the key waterfall and portal blocks reach the native client at its ordinary test view distance. Primary landmarks sit within that range; farther islands add depth on longer client/server view distances. Vanilla cloud rendering must be enabled in the player's video settings to see the clouds.

## Authoring and migration

**Heartwood Pavilion.** The packed selector uses `smash_vanilla:showcase`. `ShowcaseBuilder.ensureFighterBuilt` builds a warm timber garden court with a bamboo parquet dais, sandstone rim and stairs, timber posts with copper collars and corbels, a layered copper gable, hanging lanterns, planters, a contained lily pool, workshop/library alcove, benches, cherry trees and oak foliage. The central silhouette stays clear. The detached camera is at `(0.2, 104.7, 14.5)` relative to the room, facing `(180,8)`, placing the full fighter near the middle beside the left-hand dialog menu.

An emerald-block marker at `(0,93,0)` versions the pavilion. The first packed visit clears the combined old garden/gray studio/new pavilion bounds (`x=-38..34, y=91..128, z=-30..18`) and rebuilds. The marker is written only after successful construction. Winner rooms have negative IDs and retain their existing presentation. The resource-pack-disabled fallback can rebuild its original garden in a positive room.

Concurrent sessions occupy separate sets spaced 1,024 blocks apart, beyond the supported entity tracking/view distances. Active sessions hold chunk tickets through presentation handoffs; released sets are reused and their tickets are released. World geometry persists; cameras, models and labels belong to the session and are removed on confirm, cancel, disconnect or shutdown. Stale saved temporary entities are discarded when their chunks load.

`CharacterStage` owns the draft choice; `FighterModels` supplies the same five native entity types used by combat. The queue/coordinator only receives the group's classes once every member has explicitly readied. Party members browse separate stages concurrently. The selected model rotates automatically. The packed menu uses native dialog text click regions and a detached camera; the fallback uses hotbar controls and in-world interaction targets. Closing the stage restores ordinary movement and interaction reach. Camera-session attack packets are handled before vanilla's invalid-target/self-attack check. No custom client packets or client gameplay mod are required.

For the focused native-input and visual checks, run `./gradlew.bat --gradle-user-home ../smash_arena/.gradle-user-home runClientGameTest -PdedicatedTests -PshowcaseTests`. The full client suite also runs those checks. Screenshots include all five fighters, 16:9 and 4:3 framing, and 90 FOV.

Use `-PlobbyTests` for the focused PLAY podium check: walk from the exact arrival point, click with an empty hand, enter/cancel character selection, return to a recreated NPC, and click the fighter and base independently. It also checks equipment protection, an unobstructed center path and repeated world preparation.

`tools/garden_source.py` contains the original garden geometry. `tools/build_garden.py` composes the relocated landmarks and the new interior, validates the approach, stairs and fall-return point, and losslessly replays the compressed asset. Run it with Python and NumPy:

```powershell
C:/Python312/python.exe tools/build_garden.py
```

Commit the generated `mythical_garden_v3.bin.gz` and its JSON manifest. Update the expected block count in `LobbyBuilder` if geometry changes that count. The build and Docker images use this committed asset; the server does not need Python.

The original and revision-2 binaries are retained as exact migration footprints. The lobby builder validates all three assets, clears their combined footprints, places revision 3, and publishes its new marker only after completion. Grove revision 4 clears the old and new scenery bounds (`x=-84..84, y=43..122, z=-88..5`) and replaces its old marker with an emerald block at `(1,40,0)` only after completing the build. Both older Grove revisions migrate automatically, including a return from an older server JAR. No world deletion or manual map import is needed. `AlternateArenaBuilder` builds Emberforge and Cloudspire in their own dimensions, clearing their authored bounds and retaining their reinforced-deepslate revision marker at `(1,40,0)`. Interrupted construction retries on startup. These builders only operate in the protected Smash dimensions.

To check the real world migration, walk the tree approach and stairs using native input, capture both maps, and run the class-combat regression suite:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25'
./gradlew.bat --gradle-user-home ../smash_arena/.gradle-user-home build runClientGameTest -PdedicatedTests -PmapTests
```

`MapWorldTest` checks that the old landmarks and halo are removed and that the arena's block collisions agree with its gameplay floors. Screenshots are written under `build/run/clientGameTest/screenshots`.
