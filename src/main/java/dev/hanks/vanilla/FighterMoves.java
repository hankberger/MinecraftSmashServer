package dev.hanks.vanilla;

/** Shared descriptions and prediction; the server alone selects and resolves a move. Times are ticks. */
public final class FighterMoves {
    public static final int BUFFER_TICKS = 3, DOWN_INTENT_TICKS = 2;
    public static final double SLAM_FALL_SPEED = -1.25;
    public static final int SLAM_DIVE_TICKS = 24, SLAM_LANDING_LOCKOUT = 14;
    public enum Technique { MELEE, TNT, ANVIL, QUICK_ARROW, UP_ARROW, DOWN_ARROW, SCATTER, PARCEL, SAPLING, POT, FISSURE, GOLEM, BITE, WIND_STEP }
    public record Move(int id, String name, AttackKind kind, AttackDirection aim, boolean aerial,
                       int damage, int startup, int lockout, double reach, double horizontal,
                       double vertical, int stunBonus, int shieldDamage, FighterClass fighter, Technique technique) {
        public Move(int id, String name, AttackKind kind, AttackDirection aim, boolean aerial, int damage, int startup,
                    int lockout, double reach, double horizontal, double vertical, int stunBonus, int shieldDamage) {
            this(id,name,kind,aim,aerial,damage,startup,lockout,reach,horizontal,vertical,stunBonus,shieldDamage,null,Technique.MELEE);
        }
        public Move style(FighterClass fighter, Technique technique) {
            return new Move(id,name,kind,aim,aerial,damage,startup,lockout,reach,horizontal,vertical,stunBonus,shieldDamage,fighter,technique);
        }
        public boolean melee() { return technique == Technique.MELEE || technique == Technique.BITE; }
        public boolean detached() { return !melee() || id == 8 || id == 9; }
        public Move power(int damage, double horizontal, double vertical) {
            return new Move(id, name, kind, aim, aerial, damage, startup, lockout, reach, horizontal, vertical, stunBonus, shieldDamage,fighter,technique);
        }
        public Move timing(int startup, int lockout) {
            return new Move(id, name, kind, aim, aerial, damage, startup, lockout, reach, horizontal, vertical, stunBonus, shieldDamage,fighter,technique);
        }
        public CombatRules.Launch launch(int percent, int direction, double weight) {
            var base = CombatRules.launch(percent, direction);
            if (technique == Technique.PARCEL || technique == Technique.FISSURE || technique == Technique.QUICK_ARROW
                    || technique == Technique.UP_ARROW || technique == Technique.DOWN_ARROW || technique == Technique.SCATTER)
                return new CombatRules.Launch(base.x()*horizontal*weight, base.y()*vertical*weight, technique == Technique.SCATTER ? 9 : 7);
            // Partial bow shots and recovery contact are setups; a fully drawn arrow can finish.
            if (id == 8 && stunBonus == 0 || kind == AttackKind.RECOVERY)
                return new CombatRules.Launch(base.x() * horizontal * weight, base.y() * vertical * weight, id == 8 ? 5 + stunBonus : 6);
            double damage = Math.clamp(percent, 0, CombatRules.MAX_PERCENT);
            double growth = Math.max(0, damage - 100);
            double x = Math.copySign(Math.min(5.4, .50 + damage * .0135 + growth * .0045), direction < 0 ? -1 : 1) * horizontal * weight;
            double y = Math.clamp((.30 + damage * .0045 + growth * .003) * vertical * weight, -3.5, 3.5);
            // Strong launches must last long enough to travel: recovery cannot erase them immediately.
            int stun = Math.clamp(6 + (int)Math.ceil(Math.hypot(x, y) * 7) + stunBonus, 8, 40);
            return new CombatRules.Launch(x, y, stun);
        }
    }
    private FighterMoves() {}
    public static double run(FighterClass c) { return switch (c) { case ALEX -> 1.12; case ZOMBIE -> .88; case VILLAGER -> .94; default -> 1; }; }
    public static double air(FighterClass c) { return switch (c) { case ALEX -> 1.25; case ZOMBIE -> .80; case VILLAGER -> 1.05; default -> 1; }; }
    public static double weight(FighterClass c) { return switch (c) { case ALEX -> 1.10; case ZOMBIE -> .90; case SKELETON -> 1.12; default -> 1; }; }
    public static boolean hasBurst(FighterClass c) { return c == FighterClass.ALEX; }
    public static boolean isSlam(Move move) { return move != null && move.id() == 6 && move.aim() == AttackDirection.DOWN; }
    public static String role(FighterClass c) { return switch (c) {
        case STEVE -> "Sword spacing & explosive setups"; case ALEX -> "Chains, dives & evasive footwork";
        case ZOMBIE -> "Heavy claws & shield-breaking grabs"; case SKELETON -> "Quick arrows & charged finishers";
        case VILLAGER -> "Traps, bells & golem summons";
    }; }
    public static Move light(FighterClass c, AttackDirection aim, boolean air) {
        if (aim == AttackDirection.NEUTRAL) {
            if (air) return neutralAir(c);
            aim = AttackDirection.FORWARD;
        }
        int id = aim.ordinal() * 2 + (air ? 1 : 0);
        if (c == FighterClass.SKELETON && (aim != AttackDirection.DOWN || air)) {
            var technique = aim == AttackDirection.UP ? Technique.UP_ARROW : aim == AttackDirection.DOWN ? Technique.DOWN_ARROW : Technique.QUICK_ARROW;
            return new Move(id, aim == AttackDirection.UP ? "Sky Shot" : aim == AttackDirection.DOWN ? "Descending Shot" : "Quick Shot",
                    AttackKind.LIGHT,aim,air,5,4,13,0,.40,aim == AttackDirection.DOWN ? -.50 : aim == AttackDirection.UP ? 1.15 : .45,-2,10).style(c,technique);
        }
        if (c == FighterClass.VILLAGER && aim == AttackDirection.FORWARD)
            return new Move(id,"Parcel Toss",AttackKind.LIGHT,aim,air,5,5,15,0,.45,.55,-2,10).style(c,Technique.PARCEL);
        if (c == FighterClass.VILLAGER && aim == AttackDirection.DOWN)
            return new Move(id,air ? "Flowerpot Drop" : "Sapling Snare",AttackKind.LIGHT,aim,air,air ? 8 : 7,4,16,0,.35,air ? -.6 : 1.65,-2,18).style(c,air ? Technique.POT : Technique.SAPLING);
        if (c == FighterClass.STEVE && air && aim == AttackDirection.DOWN)
            return new Move(id,"Anvil Drop",AttackKind.LIGHT,aim,true,10,5,20,0,.25,-1.1,0,24).style(c,Technique.ANVIL);
        if (c == FighterClass.ZOMBIE && !air && aim == AttackDirection.DOWN)
            return new Move(id,"Grave Fissure",AttackKind.LIGHT,aim,false,6,7,19,0,.55,.85,0,20).style(c,Technique.FISSURE);
        String[] names = switch (c) {
            case STEVE -> new String[]{"Sword Swipe", "Air Slash", "Overhead Cut", "Rising Cut", "Shovel Lift", "Pickaxe Tap"};
            case ALEX -> new String[]{"Quick Slash", "Passing Cut", "Flick Slash", "Scissor Kick", "Low Cut", "Heel Cut"};
            case ZOMBIE -> new String[]{"Claw Sweep", "Raking Claws", "Grave Uppercut", "Sky Rake", "Ankle Rake", "Grave Stomp"};
            case SKELETON -> new String[]{"Bone Swing", "Heel Kick", "Bone Jab", "Up Kick", "Retreating Sweep", "Heel Drop"};
            case VILLAGER -> new String[]{"Parcel Swing", "Air Delivery", "Parcel Lift", "Overhead Delivery", "Low Delivery", "Parcel Bonk"};
        };
        int[] damage = switch (c) {
            case STEVE -> new int[]{7,7,6,6,5,8}; case ALEX -> new int[]{5,6,4,5,4,6};
            case ZOMBIE -> new int[]{9,8,10,9,8,11}; case SKELETON -> new int[]{6,7,5,6,4,6};
            case VILLAGER -> new int[]{6,7,6,6,4,7};
        };
        int startup = c == FighterClass.ALEX ? 2 : c == FighterClass.ZOMBIE ? 6 : 3;
        int lockout = c == FighterClass.ALEX ? 8 : c == FighterClass.ZOMBIE ? 18 : 11;
        if (c == FighterClass.STEVE && id == 5) { startup = 4; lockout = 13; }
        if (c == FighterClass.ZOMBIE && id == 2) { startup = 5; lockout = 15; }
        if (c == FighterClass.ZOMBIE && id == 5) { startup = 5; lockout = 17; }
        if (c == FighterClass.SKELETON && id == 4) { startup = 2; lockout = 9; }
        if (c == FighterClass.SKELETON && id == 3) { startup = 4; lockout = 12; }
        if (c == FighterClass.VILLAGER && aim == AttackDirection.UP) { startup = 4; lockout = 12; }
        double reach = switch (c) { case ALEX -> 1.85; case ZOMBIE -> 2.3; case VILLAGER -> 2.25; default -> 2.6; };
        double x = switch (c) { case ALEX -> .72; case ZOMBIE -> 1.18; case SKELETON -> 1.15; default -> .92; };
        double y = .9;
        if (aim == AttackDirection.UP) { reach = c == FighterClass.ALEX ? 1.35 : c == FighterClass.SKELETON ? 1.65 : 1.50; x = c == FighterClass.SKELETON ? .6 : .32; y = c == FighterClass.ZOMBIE ? 2.05 : 1.6; }
        if (aim == AttackDirection.DOWN) {
            reach = air ? 1.45 : reach - .3;
            x = c == FighterClass.ZOMBIE ? 1.05 : .7;
            y = air ? .65 : c == FighterClass.STEVE ? 1.05 : .5;
            if (c == FighterClass.STEVE && !air) { x = .30; y = 1.25; }
        }
        return new Move(id, names[id], AttackKind.LIGHT, aim, air, damage[id], startup, lockout, reach, x, y, -2, c == FighterClass.ZOMBIE ? 26 : 16).style(c,Technique.MELEE);
    }
    private static Move neutralAir(FighterClass c) {
        return (switch (c) {
            case STEVE -> new Move(10,"Sword Spin",AttackKind.LIGHT,AttackDirection.NEUTRAL,true,6,2,11,1.35,.60,.80,-3,14);
            case ALEX -> new Move(10,"Twisting Cut",AttackKind.LIGHT,AttackDirection.NEUTRAL,true,4,2,9,1.10,.45,.85,-3,12);
            case ZOMBIE -> new Move(10,"Flailing Claws",AttackKind.LIGHT,AttackDirection.NEUTRAL,true,9,4,15,1.55,.90,1.0,-2,22);
            case SKELETON -> new Move(10,"Bone Spin",AttackKind.LIGHT,AttackDirection.NEUTRAL,true,5,3,11,1.40,.60,.65,-3,14);
            case VILLAGER -> new Move(10,"Parcel Twirl",AttackKind.LIGHT,AttackDirection.NEUTRAL,true,6,3,12,1.45,.65,.90,-3,16);
        }).style(c,Technique.MELEE);
    }
    public static int activeTicks(Move move) {
        return !move.melee() ? 0 : move.technique() == Technique.BITE ? 2 : move.fighter() == FighterClass.ALEX && move.id() == 5 ? 6
                : move.kind() == AttackKind.RECOVERY ? 6 : move.aim() == AttackDirection.NEUTRAL ? 4 : 2;
    }
    public static Move combo(int step) {
        return new Move(step == 2 ? 11 : 12,step == 2 ? "Cross Cut" : "Launch Kick",AttackKind.LIGHT,AttackDirection.FORWARD,false,
                step == 2 ? 4 : 7,step == 2 ? 2 : 3,step == 2 ? 9 : 14,1.9,step == 2 ? .25 : .48,step == 2 ? .7 : 1.6,-2,14).style(FighterClass.ALEX,Technique.MELEE);
    }
    public static Move special(FighterClass c, AttackDirection aim, boolean air, boolean ring) {
        if (aim != AttackDirection.DOWN) return special(c,air,ring);
        return switch (c) {
            case STEVE -> new Move(20,"TNT Toss",AttackKind.HEAVY,aim,air,16,5,22,0,1.2,1.25,2,34).style(c,Technique.TNT);
            case ALEX -> new Move(20,"Wind Step",AttackKind.HEAVY,aim,air,0,2,16,0,0,0,0,0).style(c,Technique.WIND_STEP);
            case ZOMBIE -> new Move(20,"Hungry Grab",AttackKind.HEAVY,AttackDirection.FORWARD,air,10,6,26,1.2,.65,1.05,3,0).style(c,Technique.BITE);
            case SKELETON -> new Move(20,"Scatter Shot",AttackKind.HEAVY,AttackDirection.FORWARD,air,7,5,23,0,.8,.75,0,18).style(c,Technique.SCATTER);
            case VILLAGER -> new Move(20,"Golem Shove",AttackKind.HEAVY,AttackDirection.FORWARD,air,15,9,27,0,1.45,.9,3,38).style(c,Technique.GOLEM);
        };
    }
    public static int utilityCooldown(FighterClass c) { return switch(c) { case STEVE -> 40; case VILLAGER -> 48; case ZOMBIE -> 30; default -> 26; }; }
    public static Move special(FighterClass c, boolean air, boolean ring) {
        return switch (c) {
            case STEVE -> new Move(6,"Pickaxe Smash",AttackKind.HEAVY,AttackDirection.FORWARD,air,15,4,18,3.1,1.30,1.1,4,38);
            case ALEX -> new Move(6,"Dash Cut",AttackKind.HEAVY,AttackDirection.FORWARD,air,12,3,15,1.7,1.2,.95,2,32);
            case ZOMBIE -> new Move(6,"Grave Slam",AttackKind.HEAVY,AttackDirection.DOWN,air,air ? 22 : 18,5,21,2.6,1.05,1.6,4,42);
            case SKELETON -> new Move(6,"Bow Shot",AttackKind.HEAVY,AttackDirection.FORWARD,air,0,1,1,0,0,0,0,0);
            case VILLAGER -> new Move(6,ring ? "Bell Ring" : "Bell Toss",AttackKind.HEAVY,AttackDirection.FORWARD,air,0,ring ? 2 : 3,ring ? 8 : 11,0,0,0,0,0);
        };
    }
    public static Move recovery(FighterClass c) {
        String name = switch (c) { case STEVE -> "Piston Pop"; case ALEX -> "Wind Vault"; case ZOMBIE -> "Grave Rise"; case SKELETON -> "Bone Vault"; case VILLAGER -> "Firework Float"; };
        int damage = c == FighterClass.STEVE ? 6 : c == FighterClass.ALEX ? 5 : c == FighterClass.ZOMBIE ? 8 : 0;
        return new Move(7,name,AttackKind.RECOVERY,AttackDirection.UP,true,damage,1,16,1.1,.35,1.15,-4,14);
    }
    public static double recoveryY(FighterClass c) { return switch (c) { case STEVE -> 1.40; case ALEX -> 1.22; case ZOMBIE -> 1.47; case SKELETON -> 1.30; case VILLAGER -> .50; }; }
    public static double recoveryX(FighterClass c) { return switch (c) { case STEVE, SKELETON -> .23; case ALEX -> .48; case ZOMBIE -> .12; case VILLAGER -> .23; }; }
    public static Move arrow(int charge) {
        boolean full = charge >= BowRules.FULL_DRAW_TICKS;
        return new Move(8,full ? "Power Shot" : "Bow Shot",AttackKind.HEAVY,AttackDirection.FORWARD,true,BowRules.damage(charge),0,0,0,full ? .95 : .35,full ? 1.05 : .35,full ? 3 : 0,full ? 20 : 10);
    }
    public static boolean swordTip(FighterClass kind, Move move, double distance) {
        return kind == FighterClass.STEVE && move.kind() == AttackKind.LIGHT && move.aim() == AttackDirection.FORWARD && distance >= 1.95;
    }
    public static Move contact(FighterClass kind, Move move, double distance) {
        if (kind == FighterClass.STEVE && move.kind() == AttackKind.LIGHT && move.aim() == AttackDirection.FORWARD && distance < .95) return move.power(4,.55,.7);
        if (swordTip(kind, move, distance)) return move.power(9, 1.10, .95);
        if (kind == FighterClass.STEVE && move.id() == 6 && distance >= 2.15) return move.power(18, 1.45, 1.2);
        return move;
    }
    public static Move bell() { return new Move(9,"Bell Ring",AttackKind.HEAVY,AttackDirection.FORWARD,false,12,0,0,1.5,1.1,1.15,0,26); }
}
