package dev.hanks.vanilla;

import java.util.UUID;

/** One recoverable weapon, one connection per throw, and one aerial self-reel per landing. */
public final class DrownedState {
    public static final double RANGE=10, BREAK_RANGE=14, TIP_DISTANCE=2.05;
    public static final int TETHER_TICKS=40, THROW_COOLDOWN=24, REEL_TICKS=8;
    public enum Phase { HELD, OUTBOUND, HOOKED, RETURNING }
    private Phase phase=Phase.HELD;
    private UUID target;
    private long expires,readyAt;
    private boolean selfReelSpent,departed;
    public Phase phase(){return phase;}
    public boolean armed(){return phase==Phase.HELD;}
    public boolean canThrow(long now){return armed()&&now>=readyAt;}
    public long cooldown(long now){return Math.max(0,readyAt-now);}
    public void launch(){phase=Phase.OUTBOUND;target=null;}
    public void hook(long now,UUID id){phase=Phase.HOOKED;target=id;expires=now+TETHER_TICKS;}
    public UUID target(long now){return phase==Phase.HOOKED&&now<expires?target:null;}
    public long remaining(long now){return target(now)==null?0:expires-now;}
    public void returning(){phase=Phase.RETURNING;target=null;}
    public void returned(long now){phase=Phase.HELD;target=null;readyAt=now+THROW_COOLDOWN;}
    public boolean canSelfReel(){return !selfReelSpent;}
    public void selfReel(){selfReelSpent=true;}
    public void grounded(boolean grounded){if(!grounded)departed=true;else if(departed){departed=false;selfReelSpent=false;}}
    public void reset(){phase=Phase.HELD;target=null;expires=readyAt=0;selfReelSpent=departed=false;}
    public static boolean pullSelf(int axis,double targetX,double ownerX){return axis!=0&&axis*(targetX-ownerX)<0;}
    public static FighterMoves.Move unarmed(FighterMoves.Move move) {
        return new FighterMoves.Move(move.id(),"Waterlogged Swipe",move.kind(),move.aim(),move.aerial(),
                3,move.startup(),move.lockout(),Math.min(move.reach(),1.25),.5,.7,-2,10,move.fighter(),move.technique());
    }
}
