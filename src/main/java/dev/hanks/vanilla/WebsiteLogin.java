package dev.hanks.vanilla;

import dev.hanks.network.LoginCodes;
import java.net.URI;
import java.util.*;
import net.minecraft.network.chat.*;
import net.minecraft.server.level.ServerPlayer;

/** Codes are shown privately in a native dialog, never broadcast or logged. */
final class WebsiteLogin {
    private final VanillaSmash game;
    private final Map<UUID,Long> issued=new HashMap<>();
    private final Map<String,Long> recentCodes=new HashMap<>();
    WebsiteLogin(VanillaSmash game) { this.game=game; }
    int open(ServerPlayer player) {
        String key=System.getenv("SMASH_WEBSITE_KEY");
        // The production lobby trusts authenticated Velocity forwarding. Never mint for offline standalone UUIDs.
        if(!LoginCodes.configured(key) || (!game.network.lobby() && !game.server.usesAuthentication())) {
            game.hub.menu.show(player,"Website sign-in","Website sign-in is not available here.",List.of(),false,"Close",()->game.hub.menu.clear(player));
            return 1;
        }
        long now=System.currentTimeMillis(),last=issued.getOrDefault(player.getUUID(),0L);
        if(now-last<5000) { player.sendOverlayMessage(Component.literal("Please wait a moment before getting another code.")); return 1; }
        issued.entrySet().removeIf(e->now-e.getValue()>60_000);
        issued.put(player.getUUID(),now);
        recentCodes.entrySet().removeIf(e->e.getValue()<now);
        String code;
        do { code=LoginCodes.issue(key,player.getUUID(),now); } while(recentCodes.containsKey(code));
        recentCodes.put(code,now+300_000);
        show(player,code);
        return 1;
    }
    void show(ServerPlayer player,String code) {
        String formatted=LoginCodes.display(code);
        var content=Component.literal(player.getGameProfile().name()+"\n\n").withColor(0xffffff)
            .append(Component.literal(formatted).withStyle(s->s.withColor(0xffdf9e).withBold(true)
                .withClickEvent(new ClickEvent.CopyToClipboard(formatted))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Copy sign-in code")))))
            .append(Component.literal("\n\nClick the code to copy. Use it within 4 minutes.\nKeep it private: it signs you into your account.\n\n").withColor(0xb7c8c1));
        URI base=StoreMenu.url(System.getenv("SMASH_STORE_URL"));
        URI login=base.resolve("/account");
        content.append(Component.literal("Open website").withStyle(s->s.withColor(0x83dfb0).withUnderlined(true)
            .withClickEvent(new ClickEvent.OpenUrl(login))));
        game.hub.menu.showArt(player,"Website sign-in",content,310,()->game.hub.menu.clear(player));
    }
}
