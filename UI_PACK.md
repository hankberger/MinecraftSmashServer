# Vanilla fighter menu

The Play compass, spawn podium and `/smash join` open a left-side portrait menu with a live fighter near the center of Heartwood Pavilion. The mode row reads **1v1 / 4 Player / Practice**. Five fighters ship now; each page fits eight portraits, four per row, with page arrows when needed. The party roster sits below the grid beside Play/Ready. Manage parties through the lobby's Party hotbar item or `/smash party`.

Search stays on the same stage and transfers directly into a match. Changing a fighter or cancelling search does not return players to the lobby. Back to lobby and Escape cancel the selection session; winner Results and Change fighter still connect to the same picker.

## Delivery

Players accept Minecraft's ordinary server-resource-pack prompt. The pack is required for this UI and cached by SHA-1. It contains about 46 KiB of bitmap fonts, portraits and menu presentation assets, with no core shader override or client mod. No Fabric installation or manual copying is needed on clients. Reconnect after a failed download to retry.

`UiPack` offers the pack after lobby arrival settles and selection waits for a successful-load response. The network lobby defaults to `https://raw.githubusercontent.com/hankberger/MinecraftSmashServer/main/resourcepacks/<sha1>.zip`. Push pack and server changes together; CI validates that the public artifact, bundled artifact and font index match. Retain published hash files for older servers and rollback deployments. An object store/CDN may replace GitHub through `SMASH_RESOURCE_PACK_URL`; the downloaded bytes must match the bundled pack. Never overwrite a hash URL.

Standalone `PLAY.cmd` serves the bundled zip on an ephemeral localhost HTTP port. Remote standalone hosting needs a publicly reachable `SMASH_RESOURCE_PACK_URL`. Docker Compose passes that optional override to the lobby. Arenas do not show menus or send additional packs; returning lobby connections re-confirm the cached pack. `SMASH_RESOURCE_PACK=disabled` retains the old in-world selection flow for emergency fallback.

## Native rendering and input

The picker uses a standard vanilla notice dialog containing clickable text components, not a chest inventory. Its 324-GUI-pixel canvas reserves the right half for the world, allowing the visible controls and their actual mouse targets to move left together. Portraits and buttons are split into nine-pixel-high bitmap glyphs matching vanilla text line height. Each strip carries a player-bound action token. Bitmap glyphs reserve Minecraft's one-pixel advance in their artwork, so the visible and clickable widths agree. The body width includes the native text widget's padding; omitting that allowance causes line wrapping and misaligned controls.

Selection tokens rotate when the dialog changes. Stale, guessed and other players' tokens cannot act. A separate session-bound exit token stays valid across redraws and bypasses the click debounce, so Escape cannot strand a player on stage. Empty-space clicks only refresh the menu. Inventory packets are still blocked by the managed-world inventory guard. Mode changes preserve the open dialog instead of closing and reopening the screen.

The pack makes the in-world menu background texture transparent and gives Minecraft's menu-blur post effect an empty pass list. This also removes the background and blur from other vanilla menu screens while the pack is active; neither asset has per-dialog identity. Other rendering, inventory textures, and world shaders remain vanilla. Minecraft retains its native dialog warning icon and Back to lobby footer button. GUI scale and FOV still affect composition. Supported checks cover Minecraft 26.2, 16:9 and 4:3, GUI scales 2 and 3, first-person perspective and roughly 70 FOV. The pack is pinned to format 88.0. Labels are currently English; bitmap portraits do not provide full narrator accessibility. Skin browsing and drag-to-rotate remain future work.

## Assets and checks

Run `C:/Python312/python.exe tools/build_ui_pack.py` with Pillow and the official 26.2 client cached in the sibling Gradle directory. It derives text and portraits from vanilla assets and writes `src/main/resources/ui/{pack.zip,index.json}` plus `resourcepacks/<sha1>.zip`. Commit all three. Remove unpublished intermediate packs; retain published ones.

`python -m unittest discover -s deploy -p 'test_*.py'` checks artifact/hash consistency and every portrait strip. `gradlew runClientGameTest -PdedicatedTests -PpackedTests` exercises a genuine pack prompt and HTTP download, mouse selection, all four portrait corners, stale/forged actions, party invites, ready/cancel/change, Escape, remembered selection, practice, winner controls and several window/UI sizes. It also checks replacement of persisted stage geometry. Screenshots and logs are under ignored `evidence/pavilion-picker/` and `evidence/pavilion-picker-native.log`.

`deploy/matchmaking_smoke.py` checks stock clients across the real proxy and containers; see NETWORK.md.
