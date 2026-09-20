# Hosting the vanilla-client MVP

For multiple containers, shared matchmaking and rolling arena updates, use [NETWORK.md](NETWORK.md). The instructions below run a standalone server.

Players use ordinary **Minecraft Java 26.2**. They install neither Smash nor Fabric. The **server** requires Java 25, Fabric Loader 0.19.5, Fabric API 0.159.0+26.2 and `smash-vanilla-0.3.0.jar`.

These instructions are for a standalone server. The [container network](NETWORK.md) additionally accepts **26.3** through its Velocity/ViaVersion gateway; players need no compatibility mod.

Minecraft automatically offers the required menu resource pack. For a standalone server with remote players, set `SMASH_RESOURCE_PACK_URL` to a publicly reachable HTTPS URL containing the exact bundled pack; see [UI_PACK.md](UI_PACK.md). The standalone default is a loopback-only pack server for `PLAY.cmd`. The Docker lobby defaults to the content-addressed pack published in this repository.

1. Create a fresh, dedicated server directory. Download a server launcher for Minecraft 26.2 / Loader 0.19.5 from [Fabric's official server download](https://fabricmc.net/use/server/).
2. Put the two JARs from this bundle's `mods` directory in the server's `mods` directory. Do not also install the archived `smash_arena` mod.
3. Start the Fabric launcher with Java 25, for example `java -Xmx2G -jar <downloaded-launcher-name>.jar nogui`. Review Minecraft's EULA and accept it if you agree, then restart.
4. While stopped, apply `server.properties.example` as the starting configuration for this new server. It uses authenticated accounts (`online-mode=true`), whitelist entry and loopback binding for an initial private test. Whitelist your testers from the server console. For remote testing, configure the bind address and host firewall deliberately. Keep online authentication enabled.
5. On startup, the server builds Mythical Garden and the stage in their own dimensions. Join with a normal 26.2 client, use the Play or Practice hotbar item, and select a fighter. Four queued players start a match. Use first person and approximately 70 FOV during combat.

The ZIP includes the Smash server mod, its Fabric API dependency and instructions. Minecraft, Java and Fabric Loader are separate downloads. The first launch can pause for a few seconds while the 575,027-block garden is created. Subsequent startups reuse the map. Stop the server normally before copying its world for backups.

This is a single-arena MVP. It owns player arrivals, inventories and match state; it is intended for a dedicated minigame world. Match and queue state are temporary. Restarting returns players to the lobby, removes abandoned fighters/cameras and clears pending matches. The server JAR does not depend on the sibling modded source or save.

The workspace `PLAY.cmd` launcher is for local development only: it uses offline test identities, a fixed loopback port, and cached build libraries. Do not copy its generated `runtime/server/server.properties` into a public host. Use the authenticated example here.

Public hosting, DNS, load testing and monetization have not been configured. The separate Docker setup implements proxy routing. First test a complete match with real players on real connections; camera-controlled actors do not have normal local-player movement prediction. The historical feasibility report measured about 150 ms in a coarse local client-tick probe, which is not an internet latency benchmark.
