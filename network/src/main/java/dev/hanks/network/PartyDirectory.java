package dev.hanks.network;

import java.util.*;

/** Bounded search and recent opponents; identities are UUIDs, display names are never authority. */
public final class PartyDirectory {
    public static final int PAGE_SIZE = 3, RECENT_LIMIT = 24, HISTORY_LIMIT = 4096;
    public record Contact(UUID id, String name, String fighter) {}
    private final LinkedHashMap<UUID, List<Contact>> recent = new LinkedHashMap<>(16, .75f, true);
    public void clear() { recent.clear(); }
    public List<Contact> recent(UUID player) { return recent.getOrDefault(player, List.of()); }
    public void met(UUID player, List<Contact> contacts) {
        var all = new LinkedHashMap<UUID, Contact>();
        for (var c : contacts) if (!c.id().equals(player)) all.put(c.id(), c);
        for (var c : recent(player)) all.putIfAbsent(c.id(), c);
        recent.put(player, all.values().stream().limit(RECENT_LIMIT).toList());
        while (recent.size() > HISTORY_LIMIT) recent.pollFirstEntry();
    }
    public void match(Wire.MatchResult result) {
        var contacts = result.rows().stream().map(r -> new Contact(r.player(), r.name(), r.fighter())).toList();
        for (var c : contacts) met(c.id(), contacts);
    }
    public static String query(String value) {
        String trimmed = value == null ? "" : value.strip();
        if (!trimmed.matches("[A-Za-z0-9_]{0,16}")) throw new IllegalArgumentException("Use a Minecraft username");
        return trimmed;
    }
    public static List<Contact> search(Collection<Contact> online, UUID self, String query) {
        String term = query(query).toLowerCase(Locale.ROOT);
        if (term.isEmpty()) return List.of();
        return online.stream().filter(c -> !c.id().equals(self) && c.name().toLowerCase(Locale.ROOT).contains(term))
                .sorted(Comparator.comparingInt((Contact c) -> c.name().equalsIgnoreCase(term) ? 0 : c.name().toLowerCase(Locale.ROOT).startsWith(term) ? 1 : 2)
                        .thenComparing(Contact::name, String.CASE_INSENSITIVE_ORDER).thenComparing(Contact::id)).toList();
    }
}
