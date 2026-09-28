package dev.hanks.vanilla;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEventListener;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;

/** Real pack decode, native sound playback and stop packets across queue / arena / results. */
@SuppressWarnings("UnstableApiUsage")
public final class AudioClientTest {
    private record Heard(SoundInstance sound,long at) { }
    private static VanillaSmash game() { return VanillaSmash.instance(); }
    private static void check(boolean value,String message) { if(!value)throw new AssertionError(message); }
    private static boolean is(Heard h,AudioCue cue) { return h.sound.getIdentifier().equals(Identifier.fromNamespaceAndPath("smash",cue.sound())); }
    public static void run(ClientGameTestContext c) {
        var heard=new CopyOnWriteArrayList<Heard>();
        SoundEventListener listener=(sound,event,distance)->{
            if(sound.getIdentifier().getNamespace().equals("smash"))heard.add(new Heard(sound,System.nanoTime()));
        };
        var props=new Properties();props.setProperty("online-mode","false");props.setProperty("server-ip","127.0.0.1");
        props.setProperty("view-distance","6");props.setProperty("allow-flight","true");
        try(var server=c.worldBuilder().createServer(props);var connection=server.connect()) {
            MatchmakingClientTest.click(c,"Proceed");server.waitFor(s->game().uiPack.ready(connection.getServerPlayer()),1200);
            connection.waitForChunksRender();c.waitTicks(35);
            c.runOnClient(mc->{
                for(var source:List.of(SoundSource.MASTER,SoundSource.MUSIC,SoundSource.PLAYERS))mc.options.getSoundSourceOptionInstance(source).set(1.0);
                mc.getSoundManager().addListener(listener);
                for(var cue:AudioCue.values()) {
                    var event=mc.getSoundManager().getSoundEvent(Identifier.fromNamespaceAndPath("smash",cue.sound()));
                    check(event!=null && event.getWeight()==1,"Every supplied sound is registered: "+cue);
                }
                check(mc.getSoundManager().getSoundEvent(Identifier.withDefaultNamespace("music.game")).getWeight()==0,"Vanilla music cannot overlap the server soundtrack");
            });
            server.runOnServer(s->game().choose(connection.getServerPlayer(),FighterClass.STEVE,VanillaSmash.Mode.DUEL));
            c.waitFor(mc->heard.stream().anyMatch(h->is(h,AudioCue.QUEUE_CHOIR)),150);
            var choir=heard.stream().filter(h->is(h,AudioCue.QUEUE_CHOIR)).findFirst().orElseThrow().sound;
            c.runOnClient(mc->{
                check(choir.getVolume()<=.05 && choir.getSource()==SoundSource.MUSIC,"Queue choir is very quiet and respects Music volume");
                check(choir.getSound().shouldStream(),"Long choir streams instead of loading into sound-effect memory");
            });
            check(heard.stream().filter(h->is(h,AudioCue.QUEUE_DRUM)).count()==1,"Queue click plays one drum cue");
            c.waitFor(mc->mc.getSoundManager().isActive(choir),100);
            server.runOnServer(s->game().unqueue(connection.getServerPlayer()));
            c.waitFor(mc->!mc.getSoundManager().isActive(choir),100);
            c.waitTicks(12);check(heard.stream().noneMatch(h->is(h,AudioCue.FIGHT)),"Cancel never starts the battle soundtrack");
            var friend=new AtomicReference<MatchmakingClientTest.Peer>();
            server.runOnServer(s->friend.set(MatchmakingClientTest.Peer.join(s,"AudioRival")));c.waitTicks(65);
            heard.clear();
            server.runOnServer(s->{
                game().uiPack.response(friend.get().player(),new ServerboundResourcePackPacket(UiPack.ID,ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED));
                game().choose(connection.getServerPlayer(),FighterClass.STEVE,VanillaSmash.Mode.DUEL);
                game().choose(friend.get().player(),FighterClass.ZOMBIE,VanillaSmash.Mode.DUEL);
            });
            c.waitTicks(20);check(heard.stream().noneMatch(h->is(h,AudioCue.FIGHT)||AudioCue.TRACKS.stream().anyMatch(t->is(h,t))),"Countdown has no Fight or battle music");
            c.waitFor(mc->heard.stream().anyMatch(h->AudioCue.TRACKS.stream().anyMatch(t->is(h,t))),300);
            var fight=heard.stream().filter(h->is(h,AudioCue.FIGHT)).findFirst().orElseThrow();
            var music=heard.stream().filter(h->AudioCue.TRACKS.stream().anyMatch(t->is(h,t))).findFirst().orElseThrow();
            check((music.at-fight.at)/1_000_000>=500,"Music follows the short Fight announcement");
            check(music.sound.getSound().shouldStream() && music.sound.getSource()==SoundSource.MUSIC,"Battle track uses streaming Music audio");
            c.waitFor(mc->mc.getSoundManager().isActive(music.sound),100);
            server.runOnServer(s->game().match.finish(connection.getServerPlayer().getUUID(),"Audio fixture"));
            c.waitFor(mc->heard.stream().anyMatch(h->is(h,AudioCue.GAME)),100);
            c.waitFor(mc->!mc.getSoundManager().isActive(music.sound),100);
            c.waitTicks(60);
            check(heard.stream().filter(h->is(h,AudioCue.GAME)).count()==1,"Game plays exactly once including the results transfer");
            c.runOnClient(mc->check(heard.stream().filter(h->h.sound.getSource()==SoundSource.MUSIC).noneMatch(h->mc.getSoundManager().isActive(h.sound)),"No soundtrack leaks onto the results stage"));
            MatchmakingClientTest.winnerReady(c);MatchmakingClientTest.winnerAction(c,3);c.waitTicks(20);
            heard.clear();
            server.runOnServer(s->game().choose(connection.getServerPlayer(),FighterClass.ALEX,VanillaSmash.Mode.PRACTICE));
            c.waitFor(mc->heard.stream().anyMatch(h->AudioCue.TRACKS.stream().anyMatch(t->is(h,t))),300);
            var practice=heard.stream().filter(h->AudioCue.TRACKS.stream().anyMatch(t->is(h,t))).findFirst().orElseThrow().sound;
            server.runOnServer(s->game().leave(connection.getServerPlayer()));
            c.waitFor(mc->!mc.getSoundManager().isActive(practice),100);c.waitTicks(12);
            check(heard.stream().noneMatch(h->is(h,AudioCue.GAME)),"Leaving practice stops music without a false Game announcement");
            server.runOnServer(s->friend.get().leave());
        } finally { c.runOnClient(mc->mc.getSoundManager().removeListener(listener)); }
        VanillaSmash.LOG.info("AUDIO_NATIVE_CLIENT_TEST_PASSED");
    }
}
