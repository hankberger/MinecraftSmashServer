package dev.hanks.vanilla;

import static org.junit.jupiter.api.Assertions.*;
import static dev.hanks.vanilla.MatchAudioTimeline.Phase.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class MatchAudioTimelineTest {
    static class Speaker implements MatchAudioTimeline.Output {
        final List<AudioCue> played=new ArrayList<>();
        final Set<AudioCue> active=EnumSet.noneOf(AudioCue.class);
        final MatchAudioTimeline timeline=new MatchAudioTimeline(this);
        public void play(AudioCue cue) {
            if(cue.music)assertTrue(active.stream().noneMatch(c->c.music),"Music must never overlap");
            played.add(cue);active.add(cue);
        }
        public void stop(AudioCue cue) { active.remove(cue); }
        public void stopVanillaMusic() { active.removeIf(c->c.music); }
        void update(MatchAudioTimeline.Phase phase,long now) { timeline.update(phase,phase==QUEUE||phase==IDLE?null:"round",42,now); }
    }
    @Test void quietQueueLoopsOnlyWhileActuallyQueued() {
        var s=new Speaker();s.update(QUEUE,100);
        assertEquals(List.of(AudioCue.QUEUE_CHOIR),s.played);
        assertTrue(AudioCue.QUEUE_CHOIR.volume<=.05f);
        s.update(QUEUE,100+AudioCue.QUEUE_CHOIR.durationMillis()-1);assertEquals(1,s.played.size());
        s.update(QUEUE,100+AudioCue.QUEUE_CHOIR.durationMillis());assertEquals(2,s.played.size());
        s.update(IDLE,90_000);s.update(IDLE,900_000);
        assertTrue(s.active.isEmpty());assertEquals(2,s.played.size());
    }
    @Test void countdownIsSilentAndFightFinishesBeforeMusic() {
        var s=new Speaker();s.update(QUEUE,0);s.update(COUNTDOWN,5000);
        assertTrue(s.active.isEmpty());s.update(COUNTDOWN,9999);assertEquals(1,s.played.size());
        s.update(FIGHT,10_000);assertEquals(AudioCue.FIGHT,s.played.getLast());
        s.update(FIGHT,10_000+AudioCue.FIGHT.durationMillis()-1);assertEquals(2,s.played.size());
        s.update(FIGHT,10_000+AudioCue.FIGHT.durationMillis());assertTrue(s.played.getLast().music);
    }
    @Test void playlistRunsBeyondEightMinutesWithoutRepeatsOrOverlap() {
        var a=new Speaker();var b=new Speaker();a.update(FIGHT,0);b.update(FIGHT,0);
        long time=AudioCue.FIGHT.durationMillis();
        var songs=new ArrayList<AudioCue>();
        for(int i=0;i<15;i++) {
            a.update(FIGHT,time);b.update(FIGHT,time);
            var song=a.played.getLast();songs.add(song);
            if(i>0)assertNotEquals(songs.get(i-1),song);
            assertEquals(a.played,b.played,"Same round seed gives every listener the same playlist");
            time+=song.durationMillis();
        }
        for(int i=0;i<15;i+=5)assertEquals(new HashSet<>(AudioCue.TRACKS),new HashSet<>(songs.subList(i,i+5)));
    }
    @Test void resultsStopMusicAndOnlyAnnounceGameOnce() {
        var s=new Speaker();s.update(FIGHT,0);s.update(FIGHT,1000);s.update(RESULTS,5000);
        assertEquals(Set.of(AudioCue.GAME),s.active);
        s.update(RESULTS,500_000);assertEquals(1,s.played.stream().filter(c->c==AudioCue.GAME).count());
        s.update(IDLE,500_001);assertTrue(s.active.isEmpty());
    }
    @Test void cancellationDuringAnnouncementCannotStartLateMusic() {
        var s=new Speaker();s.update(FIGHT,0);s.update(IDLE,100);s.update(IDLE,99_000);
        assertEquals(List.of(AudioCue.FIGHT),s.played);assertTrue(s.active.isEmpty());
        s.update(COUNTDOWN,100_000);s.timeline.close();s.update(IDLE,200_000);
        assertFalse(s.played.contains(AudioCue.GAME));
    }
    @Test void lagStartsOneFullTrackRatherThanCatchingUpWithABurst() {
        var s=new Speaker();s.update(FIGHT,0);s.update(FIGHT,1_000_000);
        assertEquals(2,s.played.size());s.update(FIGHT,1_000_001);assertEquals(2,s.played.size());
    }
    @Test void nextRoundGetsANewAnnouncementEvenWithoutAnIdleTick() {
        var s=new Speaker();s.update(FIGHT,0);s.timeline.update(FIGHT,"another",42,100);
        assertEquals(List.of(AudioCue.FIGHT,AudioCue.FIGHT),s.played);
    }
}
