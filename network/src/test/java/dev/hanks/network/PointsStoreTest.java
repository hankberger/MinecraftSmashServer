package dev.hanks.network;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PointsStoreTest {
    @TempDir Path dir;
    private Wire.MatchResult match(List<UUID> players,UUID winner,UUID forfeited) {
        String mode=players.size()==2?"DUEL":"MATCH";
        var roster=players.stream().map(p->new Wire.Ticket(p,"STEVE",mode,UUID.randomUUID())).toList();
        var rows=players.stream().map(p->new Wire.ResultRow(p,"Fighter","STEVE",players.indexOf(p)+1,p.equals(winner)?1:0,1,2,100,p.equals(forfeited))).toList();
        return new Wire.MatchResult(UUID.randomUUID(),mode,winner,roster,rows);
    }
    @Test void winnerAndLoserPersistAndDuplicateDeliveryNeverPaysTwice() throws Exception {
        var a=UUID.randomUUID();var b=UUID.randomUUID();var match=match(List.of(a,b),a,null);var file=dir.resolve("points.db");
        try(var db=new PointsStore(file)) {
            var receipt=db.settle(match);assertEquals(75,receipt.get(a).total());assertEquals(50,receipt.get(b).total());
            assertEquals(receipt,db.settle(match));
        }
        try(var db=new PointsStore(file)) {
            assertEquals(new PointsStore.Account(75,75,1,1),db.account(a));
            assertEquals(new PointsStore.Account(50,50,1,0),db.account(b));
            db.settle(Wire.JSON.fromJson(Wire.JSON.toJson(match),Wire.MatchResult.class));
            assertEquals(75,db.account(a).balance());
            db.settle(match(List.of(a,b),b,null));
            assertEquals(new PointsStore.Account(125,125,2,1),db.account(a));
            assertEquals(new PointsStore.Account(125,125,2,1),db.account(b));
        }
    }
    @Test void ffaDrawAndEarlyExitRules() throws Exception {
        var p=java.util.stream.IntStream.range(0,4).mapToObj(i->UUID.randomUUID()).toList();
        try(var db=new PointsStore(dir.resolve("points.db"))) {
            db.settle(match(p,p.get(0),p.get(3)));
            assertEquals(75,db.account(p.get(0)).balance());assertEquals(50,db.account(p.get(1)).balance());
            assertEquals(PointsStore.Account.EMPTY,db.account(p.get(3)));
            db.settle(match(p,null,null));
            assertEquals(125,db.account(p.get(0)).balance());assertEquals(50,db.account(p.get(3)).balance());
            assertEquals(0,db.account(p.get(3)).wins());
        }
    }
    @Test void failedGroupTransactionDoesNotPartiallyPayAndCanBeRetried() throws Exception {
        var a=UUID.randomUUID();var b=UUID.randomUUID();var match=match(List.of(a,b),a,null);var file=dir.resolve("points.db");
        try(var db=new PointsStore(file);var external=DriverManager.getConnection("jdbc:sqlite:"+file);var sql=external.createStatement()) {
            sql.execute("INSERT INTO accounts VALUES('"+b+"',9223372036854775807,9223372036854775807,0,0)");
            assertThrows(ArithmeticException.class,()->db.settle(match));
            assertEquals(PointsStore.Account.EMPTY,db.account(a));assertTrue(db.receipts(match.id()).isEmpty());
            sql.execute("UPDATE accounts SET balance=0,earned=0 WHERE player='"+b+"'");
            db.settle(match);assertEquals(75,db.account(a).balance());assertEquals(50,db.account(b).balance());
        }
    }
    @Test void duplicateMessagesFromDifferentWorkersAreSerialized() throws Exception {
        var players=List.of(UUID.randomUUID(),UUID.randomUUID());var match=match(players,players.getFirst(),null);
        try(var db=new PointsStore(dir.resolve("points.db"));var jobs=Executors.newVirtualThreadPerTaskExecutor()) {
            var writes=new ArrayList<Future<?>>();for(int i=0;i<20;i++)writes.add(jobs.submit(()->{try{db.settle(match);}catch(Exception e){throw new RuntimeException(e);}}));
            for(var write:writes)write.get();assertEquals(75,db.account(players.getFirst()).balance());assertEquals(1,db.account(players.getFirst()).matches());
        }
    }
    @Test void conflictingResultIdFailsWithoutChangingAccounts() throws Exception {
        var a=UUID.randomUUID();var b=UUID.randomUUID();var first=match(List.of(a,b),a,null);
        var conflict=new Wire.MatchResult(first.id(),first.mode(),b,first.roster(),first.rows());
        try(var db=new PointsStore(dir.resolve("points.db"))) {
            db.stage(first);assertThrows(SQLException.class,()->db.stage(conflict));
            db.settle(first);assertThrows(SQLException.class,()->db.settle(conflict));assertEquals(75,db.account(a).balance());
        }
    }
    @Test void arenaOutboxSurvivesRestartsUntilLobbyCommitIsAcknowledged() throws Exception {
        var a=UUID.randomUUID();var b=UUID.randomUUID();var result=match(List.of(a,b),a,null);
        var arenaFile=dir.resolve("arena.db");var lobbyFile=dir.resolve("lobby.db");
        try(var arena=new PointsStore(arenaFile)){arena.stage(result);assertEquals(0,arena.account(a).balance());}
        try(var arena=new PointsStore(arenaFile);var lobby=new PointsStore(lobbyFile)) {
            assertEquals(List.of(result),arena.pending());lobby.settle(arena.pending().getFirst());
            // Simulate coordinator failure after payment but before acknowledgment.
        }
        try(var arena=new PointsStore(arenaFile);var lobby=new PointsStore(lobbyFile)) {
            lobby.settle(arena.pending().getFirst());arena.acknowledge(result.id());arena.acknowledge(result.id());
            assertEquals(75,lobby.account(a).balance());assertTrue(arena.pending().isEmpty());
        }
        try(var arena=new PointsStore(arenaFile)){assertTrue(arena.pending().isEmpty());}
    }
    @Test void corruptDatabaseIsNotSilentlyReplaced() throws Exception {
        var file=dir.resolve("points.db");byte[] contents="existing damaged database".getBytes();Files.write(file,contents);
        assertThrows(SQLException.class,()->new PointsStore(file));assertArrayEquals(contents,Files.readAllBytes(file));
    }
    @Test void trainingCannotBecomeAnAwardableResult() {
        var id=UUID.randomUUID();var ticket=new Wire.Ticket(id,"STEVE","PRACTICE",UUID.randomUUID());
        assertThrows(IllegalArgumentException.class,()->new Wire.MatchResult(UUID.randomUUID(),"PRACTICE",id,List.of(ticket),List.of(new Wire.ResultRow(id,"Player","STEVE",1,3,5,0,100))));
    }
}
