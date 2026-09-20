package dev.hanks.network;

import java.util.*;

/** Lobby parties, expiring invitations and ready rounds. Mutations validate authority and stale rounds. */
public final class PartyBook {
    public enum Phase { IDLE, SELECTING, QUEUED, PLAYING }
    public record Member(UUID id, String name, String fighter, boolean ready) {}
    public record View(UUID id, UUID leader, boolean party, String mode, Phase phase, UUID round, List<Member> members) {
        public long readyCount() { return members.stream().filter(Member::ready).count(); }
    }
    public record Invite(UUID party, UUID leader, String name, long expiresAt) {}
    private static final class Group {
        final UUID id = UUID.randomUUID();
        UUID leader, round = UUID.randomUUID(); boolean party;
        String mode = "DUEL"; Phase phase = Phase.IDLE;
        final LinkedHashMap<UUID, Member> members = new LinkedHashMap<>();
        Group(UUID id, String name) { leader = id; members.put(id, new Member(id, name, null, false)); }
        View view() { return new View(id, leader, party, mode, phase, round, List.copyOf(members.values())); }
    }
    private final Map<UUID, Group> byPlayer = new LinkedHashMap<>();
    private final Map<UUID, Map<UUID, Invite>> invitations = new HashMap<>();
    public View ensure(UUID player, String name) { return byPlayer.computeIfAbsent(player, k -> new Group(player, name)).view(); }
    public View view(UUID player) { var group = byPlayer.get(player); return group == null ? null : group.view(); }
    public Set<UUID> players() { return Set.copyOf(byPlayer.keySet()); }
    public void clear() { byPlayer.clear(); invitations.clear(); }
    private Group group(UUID player) { var group = byPlayer.get(player); if (group == null) throw new IllegalStateException("Open /smash join first"); return group; }
    private Group leader(UUID player) {
        var group = group(player); if (!group.leader.equals(player)) throw new IllegalStateException("Only the party leader can do that"); return group;
    }
    private static void editable(Group group) { if (group.phase == Phase.PLAYING) throw new IllegalStateException("Your match is starting or in progress"); }
    private static void idle(Group group) { if (group.phase != Phase.IDLE) throw new IllegalStateException("Cancel selection or matchmaking first"); }
    public void create(UUID player) { var group = leader(player); idle(group); group.party = true; }
    public void invite(UUID leader, UUID target, long now) {
        var group = leader(leader); idle(group);
        if (!group.party) throw new IllegalStateException("Create a party first");
        if (group.members.size() >= 4) throw new IllegalStateException("Party is full");
        if (leader.equals(target) || group.members.containsKey(target)) throw new IllegalStateException("Already in your party");
        var other = group(target); idle(other);
        if (other.party) throw new IllegalStateException("That player is already in a party");
        invites(target, now);
        var pending = invitations.computeIfAbsent(target, k -> new LinkedHashMap<>());
        if (pending.containsKey(group.id)) throw new IllegalStateException("Invitation already sent");
        if (pending.size() >= 8) throw new IllegalStateException("That player has too many invitations");
        pending.put(group.id, new Invite(group.id, leader, group.members.get(leader).name(), now + 2400));
    }
    public List<Invite> invites(UUID player, long now) {
        var pending = invitations.get(player); if (pending == null) return List.of();
        pending.values().removeIf(i -> i.expiresAt() <= now || byPlayer.get(i.leader()) == null
                || !byPlayer.get(i.leader()).id.equals(i.party()) || !byPlayer.get(i.leader()).leader.equals(i.leader()));
        return List.copyOf(pending.values());
    }
    public void decline(UUID player, UUID party) { var pending = invitations.get(player); if (pending != null) pending.remove(party); }
    public void accept(UUID player, UUID party, long now) {
        var invite = invites(player, now).stream().filter(i -> i.party().equals(party)).findFirst().orElseThrow(() -> new IllegalStateException("Invitation expired"));
        var target = group(invite.leader()); var previous = group(player); idle(target); idle(previous);
        if (previous.party || target.members.size() >= 4) throw new IllegalStateException("Party is full or you already joined another party");
        target.members.put(player, previous.members.get(player)); byPlayer.put(player, target); invitations.remove(player);
        if (target.members.size() > Wire.capacity(target.mode)) target.mode = "MATCH";
        reset(target);
    }
    public UUID start(UUID player, String mode) {
        var group = leader(player); editable(group);
        if (group.members.size() > Wire.capacity(mode)) throw new IllegalStateException(Wire.label(mode) + " supports " + Wire.capacity(mode) + " player(s)");
        reset(group); group.mode = mode; group.phase = Phase.SELECTING; return group.round;
    }
    public List<Wire.Ticket> ready(UUID player, UUID round, String fighter) {
        var group = group(player);
        if (group.phase != Phase.SELECTING || !group.round.equals(round)) throw new IllegalStateException("This character selection has ended");
        if (!Wire.CLASSES.contains(fighter)) throw new IllegalArgumentException("Invalid fighter");
        var previous = group.members.get(player);
        group.members.put(player, new Member(player, previous.name(), fighter, true));
        if (group.members.values().stream().anyMatch(m -> !m.ready())) return List.of();
        var tickets = group.members.values().stream().map(m -> new Wire.Ticket(m.id(), m.fighter(), group.mode, UUID.randomUUID(), group.round, group.members.size())).toList();
        group.phase = Phase.QUEUED; return tickets;
    }
    public void reselect(UUID player) { var group = group(player); idle(group); group.phase = Phase.SELECTING; }
    public void change(UUID player) {
        var group = group(player); editable(group);
        if (group.phase != Phase.SELECTING && group.phase != Phase.QUEUED) throw new IllegalStateException("Choose a mode first");
        group.phase = Phase.SELECTING;
        var member = group.members.get(player); group.members.put(player, new Member(player, member.name(), member.fighter(), false));
    }
    public void cancel(UUID player) { var group = group(player); editable(group); reset(group); }
    public void claim(UUID round) {
        byPlayer.values().stream().distinct().filter(g -> g.round.equals(round) && g.phase == Phase.QUEUED).forEach(g -> g.phase = Phase.PLAYING);
    }
    public void finish(UUID round) { byPlayer.values().stream().distinct().filter(g -> g.round.equals(round)).forEach(PartyBook::reset); }
    public void promote(UUID player, UUID member) {
        var group = leader(player); editable(group);
        if (!group.members.containsKey(member)) throw new IllegalStateException("Player is no longer in your party");
        reset(group); group.leader = member;
    }
    public void kick(UUID player, UUID member) {
        var group = leader(player); editable(group);
        if (member.equals(player) || !group.members.containsKey(member)) throw new IllegalStateException("Invalid party member");
        leave(member);
    }
    public void leave(UUID player) { var group = group(player); editable(group); remove(player); }
    public void disconnect(UUID player) { if (byPlayer.containsKey(player)) remove(player); invitations.remove(player); }
    private void remove(UUID player) {
        var group = byPlayer.remove(player); group.members.remove(player);
        if (group.members.isEmpty()) return;
        if (group.leader.equals(player)) group.leader = group.members.keySet().iterator().next();
        if (group.phase != Phase.PLAYING) reset(group);
    }
    private static void reset(Group group) {
        group.phase = Phase.IDLE; group.round = UUID.randomUUID();
        group.members.replaceAll((id, m) -> new Member(id, m.name(), m.fighter(), false));
    }
}
