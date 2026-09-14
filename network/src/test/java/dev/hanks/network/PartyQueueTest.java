package dev.hanks.network;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

final class PartyQueueTest {
    static List<Wire.Ticket> group(String mode, int size) {
        var id = UUID.randomUUID(); var result = new ArrayList<Wire.Ticket>();
        for (int i = 0; i < size; i++) result.add(new Wire.Ticket(UUID.randomUUID(),"STEVE",mode,UUID.randomUUID(),id,size));
        return result;
    }
    @Test void duelPairsSolosOrACompleteTwoPersonParty() {
        var queue = new MatchQueue(); var party = group("DUEL",2); party.forEach(queue::offer);
        assertEquals(party,queue.reserve().roster());
        group("DUEL",1).forEach(queue::offer); assertNull(queue.reserve());
        group("DUEL",1).forEach(queue::offer); assertEquals(2,queue.reserve().roster().size());
    }
    @Test void partialPartyAndMixedModesNeverStart() {
        var queue = new MatchQueue(); var party = group("MATCH",3);
        queue.offer(party.getFirst()); group("MATCH",1).forEach(queue::offer); group("DUEL",1).forEach(queue::offer);
        assertNull(queue.reserve());
        queue.offer(party.get(1)); queue.offer(party.get(2)); var match = queue.reserve();
        assertEquals(4,match.roster().size()); assertTrue(match.roster().containsAll(party)); assertEquals(1,queue.size());
    }
    @Test void twoPairsFillFfaWithoutSplittingOlderThreePersonParty() {
        var queue = new MatchQueue(); var older = group("MATCH",3); older.forEach(queue::offer);
        var first = group("MATCH",2); var second = group("MATCH",2); first.forEach(queue::offer); second.forEach(queue::offer);
        var match = queue.reserve(); assertTrue(match.roster().containsAll(first)); assertTrue(match.roster().containsAll(second));
        assertEquals(older,queue.tickets()); group("MATCH",1).forEach(queue::offer); assertTrue(queue.reserve().roster().containsAll(older));
    }
    @Test void removingOneMemberWithdrawsTheirWholeGroup() {
        var queue = new MatchQueue(); var party = group("MATCH",3); party.forEach(queue::offer); var solo = group("MATCH",1); solo.forEach(queue::offer);
        queue.remove(party.get(1).player()); assertEquals(solo,queue.tickets()); assertNull(queue.reserve());
    }
    @Test void oldestFeasiblePartyWinsAcrossModesAndCombinations() {
        var queue = new MatchQueue(); var old = group("MATCH",1); old.forEach(queue::offer);
        group("MATCH",2).forEach(queue::offer); group("MATCH",2).forEach(queue::offer); group("DUEL",2).forEach(queue::offer); group("MATCH",1).forEach(queue::offer);
        assertEquals(old.getFirst(), queue.reserve().roster().getFirst());
    }
    @Test void reservationsRejectSplitOrConflictingGroups() {
        var group = group("DUEL",2);
        assertThrows(IllegalArgumentException.class, () -> new Wire.Reservation(UUID.randomUUID(),List.of(group.getFirst(),group("DUEL",1).getFirst())));
        var valid = new Wire.Reservation(UUID.randomUUID(),group);
        assertEquals(valid,Wire.JSON.fromJson(Wire.JSON.toJson(valid),Wire.Reservation.class));
    }
}
