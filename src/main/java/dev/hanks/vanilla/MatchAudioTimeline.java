package dev.hanks.vanilla;

import java.util.*;

/** Per-listener lifecycle, advanced using monotonic real time so server lag can't overlap songs. */
final class MatchAudioTimeline {
    enum Phase { IDLE, QUEUE, COUNTDOWN, FIGHT, RESULTS }
    interface Output {
        void play(AudioCue cue);
        void stop(AudioCue cue);
        void stopVanillaMusic();
    }
    private final Output output;
    private Phase phase=Phase.IDLE;
    private Object round;
    private long next=Long.MAX_VALUE;
    private Random random=new Random();
    private final ArrayDeque<AudioCue> bag=new ArrayDeque<>();
    private AudioCue playing;
    MatchAudioTimeline(Output output) { this.output=output; }
    void update(Phase desired,Object round,long seed,long now) {
        if(phase!=desired || !Objects.equals(this.round,round)) {
            phase=desired; this.round=round; next=Long.MAX_VALUE;
            for(var cue:AudioCue.values()) if(cue!=AudioCue.QUEUE_DRUM || desired==Phase.IDLE) output.stop(cue);
            playing=null;
            if(desired==Phase.QUEUE || desired==Phase.COUNTDOWN || desired==Phase.FIGHT) output.stopVanillaMusic();
            switch(desired) {
                case QUEUE -> play(AudioCue.QUEUE_CHOIR,now);
                case FIGHT -> {
                    bag.clear();random=new Random(seed);
                    output.play(AudioCue.FIGHT);next=now+AudioCue.FIGHT.durationMillis();
                }
                case RESULTS -> output.play(AudioCue.GAME);
                default -> { }
            }
        }
        if(now<next)return;
        if(phase==Phase.QUEUE) play(AudioCue.QUEUE_CHOIR,now);
        else if(phase==Phase.FIGHT) {
            if(bag.isEmpty()) {
                var tracks=new ArrayList<>(AudioCue.TRACKS);Collections.shuffle(tracks,random);
                // Avoid a repeat across shuffle boundaries; every bag still contains all five tracks.
                if(tracks.getFirst()==playing) Collections.swap(tracks,0,1);
                bag.addAll(tracks);
            }
            play(bag.removeFirst(),now);
        }
    }
    private void play(AudioCue cue,long now) {
        if(playing!=null)output.stop(playing);
        playing=cue;output.play(cue);next=now+cue.durationMillis();
    }
    void close() {
        for(var cue:AudioCue.values())output.stop(cue);
        phase=Phase.IDLE;round=null;playing=null;bag.clear();next=Long.MAX_VALUE;
    }
}
