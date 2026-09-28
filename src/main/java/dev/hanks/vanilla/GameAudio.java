package dev.hanks.vanilla;

import java.util.*;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.*;

/** Vanilla sound packets and the required pack; no client mod, global broadcast or delayed tasks. */
public final class GameAudio {
    private final VanillaSmash game;
    private final Map<UUID,MatchAudioTimeline> listeners=new HashMap<>();
    private Battle round;
    private long seed;
    GameAudio(VanillaSmash game) { this.game=game; }
    private boolean enabled(ServerPlayer p) { return game.uiPack.enabled() && (game.uiPack.ready(p) || game.network.arena()); }
    public void queuePressed(ServerPlayer p) {
        if(!enabled(p))return;
        stop(p,AudioCue.QUEUE_DRUM);play(p,AudioCue.QUEUE_DRUM);
    }
    public void tick() {
        if(game.server==null)return;
        if(round!=game.battle) { round=game.battle;seed=UUID.randomUUID().getMostSignificantBits(); }
        long now=System.nanoTime()/1_000_000;
        for(var p:game.server.getPlayerList().getPlayers()) {
            if(!enabled(p)) { leave(p);continue; }
            var phase=MatchAudioTimeline.Phase.IDLE;
            Object key=null;
            if(game.battle!=null && game.viewers.containsKey(p.getUUID())) {
                key=game.battle;
                phase=switch(game.match.phase()) {
                    case COUNTDOWN -> MatchAudioTimeline.Phase.COUNTDOWN;
                    case ACTIVE -> MatchAudioTimeline.Phase.FIGHT;
                    case RESULTS -> MatchAudioTimeline.Phase.RESULTS;
                    case IDLE -> MatchAudioTimeline.Phase.IDLE;
                };
            } else if(game.network.selected(p.getUUID())) phase=MatchAudioTimeline.Phase.QUEUE;
            if(phase!=MatchAudioTimeline.Phase.IDLE || listeners.containsKey(p.getUUID()))
                listeners.computeIfAbsent(p.getUUID(),id->new MatchAudioTimeline(new MatchAudioTimeline.Output() {
                    public void play(AudioCue cue) { GameAudio.this.play(p,cue); }
                    public void stop(AudioCue cue) { GameAudio.this.stop(p,cue); }
                    public void stopVanillaMusic() { p.connection.send(new ClientboundStopSoundPacket(null,SoundSource.MUSIC)); }
                })).update(phase,key,seed,now);
        }
    }
    /** Also runs on new backend connections: client audio can survive a proxy transfer. */
    public void reset(ServerPlayer p) {
        leave(p);for(var cue:AudioCue.values())stop(p,cue);
    }
    public void leave(ServerPlayer p) {
        var timeline=listeners.remove(p.getUUID());if(timeline!=null)timeline.close();
    }
    public void close() {
        listeners.values().forEach(MatchAudioTimeline::close);listeners.clear();round=null;
    }
    private static Identifier id(AudioCue cue) { return Identifier.fromNamespaceAndPath("smash",cue.sound()); }
    private static SoundSource source(AudioCue cue) { return cue.music?SoundSource.MUSIC:SoundSource.PLAYERS; }
    private void play(ServerPlayer p,AudioCue cue) {
        var camera=p.getCamera();
        p.connection.send(new ClientboundSoundPacket(Holder.direct(SoundEvent.createVariableRangeEvent(id(cue))),
            source(cue),camera.getX(),camera.getY(),camera.getZ(),cue.volume,1,p.level().getRandom().nextLong()));
    }
    private void stop(ServerPlayer p,AudioCue cue) { p.connection.send(new ClientboundStopSoundPacket(id(cue),source(cue))); }
}
