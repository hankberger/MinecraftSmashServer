package dev.hanks.network;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RankingsTest {
    @TempDir Path dir;
    private static final UUID LOSER=UUID.randomUUID();
    private Wire.MatchResult result(UUID winner,String name,int kos,boolean forfeited,Wire.MatchEvidence evidence){
        var roster=List.of(new Wire.Ticket(winner,"STEVE","DUEL",UUID.randomUUID()),new Wire.Ticket(LOSER,"ZOMBIE","DUEL",UUID.randomUUID()));
        return new Wire.MatchResult(UUID.randomUUID(),"DUEL",winner,roster,List.of(
                new Wire.ResultRow(winner,name,"STEVE",1,1,kos,2,100,forfeited),new Wire.ResultRow(LOSER,"Opponent","ZOMBIE",2,0,2,3,150)),evidence);
    }
    private Rankings.Standing board(PointsStore db,UUID p,Rankings.Board board) throws Exception {return db.rankings(p,System.currentTimeMillis()).boards().get(board);}
    @Test void receiptsAndRankingsCommitOnceAcrossArenaRetryAndRestart() throws Exception {
        var file=dir.resolve("lobby.db");var a=UUID.randomUUID();var match=result(a,"Winner",3,false,null);
        try(var arena=new PointsStore(dir.resolve("arena.db"));var lobby=new PointsStore(file)){
            arena.stage(match);assertTrue(board(arena,a,Rankings.Board.KOS).leaders().isEmpty());
            lobby.settle(arena.pending().getFirst());lobby.settle(match);
            assertEquals(3,board(lobby,a,Rankings.Board.KOS).own().score());
        }
        try(var db=new PointsStore(file)){
            db.settle(match);assertEquals(1,board(db,a,Rankings.Board.ALL_TIME_WINS).own().score());
            assertEquals(3,board(db,a,Rankings.Board.KOS).own().score());
            db.deliver(new PointsStore.StoreDelivery("order:rankings",a,5500,-1,"test"));
            assertEquals(1,board(db,a,Rankings.Board.ALL_TIME_WINS).own().score());
        }
    }
    @Test void tiesShareRankAndOwnRowSurvivesOutsideTopTen() throws Exception {
        var ids=new ArrayList<UUID>();try(var db=new PointsStore(dir.resolve("db"))){
            for(int i=0;i<12;i++){
                var id=UUID.randomUUID();ids.add(id);
                for(int j=0;j<(i<2?3:i<11?2:1);j++)db.settle(result(id,String.format("Player%02d",i),3,false,null));
            }
            var rows=board(db,ids.getLast(),Rankings.Board.ALL_TIME_WINS);
            assertEquals(10,rows.leaders().size());assertEquals(12,rows.players());
            assertEquals(1,rows.leaders().get(0).rank());assertEquals(1,rows.leaders().get(1).rank());assertEquals(3,rows.leaders().get(2).rank());
            assertEquals(12,rows.own().rank());assertEquals(1,rows.own().score());
            assertFalse(rows.leaders().stream().anyMatch(e->e.player().equals(ids.getLast())));
            db.rankingName(ids.getLast(),"Renamed","DROWNED");assertEquals("Renamed",board(db,ids.getLast(),Rankings.Board.ALL_TIME_WINS).own().name());
            assertEquals(12,board(db,ids.getLast(),Rankings.Board.ALL_TIME_WINS).own().rank());
        }
    }
    @Test void weeklyBoundaryIsMondayUtcAndHistoryBackfillsIdempotently() throws Exception {
        long monday=Instant.parse("2026-09-28T00:00:00Z").toEpochMilli();
        assertEquals(monday-7*86_400_000L,Rankings.weekStart(monday-1));assertEquals(monday,Rankings.weekStart(monday));
        var file=dir.resolve("db");var a=UUID.randomUUID();var before=result(a,"Sunday",3,false,null);var after=result(a,"Monday",4,false,null);
        try(var db=new PointsStore(file)){db.settle(before);db.settle(after);}
        // Simulate an existing installation with old settled matches, before rankings tables exist.
        try(var db=DriverManager.getConnection("jdbc:sqlite:"+file);var s=db.createStatement()){
            s.execute("DROP TABLE ranking_scores");s.execute("DROP TABLE ranking_rounds");s.execute("DROP TABLE ranking_names");s.execute("DROP TABLE ranking_totals");
            s.execute("UPDATE settled_matches SET settled_at="+(monday-1)+" WHERE id='"+before.id()+"'");
            s.execute("UPDATE settled_matches SET settled_at="+monday+" WHERE id='"+after.id()+"'");
        }
        for(int i=0;i<2;i++)try(var db=new PointsStore(file)){
            var now=db.rankings(a,monday);assertEquals(monday,now.weekStart());
            assertEquals(1,now.boards().get(Rankings.Board.WEEKLY_WINS).own().score());
            assertEquals(2,now.boards().get(Rankings.Board.ALL_TIME_WINS).own().score());
            assertEquals(7,now.boards().get(Rankings.Board.KOS).own().score());
            assertNull(db.rankings(a,monday+7*86_400_000L).boards().get(Rankings.Board.WEEKLY_WINS).own());
        }
    }
    @Test void forfeitShortInactiveDrawAndUnrankedAreHandled() throws Exception {
        var a=UUID.randomUUID();try(var db=new PointsStore(dir.resolve("db"))){
            db.settle(result(a,"Quit",9,true,null));
            db.settle(result(a,"Short",9,false,new Wire.MatchEvidence(20,Map.of(a,new Wire.Participation(20,20),LOSER,new Wire.Participation(20,20)))));
            db.settle(result(a,"Idle",9,false,new Wire.MatchEvidence(2400,Map.of(a,new Wire.Participation(2400,0),LOSER,new Wire.Participation(2400,2400)))));
            for(var board:Rankings.Board.values())assertNull(board(db,a,board).own());
            var match=result(a,"Draw",3,false,null);db.settle(new Wire.MatchResult(match.id(),"DUEL",null,match.roster(),match.rows()));
            assertNull(board(db,a,Rankings.Board.ALL_TIME_WINS).own());assertEquals(3,board(db,a,Rankings.Board.KOS).own().score());
            assertNull(board(db,UUID.randomUUID(),Rankings.Board.ALL_TIME_WINS).own());
        }
    }
    @Test void receiptFailureRollsBackRankingsToo() throws Exception {
        var file=dir.resolve("db");var a=UUID.randomUUID();var match=result(a,"Winner",3,false,null);
        try(var db=new PointsStore(file);var external=DriverManager.getConnection("jdbc:sqlite:"+file);var sql=external.createStatement()){
            sql.execute("INSERT INTO accounts VALUES('"+a+"',9223372036854775807,9223372036854775807,0,0)");
            assertThrows(ArithmeticException.class,()->db.settle(match));assertTrue(board(db,a,Rankings.Board.KOS).leaders().isEmpty());
            sql.execute("UPDATE accounts SET balance=0,earned=0 WHERE player='"+a+"'");db.settle(match);
            assertEquals(3,board(db,a,Rankings.Board.KOS).own().score());
        }
    }
}
