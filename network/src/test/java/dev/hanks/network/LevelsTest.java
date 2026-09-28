package dev.hanks.network;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LevelsTest {
    @TempDir Path dir;
    private static final UUID A=UUID.randomUUID(),B=UUID.randomUUID();
    private Wire.MatchResult match(UUID winner,int kos,boolean forfeit,int duration,int active){
        return new Wire.MatchResult(UUID.randomUUID(),"DUEL",winner,List.of(new Wire.Ticket(A,"STEVE","DUEL",UUID.randomUUID()),new Wire.Ticket(B,"ZOMBIE","DUEL",UUID.randomUUID())),
                List.of(new Wire.ResultRow(A,"First","STEVE",1,1,kos,2,300,forfeit),new Wire.ResultRow(B,"Second","ZOMBIE",2,0,2,3,180)),
                new Wire.MatchEvidence(duration,Map.of(A,new Wire.Participation(duration,active),B,new Wire.Participation(duration,duration))));
    }
    @Test void progressionHasExactBoundariesAndLevelsNeverGetSlowerThanTheCeiling(){
        assertEquals(1,LevelRules.progress(0).level());assertEquals(1,LevelRules.progress(99).level());
        assertEquals(2,LevelRules.progress(100).level());assertEquals(0,LevelRules.progress(100).into());
        assertEquals(700,LevelRules.threshold(5));assertEquals(2700,LevelRules.threshold(10));
        assertEquals(LevelRules.Tier.BRAWLER,LevelRules.progress(700).tier());
        for(int level=1;level<=500;level++){
            long threshold=LevelRules.threshold(level);var p=LevelRules.progress(threshold);
            assertEquals(level,p.level());assertEquals(0,p.into());assertTrue(p.required()<=1500);
            if(level>1)assertEquals(level-1,LevelRules.progress(threshold-1).level());
        }
        assertTrue(LevelRules.progress(Long.MAX_VALUE).fraction()<1);
        assertThrows(IllegalArgumentException.class,()->LevelRules.progress(-1));
    }
    @Test void completionDominatesWithWinAndBoundedKnockoutBonuses(){
        assertEquals(new LevelRules.Reward(100,40,45),LevelRules.reward(match(A,3,false,2400,1200),A));
        assertEquals(new LevelRules.Reward(100,0,30),LevelRules.reward(match(A,3,false,2400,1200),B));
        assertEquals(160,LevelRules.reward(match(null,99,false,2400,1200),A).total());
        assertEquals(140,LevelRules.reward(match(A,-1,false,2400,1200),A).total());
        assertEquals(0,LevelRules.reward(match(A,3,true,2400,1200),A).total());
        assertEquals(0,LevelRules.reward(match(A,3,false,599,599),A).total());
        assertEquals(0,LevelRules.reward(match(A,3,false,2400,99),A).total());
        assertEquals(185,LevelRules.reward(match(A,3,false,600,100),A).total());
    }
    @Test void outboxRetriesAndRestartsCannotDoubleAwardOrTradeCreditsForXp() throws Exception {
        var file=dir.resolve("lobby.db");var result=match(A,3,false,2400,1200);
        try(var arena=new PointsStore(dir.resolve("arena.db"));var lobby=new PointsStore(file)){
            arena.stage(result);assertEquals(0,arena.xp(A));
            lobby.settle(arena.pending().getFirst());lobby.settle(result);
            var receipt=lobby.levelReceipts(result.id()).get(A);assertEquals(0,receipt.before());assertEquals(185,receipt.after());
            assertEquals(185,lobby.xp(A));assertEquals(130,lobby.xp(B));
            lobby.deliver(new PointsStore.StoreDelivery("order:levels",A,5500,System.currentTimeMillis()+100000,"test"));
            lobby.outfit(A,"STEVE","diamond",true);
            assertEquals(185,lobby.xp(A));assertEquals(185,lobby.levelReceipts(result.id()).get(A).total());
        }
        try(var lobby=new PointsStore(file)){
            lobby.settle(result);assertEquals(185,lobby.levels().get(A));assertEquals(130,lobby.xp(B));
            var conflict=new Wire.MatchResult(result.id(),"DUEL",B,result.roster(),result.rows(),result.evidence());
            assertThrows(SQLException.class,()->lobby.settle(conflict));assertEquals(185,lobby.xp(A));
        }
    }
    @Test void failedCurrencyTransactionCannotLeakXpAndCanBeRetried() throws Exception {
        var file=dir.resolve("db");var result=match(A,3,false,2400,1200);
        try(var db=new PointsStore(file);var external=DriverManager.getConnection("jdbc:sqlite:"+file);var sql=external.createStatement()){
            sql.execute("INSERT INTO accounts VALUES('"+A+"',9223372036854775807,9223372036854775807,0,0)");
            assertThrows(ArithmeticException.class,()->db.settle(result));assertEquals(0,db.xp(A));assertTrue(db.levelReceipts(result.id()).isEmpty());
            sql.execute("UPDATE accounts SET balance=0,earned=0 WHERE player='"+A+"'");db.settle(result);assertEquals(185,db.xp(A));
        }
    }
    @Test void historicalAndRollbackEraResultsBackfillOnlyOnce() throws Exception {
        var file=dir.resolve("db");var first=match(A,3,false,2400,1200);var second=match(B,1,false,2400,1200);
        try(var db=new PointsStore(file)){db.settle(first);db.settle(second);}
        try(var db=DriverManager.getConnection("jdbc:sqlite:"+file);var s=db.createStatement()){
            s.execute("DROP TABLE level_receipts");s.execute("DROP TABLE level_rounds");s.execute("DROP TABLE level_accounts");
        }
        for(int i=0;i<2;i++)try(var db=new PointsStore(file)){
            assertEquals(300,db.xp(A));assertEquals(300,db.xp(B));db.settle(first);assertEquals(300,db.xp(A));
        }
    }
    @Test void fourPlayerDrawRewardsEliminatedPlayersButNotAForefeit() throws Exception {
        var ids=List.of(A,B,UUID.randomUUID(),UUID.randomUUID());
        var tickets=ids.stream().map(id->new Wire.Ticket(id,"STEVE","MATCH",UUID.randomUUID())).toList();
        var rows=new ArrayList<Wire.ResultRow>();var activity=new HashMap<UUID,Wire.Participation>();
        for(int i=0;i<4;i++){
            rows.add(new Wire.ResultRow(ids.get(i),"Player"+i,"STEVE",i+1,i==0?0:1,i,3,100,i==3));
            activity.put(ids.get(i),new Wire.Participation(i==0?600:3600,100));
        }
        var result=new Wire.MatchResult(UUID.randomUUID(),"MATCH",null,tickets,rows,new Wire.MatchEvidence(3600,activity));
        try(var db=new PointsStore(dir.resolve("db"))){
            db.settle(result);assertEquals(100,db.xp(A));assertEquals(115,db.xp(B));
            assertEquals(130,db.xp(ids.get(2)));assertEquals(0,db.xp(ids.get(3)));
        }
    }
}
