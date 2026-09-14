# Maps

The server builds its maps in dedicated dimensions. Normal clients need no resource pack.

**Mythical Garden, revision 2.** The Elder Bloom tree and its crown now stand at `(0, 100, -48)`, where the large pink lotus used to be. The lotus and lake move to the old tree site at `(0, 100, 21)`. This shortens the approach to the tree by 69 blocks. Arrival remains `0.549 / 101 / -105.631`, facing south; falling still returns players to the center.

The hollow contains a library, reading seats, a crafting nook, an enchanting table, a lantern chandelier, and stairs to a furnished reading loft with a round window overlooking the garden. The five-block-wide approach stays clear.

**Skybound Grove, revision 2.** The battle stage is a floating grass-and-stone island with an exposed timber mineshaft, ores, roots, and an amethyst geode. Three timber platforms have copper end caps and hanging lanterns. Behind them are an oak island, an overgrown portal ruin, and a distant homestead. The stage keeps the existing landing surfaces, blast zones, and camera coordinates. Scenery is behind the fighting plane; the combat area above the main deck contains only the three intended platforms.

## Authoring and migration

**Character Garden.** The live selector uses `smash_vanilla:showcase`, a separate void dimension with the garden's sky and lighting. `ShowcaseBuilder` creates a small cherry-tree island, tiered roster shelves and a lantern-framed presentation platform on first use. Concurrent sessions occupy separate sets spaced 1,024 blocks apart, beyond the supported entity tracking/view distances. Released sets are reused and their chunks are not force-loaded. World geometry persists; cameras, models and labels belong to the session and are removed on confirm, cancel, disconnect or shutdown. Stale saved temporary entities are discarded when their chunks load.

`CharacterStage` owns the draft choice and native hotbar input; `FighterModels` supplies the same five native entity types used by combat. The queue/coordinator only receives a class after explicit confirmation. The selected model rotates automatically; the camera stays fixed. No custom client packets, client mixins, resource pack or inventory selector are involved.

For the focused native-input and visual checks, run `./gradlew.bat --gradle-user-home ../smash_arena/.gradle-user-home runClientGameTest -PdedicatedTests -PshowcaseTests`. The full client suite also runs those checks. Screenshots include all five fighters, 16:9 and 4:3 framing, and 90 FOV.

`tools/garden_source.py` contains the original garden geometry. `tools/build_garden.py` composes the relocated landmarks and the new interior, validates the approach, stairs and fall-return point, and losslessly replays the compressed asset. Run it with Python and NumPy:

```powershell
C:/Python312/python.exe tools/build_garden.py
```

Commit the generated `mythical_garden_v2.bin.gz` and its JSON manifest. Update the expected block count in `LobbyBuilder` if geometry changes that count. The build and Docker images use this committed asset; the server does not need Python.

The original garden binary is retained as the exact footprint of revision 1. On migration, the lobby builder validates both assets, clears both footprints, places revision 2, and publishes its new marker only after completion. The arena builder clears the bounds containing both revisions before constructing the new arena. Interrupted construction retries on startup. These builders only operate in the protected Smash dimensions.

To check the real world migration, walk the tree approach and stairs using native input, capture both maps, and run the class-combat regression suite:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25'
./gradlew.bat --gradle-user-home ../smash_arena/.gradle-user-home build runClientGameTest -PdedicatedTests -PmapTests
```

`MapWorldTest` checks that the old landmarks and halo are removed and that the arena's block collisions agree with its gameplay floors. Screenshots are written under `build/run/clientGameTest/screenshots`.
