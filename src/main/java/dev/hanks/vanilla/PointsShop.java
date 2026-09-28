package dev.hanks.vanilla;

import dev.hanks.network.*;
import java.net.URI;
import java.util.Optional;

/** Store discovery only. Payment fulfillment is configured separately before launch. */
final class PointsShop {
    private PointsShop() {}
    static Optional<URI> url(String value) {
        if(value==null || value.isBlank())return Optional.empty();
        var uri=URI.create(value.strip());
        if(!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost()==null || uri.getRawUserInfo()!=null)
            throw new IllegalArgumentException("SMASH_STORE_URL must be an HTTPS store URL without credentials");
        return Optional.of(uri);
    }
    static String body(long balance,Cosmetics.Wardrobe wardrobe,Cosmetics.Skin skin,boolean storeReady) {
        var text=new StringBuilder("Balance: "+PointRules.format(balance)+" Points\n\n");
        if(!wardrobe.owns(skin)) {
            int price=Cosmetics.price(wardrobe,skin);
            text.append(skin.label()).append(" · ").append(PointRules.format(price)).append(" Points");
            if(wardrobe.owned().isEmpty())text.append(" · ").append(EconomyRules.FIRST_SKIN_DISCOUNT_PERCENT).append("% off");
            if(balance<price)text.append("\n").append(PointRules.format(price-balance)).append(" more to unlock");
            text.append("\n\n");
        }
        text.append(storeReady?"Buy Points in our online store.":"Point purchases are coming soon.");
        text.append("\n\nEarn by playing\n").append(PointRules.FINISH).append(" per qualifying match · +")
                .append(PointRules.WIN).append(" for a win");
        return text.toString();
    }
}
