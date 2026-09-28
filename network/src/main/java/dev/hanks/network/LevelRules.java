package dev.hanks.network;

import java.util.UUID;

/** Account progression is earned through play; it is never spendable or purchasable. */
public final class LevelRules {
    public static final int FINISH=100, WIN=40, KO=15, MAX_REWARDED_KOS=4;
    public record Reward(int finish,int win,int knockouts) {
        public int total(){return finish+win+knockouts;}
    }
    public record Progress(long total,long level,int into,int required) {
        public float fraction(){return (float)into/required;}
        public int remaining(){return required-into;}
        public Tier tier(){return LevelRules.tier(level);}
    }
    public enum Tier {
        ROOKIE(1,"Rookie",0x98dfb9), BRAWLER(5,"Brawler",0x65d6d0), CONTENDER(10,"Contender",0x75baff),
        CHALLENGER(25,"Challenger",0xca9aff), ALL_STAR(50,"All-Star",0xff876f), LEGEND(100,"Legend",0xffd66b);
        public final int level,color; public final String label;
        Tier(int level,String label,int color){this.level=level;this.label=label;this.color=color;}
    }
    public static Tier tier(long level){Tier found=Tier.ROOKIE;for(var tier:Tier.values())if(level>=tier.level)found=tier;return found;}
    public static Tier nextTier(long level){for(var tier:Tier.values())if(tier.level>level)return tier;return null;}
    public static Reward reward(Wire.MatchResult result,UUID player){
        if(PointRules.reason(result,player)!=PointRules.Reason.EARNED)return new Reward(0,0,0);
        var row=result.rows().stream().filter(r->r.player().equals(player)).findFirst().orElseThrow();
        return new Reward(FINISH,player.equals(result.winner())?WIN:0,Math.clamp(row.knockouts(),0,MAX_REWARDED_KOS)*KO);
    }
    /** 100 XP for the first level, +50 per level, then a steady 1,500 XP per level. */
    public static int cost(long level){if(level<1)throw new IllegalArgumentException("Level must be positive");return level>=29?1500:100+(int)(level-1)*50;}
    public static long threshold(long level){
        if(level<1)throw new IllegalArgumentException("Level must be positive");
        long n=Math.min(28,level-1);
        return Math.addExact(100*n+25*n*(n-1),Math.multiplyExact(Math.max(0,level-29),1500));
    }
    public static Progress progress(long xp){
        if(xp<0)throw new IllegalArgumentException("XP cannot be negative");
        long ramp=threshold(29),level;
        if(xp>=ramp)level=29+(xp-ramp)/1500;
        else {level=1;while(xp>=threshold(level+1))level++;}
        return new Progress(xp,level,(int)(xp-threshold(level)),cost(level));
    }
    public static String exclusion(Wire.MatchResult result,UUID player){
        return switch(PointRules.reason(result,player)){
            case EARNED -> ""; case LEFT_EARLY -> "No XP · Left early"; case TOO_SHORT -> "No XP · Short match";
            case INACTIVE -> "No XP · Inactive"; case TRAINING -> "No XP · Practice";
        };
    }
    private LevelRules(){}
}
