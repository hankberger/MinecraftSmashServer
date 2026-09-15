# Vanilla fighter menu

The Play compass, spawn podium and `/smash join` open one menu with a mouse-driven portrait grid, live rotating fighter on the right, mode buttons, party access and Play/Ready. Search stays on that stage and transfers directly into a match. Five default fighters ship now; six portraits fit each page. Adding a fighter requires its server model/moves and generated portrait assets; page controls appear automatically.

## Delivery

Players accept Minecraft's ordinary server-resource-pack prompt. The pack is required for this UI and cached by Minecraft using its SHA-1. It contains about 106 KiB of fonts, portraits and container textures, with no shaders or executable code. Reconnect after a failed download to retry. No Fabric installation or manual file copying is needed on clients.

`UiPack` offers the pack after lobby arrival has settled, and selection waits for Minecraft's successful-load response. The network lobby defaults to `https://raw.githubusercontent.com/hankberger/MinecraftSmashServer/main/resourcepacks/<sha1>.zip`. Push pack and server changes together; CI validates that the public artifact, bundled artifact and font index match. Retain previously published hash files so older servers and rollback deployments still work. A content-addressed object store/CDN can replace GitHub later via `SMASH_RESOURCE_PACK_URL`; keep the bytes identical to the bundled pack. Never overwrite an existing hash URL.

For standalone `PLAY.cmd`, the server serves its bundled zip on an ephemeral `127.0.0.1` HTTP port. Remote standalone hosting must set `SMASH_RESOURCE_PACK_URL` to a URL reachable by players. Docker Compose passes this optional environment override to the lobby. Arena servers do not show the menu or send additional packs; returning lobby connections re-confirm the cached pack. `SMASH_RESOURCE_PACK=disabled` retains the previous selection flow for development or emergency fallback.

## Native constraints

This is an ordinary six-row container with server-owned empty slots and a custom bitmap-font title. The artwork covers the fixed vanilla slot hit areas; the right side stays transparent over the world. The server consumes left-click actions and rejects inventory manipulation and stale menu packets. Party dialogs retain normal Minecraft buttons. It is not a custom client screen.

Minecraft dims the world while the menu is open. First-person perspective and approximately 70 FOV give the intended stage framing. GUI scale changes the menu size independently of the world, so framing varies. Test at 16:9 and 4:3, GUI scales 2 and 3; 26.2 is the supported client version. The pack makes generic six-row chest backgrounds, inventory labels and slot highlights transparent while connected. This dedicated minigame owns inventories; unrelated inventory plugins would need compatible textures. Raster labels are currently English, and custom glyphs do not provide full narrator accessibility. There is no skin browsing or drag-to-rotate yet.

## Assets and validation

Run `C:/Python312/python.exe tools/build_ui_pack.py` with Pillow and the official 26.2 client already in the sibling Gradle cache. It derives portraits and text from vanilla assets and produces `src/main/resources/ui/{pack.zip,index.json}` plus the matching `resourcepacks/<sha1>.zip`. Commit all three. Remove unpublished intermediate packs before committing; retain published versions.

`python -m unittest discover -s deploy -p 'test_*.py'` checks artifact/hash consistency. `gradlew runClientGameTest -PdedicatedTests -PpackedTests` exercises a genuine HTTP download and pack prompt, mouse clicks on all portraits, parties, ready/cancel/change, Escape, remembered selection, rendering at different sizes and practice entry. `deploy/matchmaking_smoke.py` checks four checksum-verified stock clients across the real proxy and containers; see NETWORK.md. Native screenshots and network evidence are stored under the ignored `evidence/packed-menu/` directory.
