package dev.hanks.network;

import java.sql.*;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/** Derived from committed match results, in the same transaction as their receipts. */
public final class Rankings {
    public enum Board {
        WEEKLY_WINS("Weekly wins", "wins"), ALL_TIME_WINS("All-time wins", "wins"), KOS("KOs", "KOs");
        public final String label, unit;
        Board(String label,String unit){this.label=label;this.unit=unit;}
        public String unit(long score){return score==1?(this==KOS?"KO":"win"):unit;}
    }
    public record Entry(UUID player,String name,String fighter,long score,long rank) {}
    public record Standing(List<Entry> leaders,Entry own,long players) {
        public Standing { leaders=List.copyOf(leaders); }
    }
    public record Snapshot(long weekStart,Map<Board,Standing> boards) {
        public Snapshot { boards=Map.copyOf(boards); }
    }
    public static long weekStart(long now) {
        return Instant.ofEpochMilli(now).atZone(ZoneOffset.UTC).toLocalDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
    }
    static void initialize(Connection db) throws SQLException {
        try(var sql=db.createStatement()) {
            sql.execute("CREATE TABLE IF NOT EXISTS ranking_rounds (match_id TEXT PRIMARY KEY REFERENCES settled_matches(id), settled_at INTEGER NOT NULL)");
            sql.execute("CREATE TABLE IF NOT EXISTS ranking_scores (match_id TEXT NOT NULL REFERENCES ranking_rounds(match_id), player TEXT NOT NULL, wins INTEGER NOT NULL, kos INTEGER NOT NULL, PRIMARY KEY(match_id,player))");
            sql.execute("CREATE INDEX IF NOT EXISTS ranking_scores_player ON ranking_scores(player)");
            sql.execute("CREATE INDEX IF NOT EXISTS ranking_rounds_time ON ranking_rounds(settled_at)");
            sql.execute("CREATE TABLE IF NOT EXISTS ranking_names (player TEXT PRIMARY KEY, name TEXT NOT NULL, fighter TEXT NOT NULL, seen_at INTEGER NOT NULL)");
            sql.execute("CREATE TABLE IF NOT EXISTS ranking_totals (period INTEGER NOT NULL, player TEXT NOT NULL, wins INTEGER NOT NULL, kos INTEGER NOT NULL, PRIMARY KEY(period,player))");
        }
        // Backfill historical receipts once, preserving their original settlement dates.
        db.setAutoCommit(false);
        try {
            try(var sql=db.createStatement();var rows=sql.executeQuery("SELECT result,settled_at FROM settled_matches WHERE id NOT IN (SELECT match_id FROM ranking_rounds) ORDER BY settled_at")) {
                while(rows.next())record(db,Wire.JSON.fromJson(rows.getString(1),Wire.MatchResult.class),rows.getLong(2));
            }
            db.commit();
        }catch(SQLException|RuntimeException e){db.rollback();throw e;}finally{db.setAutoCommit(true);}
    }
    static void record(Connection db,Wire.MatchResult match,long now) throws SQLException {
        try(var sql=db.prepareStatement("INSERT OR IGNORE INTO ranking_rounds(match_id,settled_at) VALUES(?,?)")) {
            sql.setString(1,match.id().toString());sql.setLong(2,now);if(sql.executeUpdate()==0)return;
        }
        for(var row:match.rows()) {
            remember(db,row.player(),row.name(),row.fighter(),now);
            if(PointRules.reason(match,row.player())!=PointRules.Reason.EARNED)continue;
            try(var sql=db.prepareStatement("INSERT INTO ranking_scores(match_id,player,wins,kos) VALUES(?,?,?,?)")) {
                sql.setString(1,match.id().toString());sql.setString(2,row.player().toString());
                sql.setInt(3,row.player().equals(match.winner())?1:0);sql.setInt(4,Math.max(0,row.knockouts()));sql.executeUpdate();
            }
            // Small per-player totals make reads independent of the number of historical matches.
            for(long period:new long[]{-1,weekStart(now)})try(var sql=db.prepareStatement("INSERT INTO ranking_totals(period,player,wins,kos) VALUES(?,?,?,?) ON CONFLICT(period,player) DO UPDATE SET wins=wins+excluded.wins,kos=kos+excluded.kos")) {
                sql.setLong(1,period);sql.setString(2,row.player().toString());sql.setInt(3,row.player().equals(match.winner())?1:0);sql.setInt(4,Math.max(0,row.knockouts()));sql.executeUpdate();
            }
        }
    }
    static void remember(Connection db,UUID player,String name,String fighter,long now) throws SQLException {
        try(var sql=db.prepareStatement("INSERT INTO ranking_names(player,name,fighter,seen_at) VALUES(?,?,?,?) ON CONFLICT(player) DO UPDATE SET name=excluded.name,fighter=excluded.fighter,seen_at=excluded.seen_at WHERE excluded.seen_at>=ranking_names.seen_at")) {
            sql.setString(1,player.toString());sql.setString(2,name);sql.setString(3,fighter);sql.setLong(4,now);sql.executeUpdate();
        }
    }
    static Snapshot read(Connection db,UUID player,long now) throws SQLException {
        long start=weekStart(now);var boards=new EnumMap<Board,Standing>(Board.class);
        for(var board:Board.values()) {
            String score=board==Board.KOS?"kos":"wins";
            // Equal scores share a competition rank. Names and UUIDs only order tied rows.
            String query="WITH totals AS (SELECT player,"+score+" AS score FROM ranking_totals WHERE period=? AND "+score+">0), ranked AS (SELECT t.*,n.name,n.fighter,RANK() OVER(ORDER BY t.score DESC) AS position,ROW_NUMBER() OVER(ORDER BY t.score DESC,n.name COLLATE NOCASE,t.player) AS place,COUNT(*) OVER() AS players FROM totals t JOIN ranking_names n ON n.player=t.player) SELECT * FROM ranked WHERE place<=10 OR player=? ORDER BY place";
            var leaders=new ArrayList<Entry>();Entry own=null;long players=0;
            try(var sql=db.prepareStatement(query)) {
                sql.setLong(1,board==Board.WEEKLY_WINS?start:-1);sql.setString(2,player.toString());
                try(var rows=sql.executeQuery()){while(rows.next()){
                    var entry=new Entry(UUID.fromString(rows.getString("player")),rows.getString("name"),rows.getString("fighter"),rows.getLong("score"),rows.getLong("position"));
                    if(rows.getLong("place")<=10)leaders.add(entry);if(entry.player().equals(player))own=entry;players=rows.getLong("players");
                }}
            }
            boards.put(board,new Standing(leaders,own,players));
        }
        return new Snapshot(start,boards);
    }
    private Rankings() {}
}
