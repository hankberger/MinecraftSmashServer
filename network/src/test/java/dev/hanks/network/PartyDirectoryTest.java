package dev.hanks.network;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

final class PartyDirectoryTest {
    @Test void findsOneFriendAmongHundredsAndRanksExactThenPrefixThenContains(){
        var people=new ArrayList<PartyDirectory.Contact>();var self=UUID.randomUUID();
        for(int i=0;i<500;i++)people.add(new PartyDirectory.Contact(UUID.randomUUID(),"Player"+i,"STEVE"));
        var found=PartyDirectory.search(people,self,"pLaYeR499");assertEquals(1,found.size());assertEquals("Player499",found.getFirst().name());
        for(String name:List.of("TheMoss","Mossy","Moss"))people.add(new PartyDirectory.Contact(UUID.randomUUID(),name,"ALEX"));
        people.add(new PartyDirectory.Contact(self,"MossSelf","STEVE"));
        assertEquals(List.of("Moss","Mossy","TheMoss"),PartyDirectory.search(people,self,"moss").stream().map(PartyDirectory.Contact::name).toList());
        assertTrue(PartyDirectory.search(people,self,"").isEmpty());
        assertThrows(IllegalArgumentException.class,()->PartyDirectory.query("foo\nbar"));
        assertThrows(IllegalArgumentException.class,()->PartyDirectory.query("x".repeat(17)));
        assertEquals("_KYOKII",PartyDirectory.query(" _KYOKII "));
    }
    @Test void recentPlayersDeduplicateByIdentityAndStayBounded(){
        var directory=new PartyDirectory();var self=UUID.randomUUID();var friend=UUID.randomUUID();
        directory.met(self,List.of(new PartyDirectory.Contact(self,"Self","STEVE"),new PartyDirectory.Contact(friend,"OldName","ALEX")));
        directory.met(self,List.of(new PartyDirectory.Contact(friend,"NewName","DROWNED")));
        assertEquals(List.of(new PartyDirectory.Contact(friend,"NewName","DROWNED")),directory.recent(self));
        for(int i=0;i<40;i++)directory.met(self,List.of(new PartyDirectory.Contact(UUID.randomUUID(),"Player"+i,"STEVE")));
        assertEquals(24,directory.recent(self).size());assertEquals("Player39",directory.recent(self).getFirst().name());
    }
    @Test void invitingDuringSelectionKeepsPicksAndReadinessUntilAccepted(){
        var book=new PartyBook();var owner=UUID.randomUUID();var member=UUID.randomUUID();var invited=UUID.randomUUID();
        book.ensure(owner,"Owner");book.ensure(member,"Member");book.ensure(invited,"Invited");
        book.invite(owner,member,0);book.accept(member,book.view(owner).id(),1);
        var round=book.start(owner,"MATCH");book.ready(member,round,"ALEX");book.preview(owner,"DROWNED");
        book.invite(owner,invited,2);
        assertEquals(round,book.view(owner).round());assertEquals(1,book.view(owner).readyCount());assertTrue(book.sent(owner,invited,3));
        assertThrows(IllegalStateException.class,()->book.invite(owner,invited,3));
        book.accept(invited,book.view(owner).id(),3);
        assertEquals(0,book.view(owner).readyCount());assertEquals("DROWNED",book.view(owner).members().getFirst().fighter());
        assertEquals(3,book.view(owner).members().size());assertFalse(book.sent(owner,invited,4));
    }
    @Test void queuedOrPlayingPartiesCannotInviteOrAccept(){
        var book=new PartyBook();var a=UUID.randomUUID();var b=UUID.randomUUID();
        book.ensure(a,"A");book.ensure(b,"B");book.invite(a,b,0);
        var round=book.start(a,"DUEL");book.ready(a,round,"STEVE");
        assertThrows(IllegalStateException.class,()->book.accept(b,book.view(a).id(),1));
        assertThrows(IllegalStateException.class,()->book.invite(a,b,1));
        assertEquals(1,book.view(a).members().size());book.claim(round);
        assertThrows(IllegalStateException.class,()->book.accept(b,book.view(a).id(),2));
    }
    @Test void outgoingInvitesExpireAndLeadershipChangesInvalidateThem(){
        var book=new PartyBook();var owner=UUID.randomUUID();var member=UUID.randomUUID();var invited=UUID.randomUUID();
        book.ensure(owner,"Owner");book.ensure(member,"Member");book.ensure(invited,"Invited");
        book.invite(owner,member,0);book.accept(member,book.view(owner).id(),1);
        book.invite(owner,invited,10);assertTrue(book.sent(owner,invited,2409));assertFalse(book.sent(owner,invited,2410));
        book.invite(owner,invited,2411);book.promote(owner,member);
        assertFalse(book.sent(member,invited,2412));assertTrue(book.invites(invited,2412).isEmpty());
    }
}
