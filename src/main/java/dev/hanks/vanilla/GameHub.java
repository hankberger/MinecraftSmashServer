package dev.hanks.vanilla;

import dev.hanks.network.*;
import java.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Lobby UI and party readiness, shared by standalone PLAY and the network lobby. */
public final class GameHub {
    public final PartyBook parties = new PartyBook();
    public final MatchMenu menu = new MatchMenu();
    public final RoundResults results;
    private final VanillaSmash game;
    private final MatchQueue localQueue = new MatchQueue();
    private Wire.Reservation localRound;
    private final Map<UUID, Integer> absentSince = new HashMap<>();
    private final Map<UUID, String> notices = new HashMap<>();
    private final Map<UUID, Integer> noticeUntil = new HashMap<>();
    public GameHub(VanillaSmash game) { this.game = game; results = new RoundResults(game); }
    public void reset() { results.reset(); parties.clear(); absentSince.clear(); notices.clear(); noticeUntil.clear(); localRound = null; for (var t : localQueue.tickets()) localQueue.remove(t.player()); }
    private ServerPlayer player(UUID id) { return game.server.getPlayerList().getPlayer(id); }
    public boolean available(ServerPlayer p) { return !game.arriving(p) && !game.network.arena() && !game.viewers.containsKey(p.getUUID()) && (p.level().dimension().equals(MvpWorlds.LOBBY) || game.stage.active(p)); }
    private PartyBook.View ensure(ServerPlayer p) { return parties.ensure(p.getUUID(), p.getPlainTextName()); }
    private void attempt(ServerPlayer p, Runnable action) {
        try { if (!available(p)) throw new IllegalStateException(game.arriving(p) ? "Arriving in the lobby…" : "Return to the lobby first"); action.run(); }
        catch (IllegalStateException | IllegalArgumentException e) { notice(p.getUUID(), e.getMessage()); if (available(p) && !game.stage.active(p)) open(p); }
    }
    public void notice(UUID id, String text) {
        notices.put(id, text); noticeUntil.put(id, game.ticks + 160);
        var p = player(id); if (p != null) p.sendOverlayMessage(Component.literal(text));
    }
    public String status(ServerPlayer p) {
        if (noticeUntil.getOrDefault(p.getUUID(), 0) > game.ticks) return notices.get(p.getUUID());
        var view = parties.view(p.getUUID());
        if (view != null && view.phase() == PartyBook.Phase.SELECTING) return Wire.label(view.mode()) + " · " + view.readyCount() + "/" + view.members().size() + " ready    /smash join";
        if (view != null && view.phase() == PartyBook.Phase.PLAYING) return "Joining match…";
        if (view != null && view.phase() == PartyBook.Phase.QUEUED && !game.network.enabled()) {
            long count = game.network.selections.tickets().stream().filter(t -> t.mode().equals(view.mode())).count();
            return Wire.label(view.mode()) + " · Queued " + count + "/" + Wire.capacity(view.mode()) + "    /smash unqueue";
        }
        if (!parties.invites(p.getUUID(), game.ticks).isEmpty()) return "Party invitation · /smash join";
        return null;
    }
    public int open(ServerPlayer p) {
        if (!available(p)) { notice(p.getUUID(), game.arriving(p) ? "Arriving in the lobby…" : "/smash leave"); return 1; }
        if (game.stage.active(p)) { game.stage.hint(p); return 1; }
        results.hide(p.getUUID());
        var view = ensure(p); var buttons = new ArrayList<MatchMenu.Button>();
        StringBuilder body = new StringBuilder();
        if (noticeUntil.getOrDefault(p.getUUID(), 0) > game.ticks) body.append(notices.get(p.getUUID())).append("\n\n");
        body.append(view.party() ? "Party · " + view.members().size() + "/4" : "Play solo or invite friends");
        if (view.phase() != PartyBook.Phase.IDLE) body.append("\n").append(Wire.label(view.mode())).append(" · ").append(switch (view.phase()) {
            case SELECTING -> view.readyCount() + "/" + view.members().size() + " ready";
            case QUEUED -> "Queued"; case PLAYING -> "Joining / playing"; default -> "";
        });
        for (var member : view.members()) body.append("\n").append(member.name()).append(member.id().equals(view.leader()) && view.party() ? " ★" : "")
                .append(view.phase() == PartyBook.Phase.IDLE ? "" : member.ready() ? " · Ready · " + FighterClass.valueOf(member.fighter()).label : " · Choosing");
        boolean leader = view.leader().equals(p.getUUID());
        if (view.phase() == PartyBook.Phase.IDLE) {
            if (results.book.result(p.getUUID()) != null) buttons.add(button(p, "Last match", () -> results.show(p)));
            if (leader) {
                if (view.members().size() <= 2) buttons.add(button(p, "1v1", () -> selectMode(p, VanillaSmash.Mode.DUEL)));
                buttons.add(button(p, "Free-for-all", () -> selectMode(p, VanillaSmash.Mode.MATCH)));
                if (view.members().size() == 1) buttons.add(button(p, "Practice", () -> selectMode(p, VanillaSmash.Mode.PRACTICE)));
            } else body.append("\n\nLeader chooses the mode");
            if (!view.party()) buttons.add(button(p, "Create party", () -> { parties.create(p.getUUID()); open(p); }));
            else if (leader) {
                if (view.members().size() < 4) buttons.add(button(p, "Invite player", () -> inviteMenu(p, 0)));
                buttons.add(button(p, "Manage party", () -> manageMenu(p)));
            }
        } else if (view.phase() != PartyBook.Phase.PLAYING) {
            var own = view.members().stream().filter(m -> m.id().equals(p.getUUID())).findFirst().orElseThrow();
            if (view.phase() == PartyBook.Phase.SELECTING && !own.ready() && own.fighter() != null)
                buttons.add(button(p, "Ready as " + FighterClass.valueOf(own.fighter()).label, () -> confirm(p, FighterClass.valueOf(own.fighter()), view.round())));
            buttons.add(button(p, "Change fighter", () -> change(p)));
            buttons.add(button(p, "Cancel matchmaking", () -> { cancel(p, true); open(p); }));
        }
        int inviteCount = parties.invites(p.getUUID(), game.ticks).size();
        if (inviteCount > 0) buttons.add(button(p, "Invitations (" + inviteCount + ")", () -> invitations(p)));
        if (view.party() && view.phase() != PartyBook.Phase.PLAYING) buttons.add(button(p, "Leave party", () -> leaveParty(p)));
        menu.show(p, "Smash", body.toString(), buttons, true, () -> menu.clear(p)); return 1;
    }
    private MatchMenu.Button button(ServerPlayer p, String label, Runnable action) { return new MatchMenu.Button(label, () -> attempt(p, action)); }
    public int click(ServerPlayer p, UUID token, int action) { if (!menu.click(p, token, action) && available(p)) open(p); return 1; }
    private void refresh(PartyBook.View view) {
        if (view == null) return;
        for (var member : view.members()) { var p = player(member.id()); if (p != null && menu.mainOpen(member.id()) && !game.stage.active(p)) open(p); }
    }
    public int selectMode(ServerPlayer p, VanillaSmash.Mode mode) {
        attempt(p, () -> {
            var view = ensure(p);
            if (!view.leader().equals(p.getUUID())) throw new IllegalStateException("Only the party leader chooses the mode");
            if (view.members().size() > Wire.capacity(mode.name())) throw new IllegalStateException(Wire.label(mode.name()) + " cannot fit this party");
            if (view.members().stream().anyMatch(m -> player(m.id()) == null || !available(player(m.id())))) throw new IllegalStateException("Everyone must be in the lobby");
            if (!game.network.cancelSelection(p.getUUID())) throw new IllegalStateException("Your match is already starting");
            view.members().forEach(m -> results.leave(m.id()));
            var round = parties.start(p.getUUID(), mode.name());
            try {
                for (var member : view.members()) {
                    var memberPlayer = player(member.id()); menu.clear(memberPlayer); notices.remove(member.id()); noticeUntil.remove(member.id());
                    game.stage.open(memberPlayer, mode, round);
                }
            } catch (RuntimeException failure) {
                cancel(p, true);
                VanillaSmash.LOG.error("Could not open the party's character stages", failure);
                throw new IllegalStateException("Character selection unavailable; try again");
            }
        }); return 1;
    }
    public void replay(ServerPlayer p, VanillaSmash.Mode mode, boolean change) {
        attempt(p, () -> {
            var view = ensure(p);
            if (!view.leader().equals(p.getUUID()) || view.phase() != PartyBook.Phase.IDLE) throw new IllegalStateException("The party leader starts the next round");
            if (view.members().stream().anyMatch(m -> player(m.id()) == null || !available(player(m.id())))) throw new IllegalStateException("Everyone must be in the lobby");
            if (change) { selectMode(p, mode); return; }
            var round = parties.start(p.getUUID(), mode.name());
            for (var member : view.members()) { results.leave(member.id()); menu.clear(player(member.id())); }
            var own = view.members().stream().filter(m -> m.id().equals(p.getUUID())).findFirst().orElseThrow();
            if (own.fighter() == null) { selectMode(p, mode); return; }
            confirm(p, FighterClass.valueOf(own.fighter()), round);
            if (parties.view(p.getUUID()).phase() == PartyBook.Phase.SELECTING)
                for (var member : view.members()) if (!member.id().equals(p.getUUID())) open(player(member.id()));
        });
    }
    public void confirm(ServerPlayer p, FighterClass fighter, UUID round) {
        attempt(p, () -> {
            var tickets = parties.ready(p.getUUID(), round, fighter.name());
            if (tickets.isEmpty()) { open(p); refresh(parties.view(p.getUUID())); return; }
            if (!game.network.offerSelections(tickets)) {
                parties.cancel(p.getUUID()); throw new IllegalStateException("Matchmaking unavailable; try again");
            }
            for (var ticket : tickets) { menu.clear(player(ticket.player())); game.status(player(ticket.player())); }
            startQueued();
        });
    }
    /** Existing automated stock-client probes still enter through the same ready barrier. */
    public void chooseDirect(ServerPlayer p, FighterClass fighter, VanillaSmash.Mode mode) {
        var view = ensure(p); view.members().forEach(m -> results.leave(m.id()));
        var round = parties.start(p.getUUID(), mode.name()); confirm(p, fighter, round);
    }
    public void change(ServerPlayer p) {
        if (!game.network.cancelSelection(p.getUUID())) throw new IllegalStateException("Your match is already starting");
        if (parties.view(p.getUUID()).phase() == PartyBook.Phase.IDLE) parties.reselect(p.getUUID());
        parties.change(p.getUUID()); var view = parties.view(p.getUUID());
        menu.clear(p); game.stage.open(p, VanillaSmash.Mode.valueOf(view.mode()), view.round()); refresh(view);
    }
    public boolean cancel(ServerPlayer p, boolean showPeers) {
        var view = parties.view(p.getUUID());
        if (!game.network.cancelSelection(p.getUUID())) { notice(p.getUUID(), "Your match is already starting"); return false; }
        if (view == null) return true;
        if (view.phase() == PartyBook.Phase.PLAYING) return false;
        parties.cancel(p.getUUID());
        for (var member : view.members()) {
            var other = player(member.id()); if (other == null) continue;
            if (game.stage.active(other)) { game.stage.close(other); game.returnFromPicker(other); }
            menu.clear(other);
            if (showPeers && !other.getUUID().equals(p.getUUID())) open(other);
        }
        return true;
    }
    public void backFromStage(ServerPlayer p) { if (cancel(p, true)) { game.returnFromPicker(p); open(p); } }
    public void claim(List<Wire.Ticket> tickets) {
        tickets.stream().map(Wire.Ticket::group).distinct().forEach(parties::claim);
        for (var t : tickets) { var p = player(t.player()); if (p != null) menu.clear(p); }
    }
    public void finished(Set<UUID> groups) { groups.forEach(parties::finish); }
    public void disconnected(ServerPlayer p) {
        results.disconnected(p.getUUID()); menu.forget(p.getUUID()); absentSince.put(p.getUUID(), game.ticks);
        if (game.network.selections.claimed(p.getUUID())) { if (!game.network.enabled()) parties.disconnect(p.getUUID()); return; }
        var view = parties.view(p.getUUID());
        if (view != null) { cancel(p, true); parties.disconnect(p.getUUID()); refresh(view); }
    }
    /** Proxy presence distinguishes leaving the network from moving to an arena backend. */
    public void presence(Set<UUID> online) {
        for (var id : parties.players()) {
            if (online.contains(id) || player(id) != null) { absentSince.remove(id); continue; }
            int since = absentSince.computeIfAbsent(id, k -> game.ticks);
            if (game.ticks - since >= 40) { var view = parties.view(id); results.disconnected(id); parties.disconnect(id); absentSince.remove(id); refresh(view); }
        }
    }
    public int partyCommand(ServerPlayer p, String action, String name) {
        attempt(p, () -> {
            ensure(p);
            if (action.equals("create")) { parties.create(p.getUUID()); open(p); }
            else if (action.equals("leave")) leaveParty(p);
            else if (action.equals("invite")) {
                var target = game.server.getPlayerList().getPlayerByName(name);
                if (target == null) throw new IllegalStateException("Player must be online in this lobby");
                invite(p, target);
            } else if (action.equals("accept")) {
                var invite = parties.invites(p.getUUID(), game.ticks).stream().filter(i -> i.name().equalsIgnoreCase(name)).findFirst().orElseThrow(() -> new IllegalStateException("No invitation from that player"));
                accept(p, invite.party());
            } else open(p);
        }); return 1;
    }
    private void invite(ServerPlayer p, ServerPlayer target) {
        if (!available(target)) throw new IllegalStateException("Player must be in the lobby");
        ensure(target); parties.invite(p.getUUID(), target.getUUID(), game.ticks);
        notice(target.getUUID(), p.getPlainTextName() + " invited you · /smash join"); notice(p.getUUID(), "Invitation sent to " + target.getPlainTextName());
        refresh(parties.view(target.getUUID())); open(p);
    }
    private void accept(ServerPlayer p, UUID party) {
        parties.accept(p.getUUID(), party, game.ticks); results.book.leave(p.getUUID()); notices.remove(p.getUUID()); noticeUntil.remove(p.getUUID());
        open(p); refresh(parties.view(p.getUUID()));
    }
    private void invitations(ServerPlayer p) {
        var buttons = new ArrayList<MatchMenu.Button>();
        for (var invite : parties.invites(p.getUUID(), game.ticks)) {
            buttons.add(button(p, "Join " + invite.name(), () -> accept(p, invite.party())));
            buttons.add(button(p, "Decline " + invite.name(), () -> { parties.decline(p.getUUID(), invite.party()); invitations(p); }));
        }
        menu.show(p, "Invitations", buttons.isEmpty() ? "No invitations" : "", buttons, false, () -> open(p));
    }
    private void inviteMenu(ServerPlayer p, int page) {
        var current = ensure(p);
        var candidates = game.server.getPlayerList().getPlayers().stream().filter(this::available)
                .filter(other -> current.members().stream().noneMatch(m -> m.id().equals(other.getUUID())))
                .filter(other -> { var view = ensure(other); return !view.party() && view.phase() == PartyBook.Phase.IDLE; })
                .sorted(Comparator.comparing(ServerPlayer::getPlainTextName)).toList();
        int start = Math.min(page * 10, Math.max(0, candidates.size() - 1));
        var buttons = new ArrayList<MatchMenu.Button>();
        candidates.stream().skip(start).limit(10).forEach(target -> buttons.add(button(p, target.getPlainTextName(), () -> invite(p, target))));
        if (page > 0) buttons.add(button(p, "Previous", () -> inviteMenu(p, page - 1)));
        if (start + 10 < candidates.size()) buttons.add(button(p, "Next", () -> inviteMenu(p, page + 1)));
        menu.show(p, "Invite player", candidates.isEmpty() ? "No available players in the lobby" : "", buttons, false, () -> open(p));
    }
    private void manageMenu(ServerPlayer p) {
        var buttons = new ArrayList<MatchMenu.Button>();
        for (var member : ensure(p).members()) if (!member.id().equals(p.getUUID())) buttons.add(button(p, member.name(), () -> memberMenu(p, member)));
        menu.show(p, "Party", "", buttons, false, () -> open(p));
    }
    private void memberMenu(ServerPlayer p, PartyBook.Member member) {
        menu.show(p, member.name(), "", List.of(
                button(p, "Make leader", () -> { if (!cancel(p, false)) return; parties.promote(p.getUUID(), member.id()); open(p); refresh(parties.view(p.getUUID())); }),
                button(p, "Remove from party", () -> { if (!cancel(p, false)) return; parties.kick(p.getUUID(), member.id()); results.book.leave(member.id()); var other = player(member.id()); if (other != null) open(other); open(p); refresh(parties.view(p.getUUID())); })
        ), false, () -> manageMenu(p));
    }
    private void leaveParty(ServerPlayer p) {
        var previous = ensure(p); if (!cancel(p, true)) return;
        results.book.leave(p.getUUID()); parties.leave(p.getUUID()); open(p); refresh(previous);
    }
    public void startQueued() {
        if (game.network.enabled() || game.battle != null) return;
        var desired = new HashMap<UUID, Wire.Ticket>();
        for (var t : game.network.selections.tickets()) if (!game.network.selections.claimed(t.player()) && player(t.player()) != null) desired.put(t.player(), t);
        for (var t : localQueue.tickets()) if (!t.equals(desired.get(t.player()))) localQueue.remove(t.player());
        for (var t : game.network.selections.tickets()) if (desired.containsKey(t.player())) localQueue.offer(t);
        var reservation = localQueue.reserve(); if (reservation == null) return;
        if (!game.network.selections.claim(reservation)) return;
        localRound = reservation; claim(reservation.roster());
        var players = reservation.roster().stream().map(t -> { game.choices.put(t.player(), FighterClass.valueOf(t.fighter())); return player(t.player()); }).toList();
        game.begin(players, VanillaSmash.Mode.valueOf(reservation.roster().getFirst().mode()));
    }
    public Wire.Reservation reservation() { return localRound; }
    public void endRound() {
        if (localRound == null) return;
        var old = localRound; localRound = null;
        finished(game.network.selections.clear(old.roster()));
        old.roster().forEach(t -> game.match.dequeue(t.player()));
    }
}
