package dev.hanks.vanilla;

import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.network.chat.*;
import net.minecraft.network.protocol.common.*;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/** One content-addressed, cached server pack for the vanilla dialog picker. */
public final class UiPack implements AutoCloseable {
    public static final UUID ID = UUID.fromString("6427a4c3-d7d6-4e56-9240-e713b068c21b");
    private static final JsonObject INDEX;
    static {
        try (var in = UiPack.class.getResourceAsStream("/ui/index.json")) {
            INDEX = JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(in),StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) { throw new ExceptionInInitializerError(e); }
    }
    public static final String HASH = INDEX.get("sha1").getAsString();
    private final Set<UUID> loaded = new HashSet<>();
    private final Map<UUID,String> states = new HashMap<>();
    private final Map<UUID,Integer> pending = new HashMap<>();
    private HttpServer http;
    private String url;
    public boolean enabled() { return !Boolean.getBoolean("smash_vanilla.legacyTests") && !"disabled".equals(System.getenv("SMASH_RESOURCE_PACK")); }
    public boolean ready(ServerPlayer p) { return loaded.contains(p.getUUID()); }
    public String status(ServerPlayer p) { return states.getOrDefault(p.getUUID(),"Loading fighter menu…"); }
    public void start(boolean network) {
        close(); if (!enabled()) return;
        url = System.getenv("SMASH_RESOURCE_PACK_URL");
        if (url != null && !url.isBlank()) return;
        if (network) { url = "https://raw.githubusercontent.com/hankberger/MinecraftSmashServer/main/resourcepacks/"+HASH+".zip"; return; }
        try {
            byte[] bytes;
            try (var in = UiPack.class.getResourceAsStream("/ui/pack.zip")) { bytes=Objects.requireNonNull(in).readAllBytes(); }
            http=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            http.createContext("/"+HASH+".zip",exchange -> {
                if (!exchange.getRequestURI().getPath().equals("/"+HASH+".zip") || !exchange.getRequestMethod().equals("GET")) { exchange.sendResponseHeaders(404,-1); exchange.close(); return; }
                exchange.getResponseHeaders().set("Content-Type","application/zip");
                exchange.getResponseHeaders().set("Cache-Control","public, max-age=31536000, immutable");
                exchange.sendResponseHeaders(200,bytes.length);
                try(var out=exchange.getResponseBody()) { out.write(bytes); }
            });
            http.start(); url="http://127.0.0.1:"+http.getAddress().getPort()+"/"+HASH+".zip";
        } catch(IOException e) { throw new IllegalStateException("Cannot serve local UI pack",e); }
    }
    public void offer(ServerPlayer p) {
        if (!enabled()) return;
        states.put(p.getUUID(),"Loading fighter menu…");
        p.connection.send(new ClientboundResourcePackPushPacket(ID,url,HASH,true,Optional.of(Component.literal("Smash · Fighter portraits and menus"))));
    }
    public void schedule(ServerPlayer p,int at) { if(enabled()) pending.put(p.getUUID(),at); }
    public void tick(VanillaSmash game) {
        for(var entry:List.copyOf(pending.entrySet())) if(game.ticks>=entry.getValue()) {
            pending.remove(entry.getKey()); var p=game.server.getPlayerList().getPlayer(entry.getKey()); if(p!=null) offer(p);
        }
    }
    public void response(ServerPlayer p,ServerboundResourcePackPacket packet) {
        if (!packet.id().equals(ID)) return;
        String state=packet.action().name();
        if (state.equals("SUCCESSFULLY_LOADED")) { loaded.add(p.getUUID()); states.remove(p.getUUID()); }
        else if (state.startsWith("FAILED") || state.equals("INVALID_URL") || state.equals("DECLINED")) {
            loaded.remove(p.getUUID()); states.put(p.getUUID(),"Menu pack unavailable · reconnect to retry");
        }
        VanillaSmash.LOG.info("SMASH_UI_PACK player={} status={} hash={}",p.getPlainTextName(),state,HASH);
    }
    public void forget(UUID id) { loaded.remove(id); states.remove(id); pending.remove(id); }
    public void close() { if(http!=null) http.stop(0); http=null; loaded.clear(); states.clear(); pending.clear(); }
    private static FontDescription.Resource font(String name) { return new FontDescription.Resource(Identifier.fromNamespaceAndPath("smash",name)); }
    public static Component space(int n) { return Component.literal(String.valueOf((char)(0xf000+n+256))).withStyle(s->s.withFont(font("ui"))); }
    public static MutableComponent strip(String name) {
        var glyph=INDEX.getAsJsonObject("glyphs").getAsJsonObject("dialog_"+name);
        return Component.empty().append(Component.literal(glyph.get("char").getAsString()).withStyle(s->s.withFont(font("ui")).withColor(0xffffff).withShadowColor(0)));
    }
    public static int textWidth(String value) {
        return textWidth(value,"widths");
    }
    public static MutableComponent sidebarText(String value) { return Component.literal(value).withStyle(s->s.withFont(font("sidebar"))); }
    public static MutableComponent pickerText(String value,int y,boolean narrow) {
        return Component.literal(value).withStyle(s->s.withFont(font((narrow?"picker_name_":"picker_text_")+y)).withShadowColor(0));
    }
    public static int artWidth(String name) { return INDEX.getAsJsonObject("glyphs").getAsJsonObject("dialog_"+name).get("width").getAsInt(); }
    public static int sidebarWidth(String value) {return textWidth(value,"sidebarWidths");}
    public static int pickerNameWidth(String value) {return textWidth(value,"pickerNameWidths");}
    private static int textWidth(String value,String table) {
        int width=0;var widths=INDEX.getAsJsonObject(table);
        for(char c:value.toCharArray()) width+=widths.has(String.valueOf(c))?widths.get(String.valueOf(c)).getAsInt():6;
        return width;
    }
}
