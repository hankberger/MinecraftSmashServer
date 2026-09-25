package dev.hanks.network;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.*;
import java.sql.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EconomyTest {
    @TempDir Path dir;
    private final UUID a=UUID.randomUUID(),b=UUID.randomUUID();
    private Wire.MatchResult match(int ticks,int aActive,int bActive,boolean quit) {
        var players=List.of(a,b);
        return new Wire.MatchResult(UUID.randomUUID(),"DUEL",a,
                players.stream().map(p->new Wire.Ticket(p,"STEVE","DUEL",UUID.randomUUID())).toList(),
                List.of(new Wire.ResultRow(a,"A","STEVE",1,1,0,2,0),new Wire.ResultRow(b,"B","STEVE",2,0,0,3,0,quit)),
                new Wire.MatchEvidence(ticks,Map.of(a,new Wire.Participation(ticks,aActive),b,new Wire.Participation(ticks,bActive))));
    }
    @Test void shortMatchesAndAfkDoNotPayButQualifiedLossesAndRematchesDo() throws Exception {
        try(var db=new PointsStore(dir.resolve("points.db"))) {
            var shortRound=match(599,100,100,false);db.settle(shortRound);
            assertEquals(0,db.account(a).balance());assertEquals(PointRules.Reason.TOO_SHORT,PointRules.reason(shortRound,a));
            var quit=match(5,5,5,true);db.settle(quit);assertEquals(PointRules.Reason.LEFT_EARLY,PointRules.reason(quit,b));
            var idle=match(600,100,99,false);db.settle(idle);
            assertEquals(75,db.account(a).balance());assertEquals(0,db.account(b).balance());
            assertEquals(PointRules.Reason.INACTIVE,PointRules.reason(idle,b));
            var rematch=match(600,100,100,false);db.settle(rematch);db.settle(rematch);
            assertEquals(150,db.account(a).balance());assertEquals(50,db.account(b).balance());
        }
    }
    @Test void eliminatedPlayerCanEarnWithoutWaitingForWholeMatch() {
        var base=match(6000,100,100,false);
        var finished=new Wire.MatchResult(base.id(),base.mode(),base.winner(),base.roster(),base.rows(),
                new Wire.MatchEvidence(6000,Map.of(a,new Wire.Participation(6000,100),b,new Wire.Participation(200,100))));
        assertEquals(50,PointRules.reward(finished,b).total());
    }
    @Test void legacyOutboxAndPreviouslyPaidReceiptsKeepTheirOriginalRewards() throws Exception {
        var measured=match(5,0,0,false);
        var historical=new Wire.MatchResult(measured.id(),measured.mode(),measured.winner(),measured.roster(),measured.rows());
        var path=dir.resolve("points.db");
        try(var db=new PointsStore(path)){db.stage(historical);}
        try(var db=new PointsStore(path)) {
            db.settle(db.pending().getFirst());db.acknowledge(historical.id());
            assertEquals(75,db.account(a).balance());assertEquals(50,db.account(b).balance());
            assertEquals(0L,db.economyReport().get("measuredRounds"));
            assertEquals(75,db.settle(historical).get(a).total());
        }
    }
    @Test void reportMeasuresEarningPurchasePaceAndNoPurchaseCohortExactlyOnceAcrossRestart() throws Exception {
        var path=dir.resolve("points.db");
        try(var db=new PointsStore(path)) {
            for(int i=0;i<10;i++){var result=match(6000,100,100,false);db.settle(result);db.settle(result);}
            assertEquals(750,db.account(a).balance());
            assertEquals(PointsStore.OutfitResult.PURCHASED,db.outfit(a,"STEVE","diamond",true));
        }
        try(var db=new PointsStore(path)) {
            var report=db.economyReport();assertEquals(10L,report.get("measuredRounds"));assertEquals(2L,report.get("measuredPlayers"));
            assertEquals(1250L,report.get("measuredPointsEarned"));assertEquals(300.0,report.get("averageRoundSeconds"));
            assertEquals(750.0,report.get("pointsPerPlayerMatchHour"));assertEquals(1L,report.get("earnersWithoutPurchase"));
            assertEquals(50.0,report.get("earnersWithoutPurchasePercent"));assertEquals(50.0,report.get("medianFirstPurchaseMatchMinutes"));
            assertEquals(1,report.get("measuredFirstPurchases"));
        }
    }
    @Test void telemetryFailureCannotLeavePointsPartiallyCommitted() throws Exception {
        var path=dir.resolve("points.db");var result=match(600,100,100,false);
        try(var db=new PointsStore(path);var connection=DriverManager.getConnection("jdbc:sqlite:"+path);var sql=connection.createStatement()) {
            sql.execute("CREATE TRIGGER fail_metrics BEFORE INSERT ON economy_participation BEGIN SELECT RAISE(ABORT,'fixture'); END");
            assertThrows(SQLException.class,()->db.settle(result));assertEquals(0,db.account(a).balance());assertTrue(db.receipts(result.id()).isEmpty());
            assertEquals(0L,db.economyReport().get("measuredRounds"));
            sql.execute("DROP TRIGGER fail_metrics");db.settle(result);assertEquals(75,db.account(a).balance());
        }
    }
    @Test void invalidEvidenceCannotInventParticipation() {
        assertThrows(IllegalArgumentException.class,()->new Wire.Participation(5,6));
        assertThrows(IllegalArgumentException.class,()->new Wire.MatchEvidence(5,Map.of(a,new Wire.Participation(6,1))));
        var result=match(600,100,100,false);
        assertThrows(IllegalArgumentException.class,()->new Wire.MatchResult(result.id(),result.mode(),a,result.roster(),result.rows(),new Wire.MatchEvidence(600,Map.of())));
        assertEquals(result,Wire.JSON.fromJson(Wire.JSON.toJson(result),Wire.MatchResult.class));
    }
}
