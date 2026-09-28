package dev.hanks.network;

import java.sql.*;
import java.util.*;

/** Append-only XP receipts commit atomically with the match's currency and rankings. */
public final class Levels {
    public record Receipt(UUID match,UUID player,int finish,int win,int knockouts,long before,long after) {
        public int total(){return finish+win+knockouts;}
    }
    static void initialize(Connection db) throws SQLException {
        try(var sql=db.createStatement()){
            sql.execute("CREATE TABLE IF NOT EXISTS level_accounts (player TEXT PRIMARY KEY, xp INTEGER NOT NULL CHECK(typeof(xp)='integer' AND xp>=0))");
            sql.execute("CREATE TABLE IF NOT EXISTS level_rounds (match_id TEXT PRIMARY KEY REFERENCES settled_matches(id))");
            sql.execute("CREATE TABLE IF NOT EXISTS level_receipts (match_id TEXT NOT NULL REFERENCES level_rounds(match_id), player TEXT NOT NULL, finish INTEGER NOT NULL, win INTEGER NOT NULL, kos INTEGER NOT NULL, xp_before INTEGER NOT NULL, xp_after INTEGER NOT NULL, PRIMARY KEY(match_id,player))");
        }
        // Credit existing play once. Rollback-era results are caught on the next upgrade too.
        db.setAutoCommit(false);
        try {
            try(var sql=db.createStatement();var rows=sql.executeQuery("SELECT result FROM settled_matches WHERE id NOT IN (SELECT match_id FROM level_rounds) ORDER BY settled_at,rowid")){
                while(rows.next())record(db,Wire.JSON.fromJson(rows.getString(1),Wire.MatchResult.class));
            }
            db.commit();
        }catch(SQLException|RuntimeException e){db.rollback();throw e;}finally{db.setAutoCommit(true);}
    }
    static void record(Connection db,Wire.MatchResult result) throws SQLException {
        try(var sql=db.prepareStatement("INSERT OR IGNORE INTO level_rounds(match_id) VALUES(?)")){
            sql.setString(1,result.id().toString());if(sql.executeUpdate()==0)return;
        }
        for(var row:result.rows()){
            long before=total(db,row.player());var reward=LevelRules.reward(result,row.player());long after=Math.addExact(before,reward.total());
            if(reward.total()>0)try(var sql=db.prepareStatement("INSERT INTO level_accounts(player,xp) VALUES(?,?) ON CONFLICT(player) DO UPDATE SET xp=excluded.xp")){
                sql.setString(1,row.player().toString());sql.setLong(2,after);sql.executeUpdate();
            }
            try(var sql=db.prepareStatement("INSERT INTO level_receipts VALUES(?,?,?,?,?,?,?)")){
                sql.setString(1,result.id().toString());sql.setString(2,row.player().toString());sql.setInt(3,reward.finish());sql.setInt(4,reward.win());sql.setInt(5,reward.knockouts());sql.setLong(6,before);sql.setLong(7,after);sql.executeUpdate();
            }
        }
    }
    static long total(Connection db,UUID player) throws SQLException {
        try(var sql=db.prepareStatement("SELECT xp FROM level_accounts WHERE player=?")){
            sql.setString(1,player.toString());try(var r=sql.executeQuery()){return r.next()?r.getLong(1):0;}
        }
    }
    static Map<UUID,Long> totals(Connection db) throws SQLException {
        var values=new HashMap<UUID,Long>();
        try(var sql=db.createStatement();var r=sql.executeQuery("SELECT player,xp FROM level_accounts")){while(r.next())values.put(UUID.fromString(r.getString(1)),r.getLong(2));}
        return Map.copyOf(values);
    }
    static Map<UUID,Receipt> receipts(Connection db,UUID match) throws SQLException {
        var values=new HashMap<UUID,Receipt>();
        try(var sql=db.prepareStatement("SELECT * FROM level_receipts WHERE match_id=?")){
            sql.setString(1,match.toString());try(var r=sql.executeQuery()){while(r.next()){
                var player=UUID.fromString(r.getString("player"));values.put(player,new Receipt(match,player,r.getInt("finish"),r.getInt("win"),r.getInt("kos"),r.getLong("xp_before"),r.getLong("xp_after")));
            }}
        }
        return Map.copyOf(values);
    }
    private Levels(){}
}
