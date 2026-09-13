package dev.hanks.vanilla;

/** Stable wire IDs for the five distinct, server-selected combat kits. */
public enum FighterClass {
    STEVE(0, "Steve", "THE ORIGINAL", "A familiar face. Ready for any arena.", 0xff65d9ef),
    ALEX(2, "Alex", "THE EXPLORER", "A new frontier. A new challenger.", 0xffeab578),
    ZOMBIE(1, "Zombie", "THE UNDEAD", "Back from the grave. Back for another round.", 0xff99da70),
    SKELETON(3, "Skeleton", "THE BONE FIGHTER", "All bones. All business.", 0xffd6e3e9),
    // IDs 4–9 belonged to the retired roster. Do not reuse them for a different character.
    VILLAGER(10, "Villager", "THE RESOURCEFUL", "A familiar face with a few tricks up those sleeves.", 0xffe5c779);

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
