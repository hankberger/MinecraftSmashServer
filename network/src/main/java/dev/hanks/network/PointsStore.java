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
            // Additive tables keep the original points schema readable during a server rollback.
            sql.execute("CREATE TABLE IF NOT EXISTS cosmetics (player TEXT NOT NULL, skin TEXT NOT NULL, price INTEGER NOT NULL CHECK(price>=0), purchased_at INTEGER NOT NULL, PRIMARY KEY(player,skin))");
            sql.execute("CREATE TABLE IF NOT EXISTS equipped_skins (player TEXT NOT NULL, fighter TEXT NOT NULL, skin TEXT NOT NULL, PRIMARY KEY(player,fighter))");
            sql.execute("CREATE TABLE IF NOT EXISTS economy_rounds (match_id TEXT PRIMARY KEY REFERENCES settled_matches(id), mode TEXT NOT NULL, ticks INTEGER NOT NULL CHECK(ticks>=0))");
            sql.execute("CREATE TABLE IF NOT EXISTS economy_participation (match_id TEXT NOT NULL REFERENCES economy_rounds(match_id), player TEXT NOT NULL, played_ticks INTEGER NOT NULL, active_ticks INTEGER NOT NULL, points INTEGER NOT NULL, reason TEXT NOT NULL, PRIMARY KEY(match_id,player))");
            sql.execute("CREATE INDEX IF NOT EXISTS economy_participation_player ON economy_participation(player)");
            sql.execute("CREATE TABLE IF NOT EXISTS economy_purchases (player TEXT NOT NULL, skin TEXT NOT NULL, first_purchase INTEGER NOT NULL, tracked_ticks INTEGER NOT NULL, PRIMARY KEY(player,skin), FOREIGN KEY(player,skin) REFERENCES cosmetics(player,skin))");
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
    public synchronized Map<UUID,Cosmetics.Wardrobe> wardrobes() throws SQLException {
        var ids = new HashSet<UUID>();
        try (var sql=db.createStatement();var r=sql.executeQuery("SELECT player FROM cosmetics UNION SELECT player FROM equipped_skins")) {
            while(r.next()) ids.add(UUID.fromString(r.getString(1)));
        }
        var result=new HashMap<UUID,Cosmetics.Wardrobe>();
        for(var id:ids)result.put(id,wardrobe(id));
        return Map.copyOf(result);
    }
    public synchronized Cosmetics.Wardrobe wardrobe(UUID player) throws SQLException {
        var owned=new HashSet<String>();var equipped=new HashMap<String,String>();
        try(var sql=db.prepareStatement("SELECT skin FROM cosmetics WHERE player=?")) {
            sql.setString(1,player.toString());try(var r=sql.executeQuery()){while(r.next())owned.add(r.getString(1));}
        }
        try(var sql=db.prepareStatement("SELECT fighter,skin FROM equipped_skins WHERE player=?")) {
            sql.setString(1,player.toString());try(var r=sql.executeQuery()){while(r.next())equipped.put(r.getString(1),r.getString(2));}
        }
        return new Cosmetics.Wardrobe(owned,equipped);
    }
    public enum OutfitResult { PURCHASED, EQUIPPED, NEED_POINTS, NOT_OWNED, PRICE_CHANGED }
    /** Debit, permanent ownership and equip commit together. Retrying an owned skin cannot charge again. */
    public synchronized OutfitResult outfit(UUID player,String fighter,String id,boolean purchase) throws SQLException {
        var skin=Cosmetics.skin(fighter,Objects.requireNonNull(id));
        return outfit(player,fighter,id,purchase,Cosmetics.price(wardrobe(player),skin));
    }
    public synchronized OutfitResult outfit(UUID player,String fighter,String id,boolean purchase,int quotedPrice) throws SQLException {
        var skin=Cosmetics.skin(fighter,Objects.requireNonNull(id));
        db.setAutoCommit(false);
        try {
            var wardrobe=wardrobe(player);boolean owned=wardrobe.owns(skin);
            int price=Cosmetics.price(wardrobe,skin);
            if(!owned && !purchase){db.rollback();return OutfitResult.NOT_OWNED;}
            if(!owned) {
                if(price!=quotedPrice){db.rollback();return OutfitResult.PRICE_CHANGED;}
                try(var sql=db.prepareStatement("UPDATE accounts SET balance=balance-? WHERE player=? AND balance>=?")) {
                    sql.setInt(1,price);sql.setString(2,player.toString());sql.setInt(3,price);
                    if(sql.executeUpdate()!=1){db.rollback();return OutfitResult.NEED_POINTS;}
                }
                try(var sql=db.prepareStatement("INSERT INTO cosmetics(player,skin,price,purchased_at) VALUES(?,?,?,?)")) {
                    sql.setString(1,player.toString());sql.setString(2,skin.id());sql.setInt(3,price);sql.setLong(4,System.currentTimeMillis());sql.executeUpdate();
                }
                try(var sql=db.prepareStatement("INSERT INTO economy_purchases(player,skin,first_purchase,tracked_ticks) SELECT ?,?,?,COALESCE(SUM(played_ticks),0) FROM economy_participation WHERE player=?")) {
                    sql.setString(1,player.toString());sql.setString(2,skin.id());sql.setInt(3,wardrobe.owned().isEmpty()?1:0);sql.setString(4,player.toString());sql.executeUpdate();
                }
            }
            try(var sql=db.prepareStatement("INSERT INTO equipped_skins(player,fighter,skin) VALUES(?,?,?) ON CONFLICT(player,fighter) DO UPDATE SET skin=excluded.skin")) {
                sql.setString(1,player.toString());sql.setString(2,fighter);sql.setString(3,skin.id());sql.executeUpdate();
            }
            db.commit();return owned?OutfitResult.EQUIPPED:OutfitResult.PURCHASED;
        } catch(SQLException|RuntimeException e){db.rollback();throw e;}
        finally {db.setAutoCommit(true);}
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
                if(match.evidence()!=null) {
                    try(var sql=db.prepareStatement("INSERT INTO economy_rounds(match_id,mode,ticks) VALUES(?,?,?)")) {
                        sql.setString(1,match.id().toString());sql.setString(2,match.mode());sql.setInt(3,match.evidence().roundTicks());sql.executeUpdate();
                    }
                    for(var row:match.rows())try(var sql=db.prepareStatement("INSERT INTO economy_participation(match_id,player,played_ticks,active_ticks,points,reason) VALUES(?,?,?,?,?,?)")) {
                        var activity=match.evidence().players().get(row.player());
                        sql.setString(1,match.id().toString());sql.setString(2,row.player().toString());sql.setInt(3,activity.playedTicks());sql.setInt(4,activity.activeTicks());
                        sql.setInt(5,PointRules.reward(match,row.player()).total());sql.setString(6,PointRules.reason(match,row.player()).name());sql.executeUpdate();
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
    /** Aggregate measurements only: excludes unmeasured historical rounds, practice and lobby/queue time. */
    public synchronized Map<String,Object> economyReport() throws SQLException {
        var report=new LinkedHashMap<String,Object>();
        report.put("prices",Map.of("standardSkin",EconomyRules.STANDARD_SKIN,"firstSkin",EconomyRules.STANDARD_SKIN/2,"elaborateSkin",EconomyRules.ELABORATE_SKIN,"futureClass",EconomyRules.NEW_CLASS));
        report.put("timeBasis","Active round time; excludes lobby, queue, practice and historical unmeasured matches");
        try(var sql=db.createStatement();var r=sql.executeQuery("SELECT COUNT(*),COALESCE(SUM(ticks),0) FROM economy_rounds")) {
            r.next();long rounds=r.getLong(1);report.put("measuredRounds",rounds);
            if(rounds>0)report.put("averageRoundSeconds",r.getDouble(2)/20/rounds);
        }
        try(var sql=db.createStatement();var r=sql.executeQuery("SELECT COUNT(DISTINCT player),COALESCE(SUM(played_ticks),0),COALESCE(SUM(points),0) FROM economy_participation")) {
            r.next();report.put("measuredPlayers",r.getLong(1));report.put("measuredPointsEarned",r.getLong(3));
            if(r.getLong(2)>0)report.put("pointsPerPlayerMatchHour",r.getDouble(3)*20*3600/r.getDouble(2));
        }
        var reasons=new TreeMap<String,Long>();
        try(var sql=db.createStatement();var r=sql.executeQuery("SELECT reason,COUNT(*) FROM economy_participation GROUP BY reason")) {while(r.next())reasons.put(r.getString(1),r.getLong(2));}
        report.put("rewardOutcomes",reasons);
        long earners,savers;
        try(var sql=db.createStatement();var r=sql.executeQuery("SELECT COUNT(DISTINCT player) FROM economy_participation WHERE points>0")){r.next();earners=r.getLong(1);}
        try(var sql=db.createStatement();var r=sql.executeQuery("SELECT COUNT(DISTINCT e.player) FROM economy_participation e WHERE points>0 AND NOT EXISTS (SELECT 1 FROM cosmetics c WHERE c.player=e.player)")){r.next();savers=r.getLong(1);}
        report.put("measuredEarners",earners);report.put("earnersWithoutPurchase",savers);
        if(earners>0)report.put("earnersWithoutPurchasePercent",100.0*savers/earners);
        var firsts=new ArrayList<Double>();
        try(var sql=db.createStatement();var r=sql.executeQuery("SELECT tracked_ticks/1200.0 FROM economy_purchases WHERE first_purchase=1 AND tracked_ticks>0 ORDER BY tracked_ticks")){while(r.next())firsts.add(r.getDouble(1));}
        report.put("measuredFirstPurchases",firsts.size());
        if(!firsts.isEmpty())report.put("medianFirstPurchaseMatchMinutes",(firsts.get((firsts.size()-1)/2)+firsts.get(firsts.size()/2))/2);
        return Collections.unmodifiableMap(report);
    }
    @Override public synchronized void close() throws SQLException { db.close(); }
}
