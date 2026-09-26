package dev.hanks.vanilla;

import dev.hanks.network.PointRules;
import java.net.URI;

/** The courtyard store presents the existing wallet and the one current membership. */
final class StoreMenu {
    static final String DEFAULT_URL = "https://ringshift-arena.hankberger.chatgpt.site/store";
    private StoreMenu() {}

    static URI url(String setting) {
        var uri=URI.create(setting==null || setting.isBlank()?DEFAULT_URL:setting.strip());
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost()==null || uri.getRawUserInfo()!=null)
            throw new IllegalArgumentException("Store URL must use HTTPS without credentials");
        return uri;
    }

    static String body(long credits, boolean member) {
        return PointRules.format(credits)+" credits\n\n"
                +"Ringshift Plus · "+(member?"Active":"$7.99 / month")+"\n"
                +"1,000 credits each month\n"
                +"Lobby member badge\n"
                +"All 5 alternate skins while subscribed\n\n"
                +"Use credits to unlock skins in character select.";
    }
}
