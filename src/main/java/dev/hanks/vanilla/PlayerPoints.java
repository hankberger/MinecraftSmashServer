package dev.hanks.vanilla;

import dev.hanks.network.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

/** Disk work stays off the Minecraft tick thread; only committed balances reach the UI. */
public final class PlayerPoints implements AutoCloseable {
    private final VanillaSmash game;
    private PointsStore store;
    private ExecutorService io;
    private final Map<UUID,PointsStore.Account> accounts=new ConcurrentHashMap<>();
    private final Map<UUID,Cosmetics.Wardrobe> wardrobes=new ConcurrentHashMap<>();
    private final Map<UUID,Long> memberships=new ConcurrentHashMap<>();
    private final Set<UUID> badgeUpdates=ConcurrentHashMap.newKeySet();
    private final Set<UUID> dressing=ConcurrentHashMap.newKeySet();
    private final Map<UUID,Map<UUID,PointsStore.Receipt>> receipts=new ConcurrentHashMap<>();
    private final Map<UUID,Long> receiptTimes=new ConcurrentHashMap<>();
    private final Map<UUID,Wire.MatchResult> unrecorded=new ConcurrentHashMap<>();
    private final Set<UUID> writing=ConcurrentHashMap.newKeySet();
    private volatile List<Wire.MatchResult> completed=List.of();
    public PlayerPoints(VanillaSmash game) { this.game=game; }
    public void start(Path file) {
        accounts.clear();wardrobes.clear();memberships.clear();badgeUpdates.clear();dressing.clear();receipts.clear();receiptTimes.clear();unrecorded.clear();writing.clear();
        try {
            store=new PointsStore(file);completed=store.pending();
            if(!game.network.arena()){accounts.putAll(store.accounts());wardrobes.putAll(store.wardrobes());for(var id:accounts.keySet())memberships.put(id,store.memberUntil(id));}
            io=Executors.newSingleThreadExecutor(r->new Thread(r,"smash-points"));
            if(!game.network.arena()) completed.forEach(this::record);
        } catch(Exception e) { throw new IllegalStateException("Cannot open points database; existing balances were not reset",e); }
    }
    public PointsStore.Account account(UUID player) { return accounts.getOrDefault(player,PointsStore.Account.EMPTY); }
    public Cosmetics.Wardrobe wardrobe(UUID player) { return wardrobes.getOrDefault(player,Cosmetics.Wardrobe.EMPTY); }
    public boolean dressing(UUID player) { return dressing.contains(player); }
    public boolean member(UUID player) { return memberships.getOrDefault(player,0L)>System.currentTimeMillis(); }
    public CompletableFuture<PointsStore.Account> deliver(PointsStore.StoreDelivery delivery) {
        if(game.network.arena())return CompletableFuture.failedFuture(new IllegalStateException("Only lobby accepts store delivery"));
        return CompletableFuture.supplyAsync(()->{try{
            var account=store.deliver(delivery);accounts.put(delivery.player(),account);wardrobes.put(delivery.player(),store.wardrobe(delivery.player()));memberships.put(delivery.player(),store.memberUntil(delivery.player()));badgeUpdates.add(delivery.player());return account;
        }catch(Exception e){throw new CompletionException(e);}},io);
    }
    public CompletableFuture<PointsStore.OutfitResult> outfit(UUID player,String fighter,String skin,boolean purchase) {
        int quote=Cosmetics.price(wardrobe(player),Cosmetics.skin(fighter,skin));
        if(game.network.arena() || !dressing.add(player))return CompletableFuture.failedFuture(new IllegalStateException("Outfit unavailable"));
        return CompletableFuture.supplyAsync(()->{
            try {
                var result=store.outfit(player,fighter,skin,purchase,quote);
                accounts.put(player,store.account(player));wardrobes.put(player,store.wardrobe(player));
                return result;
            } catch(Exception e){throw new CompletionException(e);}
            finally{dressing.remove(player);}
        },io);
    }
    public PointsStore.Receipt receipt(UUID match,UUID player) { return receipts.getOrDefault(match,Map.of()).get(player); }
    public CompletableFuture<Map<String,Object>> economyReport() {
        return CompletableFuture.supplyAsync(()->{try{return store.economyReport();}catch(Exception e){throw new CompletionException(e);}},io);
    }
    public List<Wire.MatchResult> completed() { return completed; }
    public boolean pending() { return !completed.isEmpty() || !unrecorded.isEmpty() || !writing.isEmpty() || !dressing.isEmpty(); }
    private void settleNow(Wire.MatchResult result) throws Exception {
        var paid=store.settle(result);
        for(var row:result.rows())accounts.put(row.player(),store.account(row.player()));
        // ResultBook expires after ten minutes; keep UI receipts slightly longer.
        receipts.put(result.id(),paid);receiptTimes.put(result.id(),System.nanoTime());
    }
    public CompletableFuture<Void> settle(Wire.MatchResult result) {
        if(game.network.arena())return CompletableFuture.failedFuture(new IllegalStateException("Only the lobby awards points"));
        return CompletableFuture.runAsync(()->{
            try { settleNow(result); }
            catch(Exception e) { throw new CompletionException(e); }
        },io);
    }
    public void record(Wire.MatchResult result) { unrecorded.putIfAbsent(result.id(),result);write(result); }
    private void write(Wire.MatchResult result) {
        if(!writing.add(result.id()))return;
        io.execute(()->{
            try {
                store.stage(result);
                if(!game.network.arena()) { settleNow(result);store.acknowledge(result.id()); }
                completed=store.pending();unrecorded.remove(result.id());
            } catch(Exception e) { VanillaSmash.LOG.error("Points delivery pending for match {}; will retry",result.id(),e); }
            finally { writing.remove(result.id()); }
        });
    }
    public CompletableFuture<Void> acknowledge(UUID match) {
        return CompletableFuture.runAsync(()->{
            try { store.acknowledge(match);completed=store.pending(); }
            catch(Exception e) { throw new CompletionException(e); }
        },io);
    }
    public void tick() {
        if(game.ticks%100!=0)return;
        if(!game.network.arena())for(var entry:memberships.entrySet())if(entry.getValue()>0&&entry.getValue()<=System.currentTimeMillis()){
            if(memberships.replace(entry.getKey(),entry.getValue(),0L)){badgeUpdates.add(entry.getKey());io.execute(()->{try{wardrobes.put(entry.getKey(),store.wardrobe(entry.getKey()));}catch(Exception e){VanillaSmash.LOG.error("Could not refresh expired membership",e);}});}
        }
        for(var id:List.copyOf(badgeUpdates))if(badgeUpdates.remove(id)){
            var player=game.server.getPlayerList().getPlayer(id);
            if(player!=null)game.server.getPlayerList().broadcastAll(new net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket(net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME,player));
        }
        List.copyOf(unrecorded.values()).forEach(this::write);
        long cutoff=System.nanoTime()-TimeUnit.MINUTES.toNanos(15);
        for(var e:receiptTimes.entrySet())if(e.getValue()<cutoff){receipts.remove(e.getKey());receiptTimes.remove(e.getKey());}
    }
    public String balance(UUID player) { return PointRules.format(account(player).balance())+" Points"; }
    public String reward(Wire.MatchResult result,UUID player) {
        var receipt=receipt(result.id(),player);
        return receipt==null?"Saving points...":receipt.total()==0?PointRules.reason(result,player).label:"+"+receipt.total()+" Points";
    }
    public int show(net.minecraft.server.level.ServerPlayer p) {
        String value=game.network.arena()?"View your points in the lobby":balance(p.getUUID())+"  ·  "+PointRules.format(account(p.getUUID()).earned())+" lifetime Points";
        game.hub.notice(p.getUUID(),value);return 1;
    }
    @Override public void close() {
        if(io==null)return;
        io.shutdown();
        try {
            if(!io.awaitTermination(15,TimeUnit.SECONDS))throw new IllegalStateException("Points writes did not finish before shutdown");
            store.close();
        } catch(Exception e) { throw new IllegalStateException("Could not close points database cleanly",e); }
        finally { io=null;store=null; }
    }
}
