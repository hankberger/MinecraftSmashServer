# Maps

The server builds its maps in dedicated dimensions. The scenery uses vanilla blocks; the fighter menu adds the server resource pack described in [UI_PACK.md](UI_PACK.md).

**Mythical Garden, revision 3.** The Elder Bloom tree and its crown stand at `(0, 100, -15)`, 33 blocks farther from arrival than revision 2 and 36 blocks closer than the original. An open court at `(0, 100, -62)` adds a gathering space, benches, low lighting and planted edges before the tree approach. The lotus and its lake now occupy a side garden at `(48, 100, 0)`, linked by a branching east walk. Arrival remains `0.549 / 101 / -105.631`, facing south, and the existing fall-return point remains clear.

The hollow contains a library, reading seats, a crafting nook, an enchanting table, a lantern chandelier, and stairs to a furnished reading loft with a round window overlooking the garden. The five-block-wide approach stays clear.

**PLAY podium.** A Steve fighter stands at `(4.5, 102, -98.5)`, visible just ahead on the left from arrival. Click the fighter or right-click the quartz base to open the existing mode/party menu. It preserves leader authority, character selection and ready-up. The base is an additive 3×3 feature at `x=3..5, z=-100..-98`, outside the five-block-wide center path; the podium is reapplied after garden migration. Only the short PLAY/click label is added. The vanilla model and labels are created while players are nearby and removed when the area empties or the server stops; no chunks are forced to stay loaded.

**Skybound Grove, revision 2.** The battle stage is a floating grass-and-stone island with an exposed timber mineshaft, ores, roots, and an amethyst geode. Three timber platforms have copper end caps and hanging lanterns. Behind them are an oak island, an overgrown portal ruin, and a distant homestead. The stage keeps the existing landing surfaces, blast zones, and camera coordinates. Scenery is behind the fighting plane; the combat area above the main deck contains only the three intended platforms.

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

The original and revision-2 binaries are retained as exact migration footprints. The lobby builder validates all three assets, clears their combined footprints, places revision 3, and publishes its new marker only after completion. The arena builder clears the bounds containing both revisions before constructing the new arena. Interrupted construction retries on startup. These builders only operate in the protected Smash dimensions.

To check the real world migration, walk the tree approach and stairs using native input, capture both maps, and run the class-combat regression suite:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25'
./gradlew.bat --gradle-user-home ../smash_arena/.gradle-user-home build runClientGameTest -PdedicatedTests -PmapTests
```

`MapWorldTest` checks that the old landmarks and halo are removed and that the arena's block collisions agree with its gameplay floors. Screenshots are written under `build/run/clientGameTest/screenshots`.
