# BrawlParty in-game artwork

These are the curated runtime sources from the BrawlParty asset pack. The server build is self-contained: it does not read the sibling website or asset catalog.

- Eight transparent fighter portraits, exported at 256px from the generated masters.
- Credit token and Plus badge, 128px.
- Victory and KO graphics, 256px.
- Approved coral impact symbol.
- Native-size button states and menu panels.

The artwork was generated with the built-in image tool using the approved BrawlParty branding. The existing Minecraft ASCII font supplies all labels. No promotional map illustration is presented as gameplay, and no new membership benefit is implied by the artwork.

`tools/brand_ui_assets.py` and `tools/picker_ui_assets.py` compose the art with native menu surfaces. The single-page picker's eight fighter cards render at 108×108 pixels but occupy **36×36 GUI pixels**. Party, result and HUD portraits, plus the store token and badge, also use 3× density. The builder pads transparent artwork to stable font advances so clicking, text alignment and live party updates remain correct. The final GUI pixel column is reserved for Minecraft's bitmap-font advance.

Run `tools/build_ui_pack.py` to regenerate the bundled pack, font index and content-addressed public zip together. Runtime asset names and Java glyph names must stay synchronized. `deploy/test_ui_pack.py` and the packed client test cover all eight fighters, full-party names and portraits, font advances, valid baselines, and atlas limits.
