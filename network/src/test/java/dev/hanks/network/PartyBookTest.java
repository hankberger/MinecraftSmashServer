package dev.hanks.network;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

final class PartyBookTest {
    private final PartyBook book = new PartyBook();
    private final UUID leader = UUID.randomUUID(), friend = UUID.randomUUID();
    private void party() {
        book.ensure(leader, "Leader"); book.ensure(friend, "Friend"); book.create(leader);
        book.invite(leader, friend, 0); book.accept(friend, book.view(leader).id(), 1);
    }
    @Test void invitationRequiresAcceptanceAndExpires() {
        book.ensure(leader,"Leader"); book.ensure(friend,"Friend"); book.create(leader); book.invite(leader, friend, 0);
        assertEquals(1, book.view(leader).members().size()); assertEquals(1, book.invites(friend, 2399).size());
        assertThrows(IllegalStateException.class, () -> book.accept(friend, book.view(leader).id(), 2400));
        assertEquals(1, book.view(leader).members().size());
    }
    @Test void leaderStartsAndEveryMemberMustConfirm() {
        party(); assertThrows(IllegalStateException.class, () -> book.start(friend, "DUEL"));
        var round = book.start(leader,"DUEL"); assertTrue(book.ready(leader,round,"ALEX").isEmpty());
        assertEquals(PartyBook.Phase.SELECTING, book.view(leader).phase());
        var tickets = book.ready(friend,round,"ZOMBIE"); assertEquals(2,tickets.size()); assertTrue(Wire.completeGroup(tickets));
        assertEquals(Set.of("ALEX","ZOMBIE"), new HashSet<>(tickets.stream().map(Wire.Ticket::fighter).toList()));
        assertEquals(PartyBook.Phase.QUEUED, book.view(friend).phase());
        assertThrows(IllegalStateException.class, () -> book.ready(friend,round,"STEVE"));
    }
    @Test void staleCharacterChoiceCannotReadyNewMode() {
        party(); var old = book.start(leader,"DUEL"); book.ready(friend,old,"STEVE");
        var current = book.start(leader,"MATCH");
        assertThrows(IllegalStateException.class, () -> book.ready(leader,old,"STEVE"));
        assertEquals(0,book.view(friend).readyCount()); assertNotEquals(old,current);
    }
    @Test void changingFighterRequiresNewConfirmationAndNewTicketIds() {
        party(); var round = book.start(leader,"MATCH"); book.ready(leader,round,"STEVE"); var old = book.ready(friend,round,"ALEX");
        book.change(friend); assertEquals(1,book.view(leader).readyCount());
        var current = book.ready(friend,round,"SKELETON"); assertNotEquals(old.getFirst().selection(), current.getFirst().selection());
        assertEquals("SKELETON",current.get(1).fighter());
    }
    @Test void leavingWhileChoosingResetsAllReadinessAndPromotesLeader() {
        party(); var round = book.start(leader,"MATCH"); book.ready(friend,round,"ALEX"); book.leave(leader);
        assertEquals(friend,book.view(friend).leader()); assertEquals(PartyBook.Phase.IDLE,book.view(friend).phase());
        assertEquals(0,book.view(friend).readyCount()); assertNull(book.view(leader));
    }
    @Test void claimedMatchPreventsChangesUntilItEnds() {
        party(); var round = book.start(leader,"DUEL"); book.ready(leader,round,"STEVE"); book.ready(friend,round,"ALEX"); book.claim(round);
        assertThrows(IllegalStateException.class, () -> book.change(friend));
        assertThrows(IllegalStateException.class, () -> book.cancel(leader));
        assertThrows(IllegalStateException.class, () -> book.leave(friend));
        book.disconnect(leader); assertEquals(PartyBook.Phase.PLAYING,book.view(friend).phase());
        book.finish(round); assertEquals(PartyBook.Phase.IDLE,book.view(friend).phase()); assertEquals(friend,book.view(friend).leader());
    }
    @Test void partyHasFourSlotsAndDuelRejectsThree() {
        party();
        for (int i = 0; i < 2; i++) { var id = UUID.randomUUID(); book.ensure(id,"Extra"+i); book.invite(leader,id,0); book.accept(id,book.view(leader).id(),1); }
        assertThrows(IllegalStateException.class, () -> book.start(leader,"DUEL"));
        var extra = UUID.randomUUID(); book.ensure(extra,"Fifth");
        assertThrows(IllegalStateException.class, () -> book.invite(leader,extra,0));
        assertEquals(4,book.view(leader).members().size()); assertNotNull(book.start(leader,"MATCH"));
    }
    @Test void membersCannotInviteKickOrPromoteAndOldInvitesDieWithLeadership() {
        party(); var third = UUID.randomUUID(); book.ensure(third,"Third"); book.invite(leader,third,0);
        assertThrows(IllegalStateException.class, () -> book.invite(friend,third,0));
        assertThrows(IllegalStateException.class, () -> book.kick(friend,leader));
        assertThrows(IllegalStateException.class, () -> book.promote(friend,friend));
        book.promote(leader,friend); assertTrue(book.invites(third,1).isEmpty());
        book.kick(friend,leader); assertNull(book.view(leader));
    }
    @Test void acceptingTwoInvitesCannotMergePartiesOrExceedCapacity() {
        party(); var other = UUID.randomUUID(); var target = UUID.randomUUID();
        book.ensure(other,"Other"); book.create(other); book.ensure(target,"Target");
        book.invite(leader,target,0); book.invite(other,target,0);
        book.accept(target,book.view(leader).id(),1);
        assertThrows(IllegalStateException.class, () -> book.accept(target,book.view(other).id(),1));
        assertEquals(3,book.view(leader).members().size()); assertEquals(1,book.view(other).members().size());
    }
}
