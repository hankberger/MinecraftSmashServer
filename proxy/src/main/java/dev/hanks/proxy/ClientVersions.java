package dev.hanks.proxy;

/** Validated client versions, rather than Velocity's much wider transport range. */
final class ClientVersions {
    static final int NATIVE = 776;
    static final int LATEST = 777;
    static final String LABEL = "Smash | Java 26.2–26.3";
    static final String INSTRUCTIONS = "Smash supports Minecraft Java 26.2 and 26.3.\nChoose Latest Release (26.3) in the Minecraft Launcher.";

    static boolean supports(int protocol) { return protocol == NATIVE || protocol == LATEST; }
    static int advertisedProtocol(int client) { return supports(client) ? client : LATEST; }
    private ClientVersions() { }
}
