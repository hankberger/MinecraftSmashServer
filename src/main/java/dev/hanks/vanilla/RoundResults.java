package dev.hanks.vanilla;

import dev.hanks.network.*;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;

/** Results live in the lobby so voting never holds an arena or prevents deployment drains. */
public final class RoundResults {
    public final ResultBook book = new ResultBook();
    public final WinnerStage scene;
    public final ResultMenu menu;
    private final VanillaSmash game;
    private final Set<UUID> pending = new HashSet<>();
    private final Map<UUID, String> visible = new HashMap<>();
    public RoundResults(VanillaSmash game) { this.game = game; scene = new WinnerStage(game); menu = new ResultMenu(game); }
    public void reset() { scene.closeAll(); book.clear(); pending.clear(); visible.clear(); }
    private ServerPlayer player(UUID id) { return game.server.getPlayerList().getPlayer(id); }
    public void receive(Wire.MatchResult result) {
        if (book.receive(result, game.ticks)) {
            if(!game.network.enabled())game.points.record(result);
            result.rows().forEach(r -> pending.add(r.player()));
        }
    }
    public void leave(UUID id) { scene.close(id,true); book.forget(id); pending.remove(id); visible.remove(id); }
    public void disconnected(UUID id) { scene.close(id,false); book.leave(id); pending.remove(id); visible.remove(id); }
    public void tick() {
        scene.tick();
        if (game.ticks % 10 != 0) return;
        book.expire(game.ticks);
        for (var id : new HashSet<>(pending)) {
            if (book.result(id) == null) { pending.remove(id); continue; }
            var p = player(id); var view = game.hub.parties.view(id);
            if (p != null && game.hub.available(p) && !game.stage.active(p) && view != null && view.phase() == PartyBook.Phase.IDLE) {
                pending.remove(id); show(p);
            }
        }
        for (var id : new HashSet<>(visible.keySet())) {
            var p = player(id); var result = book.result(id);
            if (result == null) scene.close(id,true);
            if (p == null || result == null || !game.hub.available(p) || game.stage.active(p)) { visible.remove(id); continue; }
            if (!signature(result).equals(visible.get(id))) show(p);
        }
    }
    private String signature(Wire.MatchResult r) { return book.open(r.id(), game.ticks) + ":" + book.votes(r.id())
            + ":" + r.rows().stream().map(row->game.points.receipt(r.id(),row.player())).toList(); }
    public boolean show(ServerPlayer p) {
        var r = book.result(p.getUUID()); if (r == null) return false;
        pending.remove(p.getUUID()); visible.put(p.getUUID(), signature(r));
        String winner = r.rows().stream().filter(row -> row.player().equals(r.winner())).map(Wire.ResultRow::name).findFirst().orElse(null);
        var body = new StringBuilder(game.points.reward(r,p.getUUID())+"  ·  "+game.points.balance(p.getUUID())+"\n\n");
        var rows = r.rows().stream().sorted(Comparator.comparing((Wire.ResultRow row) -> !row.player().equals(r.winner()))
                .thenComparing(Comparator.comparingInt(Wire.ResultRow::stocks).reversed()).thenComparing(Comparator.comparingInt(Wire.ResultRow::knockouts).reversed())).toList();
        for (var row : rows) {
            if (!body.isEmpty()) body.append("\n\n");
            body.append("P").append(row.slot()).append(" · ").append(row.name()).append(row.player().equals(p.getUUID()) ? " (You)" : "")
                .append("\n").append(row.knockouts()).append(" KOs  ·  ").append(row.damage()).append("% dealt  ·  ").append(row.falls()).append(" falls");
        }
        var buttons = new ArrayList<MatchMenu.Button>();
        if (book.open(r.id(), game.ticks)) {
            body.append("\n\nRematch · ").append(book.votes(r.id())).append("/").append(r.rows().size()).append(" ready");
            if (!book.voted(r.id(), p.getUUID())) buttons.add(new MatchMenu.Button("Rematch", () -> rematch(p, r.id())));
            else buttons.add(new MatchMenu.Button("Cancel rematch", () -> { book.leave(p.getUUID()); show(p); }));
        } else body.append("\n\nRematch closed");
        var party = game.hub.parties.view(p.getUUID());
        if (party != null && party.phase() == PartyBook.Phase.IDLE && party.leader().equals(p.getUUID())) {
            buttons.add(new MatchMenu.Button("Play again", () -> replay(p, false)));
            buttons.add(new MatchMenu.Button("Change fighter", () -> replay(p, true)));
        } else if (party != null && party.phase() == PartyBook.Phase.IDLE) {
            buttons.add(new MatchMenu.Button("Party", () -> { dismiss(p); game.hub.partyPanel(p); }));
        }
        String voteText = book.open(r.id(), game.ticks) ? "Rematch · " + book.votes(r.id()) + "/" + r.rows().size() + " ready" : "Rematch closed";
        try { scene.show(p,r,buttons,voteText); }
        catch (RuntimeException failure) {
            VanillaSmash.LOG.error("Could not open winner stage for {}",p.getUUID(),failure);
            game.hub.menu.show(p, winner == null ? "Draw" : winner + " wins!", body.toString(), buttons, false,
                    () -> { dismiss(p); game.hub.menu.clear(p); });
        }
        return true;
    }
    public void hide(UUID id) { scene.close(id,true); visible.remove(id); pending.remove(id); }
    public void dismiss(ServerPlayer p) { scene.close(p,true); book.leave(p.getUUID()); visible.remove(p.getUUID()); pending.remove(p.getUUID()); }
    public void rematch(ServerPlayer p, UUID match) {
        try {
            var r = book.result(p.getUUID());
            if (r == null || !r.id().equals(match) || !game.hub.available(p)) throw new IllegalStateException("Rematch has ended");
            // Every original party must still have the same members, with nobody selecting or queued elsewhere.
            var groups = r.roster().stream().collect(java.util.stream.Collectors.groupingBy(Wire.Ticket::group));
            for (var group : groups.values()) {
                var expected = new HashSet<>(group.stream().map(Wire.Ticket::player).toList());
                for (var ticket : group) {
                    var other = player(ticket.player()); var party = game.hub.parties.view(ticket.player());
                    if (other == null || !game.hub.available(other) || game.stage.active(other) || party == null || party.phase() != PartyBook.Phase.IDLE
                            || !new HashSet<>(party.members().stream().map(PartyBook.Member::id).toList()).equals(expected))
                        throw new IllegalStateException("Everyone must return with their original party");
                }
            }
            if (book.vote(match, p.getUUID(), game.ticks)) {
                var tickets = new ArrayList<Wire.Ticket>(); var started = new ArrayList<UUID>();
                try {
                    for (var group : groups.values()) {
                        var party = game.hub.parties.view(group.getFirst().player());
                        UUID round = game.hub.parties.start(party.leader(), r.mode()); started.add(party.leader());
                        for (var old : group) tickets.addAll(game.hub.parties.ready(old.player(), round, old.fighter()).stream().map(t -> t.forRematch(match)).toList());
                    }
                    if (!game.network.offerSelections(tickets)) throw new IllegalStateException("Matchmaking unavailable; try Play again");
                    for (var row : r.rows()) { leave(row.player()); game.hub.menu.clear(player(row.player())); }
                    game.hub.startQueued();
                } catch (RuntimeException e) { started.forEach(id -> game.hub.parties.cancel(id)); throw e; }
            } else show(p);
        } catch (IllegalStateException | IllegalArgumentException e) { game.hub.notice(p.getUUID(), e.getMessage()); show(p); }
    }
    public void replay(ServerPlayer p, boolean change) {
        var r = book.result(p.getUUID()); if (r == null) return;
        game.hub.replay(p, VanillaSmash.Mode.valueOf(r.mode()), change);
    }
}
