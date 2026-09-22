package dev.hanks.vanilla;

/** Primary-special charge is server timed, capped, and never fires automatically. */
public final class ChargeRules {
    private ChargeRules() {}
    public static int fullTicks(FighterClass kind) {
        return switch (kind) { case STEVE -> 18; case ALEX -> 12; case ZOMBIE -> 22; case SKELETON -> 20; case VILLAGER -> 16; };
    }
    public static double power(FighterClass kind, int ticks) {
        return kind==FighterClass.SKELETON ? BowRules.power(ticks) : Math.clamp(ticks-3,0,fullTicks(kind)-3)/(double)(fullTicks(kind)-3);
    }
    /** Plant the feet; an aerial charge brakes drift but never suspends gravity. */
    public static double velocity(double current, boolean grounded) {
        return grounded ? 0 : Math.copySign(Math.max(0, Math.abs(current)-.10), current);
    }
    public static FighterMoves.Move charged(FighterClass kind, FighterMoves.Move move, int ticks) {
        if (move.id()!=6 || kind==FighterClass.SKELETON || kind==FighterClass.VILLAGER) return move;
        double p=power(kind,ticks);
        int bonus=kind==FighterClass.ALEX ? 5 : 8;
        return new FighterMoves.Move(move.id(),move.name(),move.kind(),move.aim(),move.aerial(),
                move.damage()+(int)Math.round(bonus*p),move.startup(),move.lockout(),move.reach()+(kind==FighterClass.ZOMBIE ? .45*p : 0),
                move.horizontal()*(1+.20*p),move.vertical()*(1+.10*p),move.stunBonus(),move.shieldDamage()+(int)Math.round(12*p),move.fighter(),move.technique());
    }
    public static int releaseDelay(FighterClass kind, FighterMoves.Move move, int ticks) {
        return kind==FighterClass.SKELETON ? 0 : Math.max(kind==FighterClass.ZOMBIE ? 3 : 1,move.startup()-ticks);
    }
    public static FighterMoves.Move bell(int ticks) {
        double p=power(FighterClass.VILLAGER,ticks); var move=FighterMoves.bell();
        return move.power(12+(int)Math.round(6*p),move.horizontal()*(1+.2*p),move.vertical()*(1+.1*p));
    }
}
