package dev.hanks.network;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

final class SelectionRegistryTest {
    @Test void cancelBeforeClaimRejectsStaleAssignmentForWholeParty() {
        var registry = new SelectionRegistry(); var party = PartyQueueTest.group("DUEL",2); assertTrue(registry.offer(party));
        assertTrue(registry.cancel(party.getFirst().player()));
        assertFalse(registry.claim(new Wire.Reservation(UUID.randomUUID(),party))); assertTrue(registry.tickets().isEmpty());
    }
    @Test void claimBeforeCancelLocksTheWholeRosterAndIsIdempotent() {
        var registry = new SelectionRegistry(); var party = PartyQueueTest.group("DUEL",2); registry.offer(party);
        var reservation = new Wire.Reservation(UUID.randomUUID(),party);
        assertTrue(registry.claim(reservation)); assertTrue(registry.claim(reservation));
        assertFalse(registry.cancel(party.get(1).player())); assertEquals(party,registry.tickets());
        assertFalse(registry.claim(new Wire.Reservation(UUID.randomUUID(),party)));
        assertEquals(Set.of(party.getFirst().group()),registry.clear(party)); assertTrue(registry.cancel(party.getFirst().player()));
    }
    @Test void oldCleanupCannotEraseNewSelections() {
        var registry = new SelectionRegistry(); var old = PartyQueueTest.group("DUEL",2); registry.offer(old); registry.cancel(old.getFirst().player());
        var group = UUID.randomUUID(); var newer = old.stream().map(t -> new Wire.Ticket(t.player(),"ALEX","DUEL",UUID.randomUUID(),group,2)).toList();
        assertTrue(registry.offer(newer)); assertTrue(registry.clear(old).isEmpty()); assertEquals(newer,registry.tickets());
    }
    @Test void incompleteOrOverlappingGroupsCannotPublishPartially() {
        var registry = new SelectionRegistry(); var group = PartyQueueTest.group("DUEL",2);
        assertFalse(registry.offer(List.of(group.getFirst()))); assertTrue(registry.tickets().isEmpty());
        assertTrue(registry.offer(group)); assertFalse(registry.offer(group)); assertEquals(2,registry.tickets().size());
    }
}
