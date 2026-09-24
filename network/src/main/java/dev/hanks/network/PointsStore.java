package dev.hanks.network;

import java.io.IOException;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** One durable account authority in the lobby. Arena databases only use the delivery outbox. */
public final class PointsStore implements AutoCloseable {
    public record Account(long balance, long earned, long matches, long wins) {
        public static final Account EMPTY = new Account(0,0,0,0);
    }
    public record Receipt(UUID match, UUID player, int finish, int win, long balanceAfter) {
        public int total() { return finish + win; }
    }
    private final Connection db;
    public PointsStore(Path path) throws SQLException, IOException {
        Files.createDirectories(path.toAbsolutePath().getParent());
        try { Class.forName("org.sqlite.JDBC"); } catch (ClassNotFoundException e) { throw new SQLException("Missing points database driver",e); }
        db = DriverManager.getConnection("jdbc:sqlite:"+path.toAbsolutePath());
        try (var sql = db.createStatement()) {
            sql.execute("PRAGMA busy_timeout=5000"); sql.execute("PRAGMA journal_mode=WAL"); sql.execute("PRAGMA synchronous=FULL");
            sql.execute("PRAGMA foreign_keys=ON");
            try (var version = sql.executeQuery("PRAGMA user_version")) {
                if (version.next() && version.getInt(1) > 1) throw new SQLException("Points database requires a newer server");
            }
            sql.execute("CREATE TABLE IF NOT EXISTS accounts (player TEXT PRIMARY KEY, balance INTEGER NOT NULL CHECK(typeof(balance)='integer' AND balance>=0), earned INTEGER NOT NULL CHECK(typeof(earned)='integer' AND earned>=balance), matches INTEGER NOT NULL CHECK(matches>=0), wins INTEGER NOT NULL CHECK(wins>=0 AND wins<=matches))");
            sql.execute("CREATE TABLE IF NOT EXISTS settled_matches (id TEXT PRIMARY KEY, result TEXT NOT NULL, settled_at INTEGER NOT NULL)");
            sql.execute("CREATE TABLE IF NOT EXISTS point_ledger (match_id TEXT NOT NULL REFERENCES settled_matches(id), player TEXT NOT NULL, finish INTEGER NOT NULL CHECK(finish>=0), win INTEGER NOT NULL CHECK(win>=0), balance_after INTEGER NOT NULL CHECK(balance_after>=0), PRIMARY KEY(match_id,player))");
            sql.execute("CREATE TABLE IF NOT EXISTS result_outbox (id TEXT PRIMARY KEY, result TEXT NOT NULL)");
            sql.execute("PRAGMA user_version=1");
        } catch (SQLException e) { db.close(); throw e; }
    }
    public synchronized Map<UUID,Account> accounts() throws SQLException {
        var values = new HashMap<UUID,Account>();
        try (var sql=db.createStatement(); var r=sql.executeQuery("SELECT * FROM accounts")) {
            while(r.next()) values.put(UUID.fromString(r.getString("player")),new Account(r.getLong("balance"),r.getLong("earned"),r.getLong("matches"),r.getLong("wins")));
        }
        return Map.copyOf(values);
    }
    public synchronized Account account(UUID player) throws SQLException {
        try (var sql=db.prepareStatement("SELECT balance,earned,matches,wins FROM accounts WHERE player=?")) {
            sql.setString(1,player.toString());
            try(var r=sql.executeQuery()) { return r.next() ? new Account(r.getLong(1),r.getLong(2),r.getLong(3),r.getLong(4)) : Account.EMPTY; }
        }
    }
    private String result(String table, UUID id) throws SQLException {
        try(var sql=db.prepareStatement("SELECT result FROM "+table+" WHERE id=?")) {
            sql.setString(1,id.toString());try(var r=sql.executeQuery()){return r.next()?r.getString(1):null;}
        }
    }
    private static void same(String existing, Wire.MatchResult next) throws SQLException {
        if(existing!=null && !Wire.JSON.fromJson(existing,Wire.MatchResult.class).equals(next))
            throw new SQLException("Conflicting result for match "+next.id());
    }
    public synchronized void stage(Wire.MatchResult match) throws SQLException {
        same(result("result_outbox",match.id()),match);
        try(var sql=db.prepareStatement("INSERT OR IGNORE INTO result_outbox(id,result) VALUES(?,?)")) {
            sql.setString(1,match.id().toString());sql.setString(2,Wire.JSON.toJson(match));sql.executeUpdate();
        }
    }
    public synchronized List<Wire.MatchResult> pending() throws SQLException {
        var values=new ArrayList<Wire.MatchResult>();
        try(var sql=db.createStatement();var rows=sql.executeQuery("SELECT result FROM result_outbox ORDER BY rowid")) {
            while(rows.next()) values.add(Wire.JSON.fromJson(rows.getString(1),Wire.MatchResult.class));
        }
        return List.copyOf(values);
    }
    public synchronized void acknowledge(UUID id) throws SQLException {
        try(var sql=db.prepareStatement("DELETE FROM result_outbox WHERE id=?")){sql.setString(1,id.toString());sql.executeUpdate();}
    }
    public synchronized Map<UUID,Receipt> settle(Wire.MatchResult match) throws SQLException {
        db.setAutoCommit(false);
        try {
            String existing=result("settled_matches",match.id());same(existing,match);
            if(existing==null) {
                try(var sql=db.prepareStatement("INSERT INTO settled_matches(id,result,settled_at) VALUES(?,?,?)")) {
                    sql.setString(1,match.id().toString());sql.setString(2,Wire.JSON.toJson(match));sql.setLong(3,System.currentTimeMillis());sql.executeUpdate();
                }
                for(var row:match.rows()) {
                    var reward=PointRules.reward(match,row.player());var before=account(row.player());
                    long balance=Math.addExact(before.balance(),reward.total());
                    if(reward.total()>0) {
                        try(var sql=db.prepareStatement("INSERT INTO accounts(player,balance,earned,matches,wins) VALUES(?,?,?,?,?) ON CONFLICT(player) DO UPDATE SET balance=excluded.balance,earned=excluded.earned,matches=excluded.matches,wins=excluded.wins")) {
                            sql.setString(1,row.player().toString());sql.setLong(2,balance);sql.setLong(3,Math.addExact(before.earned(),reward.total()));
                            sql.setLong(4,Math.addExact(before.matches(),1));sql.setLong(5,Math.addExact(before.wins(),reward.win()>0?1:0));sql.executeUpdate();
                        }
                    }
                    try(var sql=db.prepareStatement("INSERT INTO point_ledger(match_id,player,finish,win,balance_after) VALUES(?,?,?,?,?)")) {
                        sql.setString(1,match.id().toString());sql.setString(2,row.player().toString());sql.setInt(3,reward.finish());sql.setInt(4,reward.win());sql.setLong(5,balance);sql.executeUpdate();
                    }
                }
            }
            var receipts=receipts(match.id());db.commit();return receipts;
        } catch(SQLException|RuntimeException e) { db.rollback();throw e; }
        finally { db.setAutoCommit(true); }
    }
    public synchronized Map<UUID,Receipt> receipts(UUID match) throws SQLException {
        var values=new HashMap<UUID,Receipt>();
        try(var sql=db.prepareStatement("SELECT player,finish,win,balance_after FROM point_ledger WHERE match_id=?")) {
            sql.setString(1,match.toString());try(var rows=sql.executeQuery()) {
                while(rows.next()) { var id=UUID.fromString(rows.getString(1));values.put(id,new Receipt(match,id,rows.getInt(2),rows.getInt(3),rows.getLong(4))); }
            }
        }
        return Map.copyOf(values);
    }
    @Override public synchronized void close() throws SQLException { db.close(); }
}
