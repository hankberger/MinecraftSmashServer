package dev.hanks.network;

import java.util.*;

/** Stable, server-owned catalog. A look never changes a fighter's kit. */
public final class Cosmetics {
    public static final String DEFAULT = "default";
    public record Skin(String id, String fighter, String label, int price) {}
    private static final List<Skin> ALTERNATES = List.of(
            new Skin("diamond", "STEVE", "Diamond", 250),
            new Skin("scout", "ALEX", "Scout", 250),
            new Skin("dune", "ZOMBIE", "Dune", 250),
            new Skin("frost", "SKELETON", "Frost", 250),
            new Skin("desert", "VILLAGER", "Desert", 250));
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
