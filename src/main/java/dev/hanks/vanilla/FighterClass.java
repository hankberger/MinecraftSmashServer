package dev.hanks.vanilla;

/** Stable wire IDs for the distinct, server-selected combat kits. */
public enum FighterClass {
    STEVE(0, "Steve", "THE ORIGINAL", "A familiar face. Ready for any arena.", 0xff65d9ef),
    ALEX(2, "Alex", "THE EXPLORER", "A new frontier. A new challenger.", 0xffeab578),
    ZOMBIE(1, "Zombie", "THE UNDEAD", "Back from the grave. Back for another round.", 0xff99da70),
    SKELETON(3, "Skeleton", "THE BONE FIGHTER", "All bones. All business.", 0xffd6e3e9),
    // IDs 4–9 belonged to the retired roster. Do not reuse them for a different character.
    VILLAGER(10, "Villager", "THE RESOURCEFUL", "A familiar face with a few tricks up those sleeves.", 0xffe5c779),
    ENDERMAN(11, "Enderman", "THE VOID HUNTER", "Mark your prey. Follow them through the void.", 0xffca8aff),
    DROWNED(12, "Drowned", "THE TIDE HUNTER", "Land the harpoon. Decide who comes closer.", 0xff63dccc),
    IRON_GOLEM(13, "Iron Golem", "THE IRON SENTINEL", "Stand your ground. Send them skyward.", 0xffd9ded2);

    public final int id, accent;
    public final String label, title, description;
    FighterClass(int id, String label, String title, String description, int accent) {
        this.id = id; this.label = label; this.title = title; this.description = description; this.accent = accent;
    }
    public static FighterClass fromId(int id) {
        for (var kind : values()) if (kind.id == id) return kind;
        return null;
    }
}
