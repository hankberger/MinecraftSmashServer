package dev.hanks.network;

import java.util.*;

/** Stable, server-owned catalog. A look never changes a fighter's kit. */
public final class Cosmetics {
    public static final String DEFAULT = "default";
    public record Skin(String id, String fighter, String label, int price) {}
    private static final List<Skin> ALTERNATES = List.of(
            new Skin("diamond", "STEVE", "Diamond", EconomyRules.STANDARD_SKIN),
            new Skin("scout", "ALEX", "Scout", EconomyRules.STANDARD_SKIN),
            new Skin("dune", "ZOMBIE", "Dune", EconomyRules.STANDARD_SKIN),
            new Skin("frost", "SKELETON", "Frost", EconomyRules.STANDARD_SKIN),
            new Skin("desert", "VILLAGER", "Desert", EconomyRules.STANDARD_SKIN));
    public static int price(Wardrobe wardrobe,Skin skin) {
        if(wardrobe.owns(skin))return 0;
        return wardrobe.owned().isEmpty()?skin.price()*(100-EconomyRules.FIRST_SKIN_DISCOUNT_PERCENT)/100:skin.price();
    }
    public static List<Skin> forFighter(String fighter) {
        if (!Wire.CLASSES.contains(fighter)) throw new IllegalArgumentException("Invalid fighter");
        var result = new ArrayList<Skin>(); result.add(new Skin(DEFAULT, fighter, "Default", 0));
        ALTERNATES.stream().filter(s -> s.fighter().equals(fighter)).forEach(result::add);
        return List.copyOf(result);
    }
    public static Skin skin(String fighter, String id) {
        return forFighter(fighter).stream().filter(s -> s.id().equals(id == null ? DEFAULT : id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Invalid skin for fighter"));
    }
    public record Wardrobe(Set<String> owned, Map<String,String> equipped) {
        public static final Wardrobe EMPTY = new Wardrobe(Set.of(), Map.of());
        public Wardrobe { owned = Set.copyOf(owned); equipped = Map.copyOf(equipped); }
        public boolean owns(Skin skin) { return skin.price() == 0 || owned.contains(skin.id()); }
        public String equipped(String fighter) { return equipped.getOrDefault(fighter, DEFAULT); }
    }
    private Cosmetics() {}
}
