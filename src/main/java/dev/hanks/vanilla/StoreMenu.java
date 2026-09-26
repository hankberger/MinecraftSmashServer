package dev.hanks.vanilla;

import dev.hanks.network.PointRules;
import java.net.URI;
import net.minecraft.network.chat.*;

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

    static URI destination(URI base, boolean credits, boolean member) {
        try {
            String query=base.getQuery();
            if(!credits && member) query=query==null?"orders":query+"&orders";
            return new URI(base.getScheme(),base.getAuthority(),base.getPath(),query,
                    credits?"credits-title":member?null:"membership-title");
        } catch (java.net.URISyntaxException invalid) { throw new IllegalArgumentException(invalid); }
    }

    static Component canvas(long credits, boolean member, URI url) {
        var canvas=Component.empty();
        String number=PointRules.compact(credits);
        int numberWidth=UiPack.storeBalanceWidth(number);
        boolean small=numberWidth>102;
        if(small)numberWidth=UiPack.textWidth(number);
        for(int row=0;row<16;row++) {
            if(row>0)canvas.append("\n");
            if(row==0) {
                canvas.append(UiPack.strip("store_panel_"+(member?"member":"guest")+"_0"));
                canvas.append(UiPack.space(-1)).append(UiPack.strip("store_panel_"+(member?"member":"guest")+"_1"));
            }
            else if(row==8) {
                int x=8+(110-numberWidth)/2;
                canvas.append(UiPack.space(x));
                var value=small?Component.literal(number).withColor(0xffdf9e):UiPack.storeBalance(number);
                canvas.append(value.withStyle(s->s.withHoverEvent(new HoverEvent.ShowText(Component.literal(PointRules.format(credits)+" credits")))));
                canvas.append(UiPack.space(324-x-numberWidth));
            } else if(row>=12 && row<=14) {
                canvas.append(UiPack.space(8));
                var left=UiPack.strip("store_"+(url==null?"disabled":"credits")+"_"+(row-12));
                var right=UiPack.strip("store_"+(url==null?"plus_disabled":member?"member":"plus")+"_"+(row-12));
                if(url!=null) {
                    left.withStyle(s->s.withClickEvent(new ClickEvent.OpenUrl(destination(url,true,member))).withHoverEvent(new HoverEvent.ShowText(Component.literal("Get credits on the website"))));
                    right.withStyle(s->s.withClickEvent(new ClickEvent.OpenUrl(destination(url,false,member))).withHoverEvent(new HoverEvent.ShowText(Component.literal(member?"Manage your membership":"View Ringshift Plus"))));
                }
                canvas.append(left).append(UiPack.space(8)).append(right).append(UiPack.space(8));
            } else canvas.append(UiPack.space(324));
        }
        return canvas;
    }
}
