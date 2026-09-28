package dev.hanks.vanilla;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** The manifest describes the actual shipped audio, not an assumed one-minute loop. */
public enum AudioCue {
    QUEUE_DRUM("queue_drum", false, .65f),
    QUEUE_CHOIR("queue_choir", true, .045f),
    FIGHT("fight", false, .9f), GAME("game", false, .9f),
    FIGHT_1("fight_1", true, .42f), FIGHT_2("fight_2", true, .42f),
    FIGHT_3("fight_3", true, .42f), FIGHT_4("fight_4", true, .42f), FIGHT_5("fight_5", true, .42f);

    public static final List<AudioCue> TRACKS = List.of(FIGHT_1,FIGHT_2,FIGHT_3,FIGHT_4,FIGHT_5);
    public final String file;
    public final boolean music;
    public final float volume;
    AudioCue(String file, boolean music, float volume) { this.file=file; this.music=music; this.volume=volume; }
    public String sound() { return "audio."+file; }
    public long durationMillis() { return Durations.VALUES.get(file); }
    private static final class Durations {
        static final Map<String,Long> VALUES = load();
        private static Map<String,Long> load() {
            try(var in=AudioCue.class.getResourceAsStream("/ui/audio.json")) {
                var data=JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(in),StandardCharsets.UTF_8)).getAsJsonObject();
                var result=new HashMap<String,Long>();
                for(var cue:values()) {
                    long duration=data.getAsJsonObject(cue.file).get("duration_ms").getAsLong();
                    if(duration<=0)throw new IllegalStateException("Invalid duration: "+cue);
                    result.put(cue.file,duration);
                }
                return Map.copyOf(result);
            } catch(Exception e) { throw new ExceptionInInitializerError(e); }
        }
    }
}
