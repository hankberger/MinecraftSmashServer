package dev.hanks.vanilla;

/** Shared descriptions and prediction; the server alone selects and resolves a move. Times are ticks. */
public final class FighterMoves {
    public static final int BUFFER_TICKS = 3, DOWN_INTENT_TICKS = 2;
    public static final double SLAM_FALL_SPEED = -1.25;
    public static final int SLAM_DIVE_TICKS = 24, SLAM_LANDING_LOCKOUT = 14;
    public record Move(int id, String name, AttackKind kind, AttackDirection aim, boolean aerial,
                       int damage, int startup, int lockout, double reach, double horizontal,
                       double vertical, int stunBonus, int shieldDamage) {
        public Move power(int damage, double horizontal, double vertical) {
            return new Move(id, name, kind, aim, aerial, damage, startup, lockout, reach, horizontal, vertical, stunBonus, shieldDamage);
        }
        public CombatRules.Launch launch(int percent, int direction, double weight) {
            var base = CombatRules.launch(percent, direction);
            // Arrows remain a spacing tool; recovery contact is not a finishing strike.
            if (id == 8 || kind == AttackKind.RECOVERY)
                return new CombatRules.Launch(base.x() * horizontal * weight, base.y() * vertical * weight, id == 8 ? 5 : 6);
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
        case STEVE -> "Balanced tools & spacing"; case ALEX -> "Fast duelist & air dash";
        case ZOMBIE -> "Heavy claws & ground slams"; case SKELETON -> "Arrows & precise spacing";
        case VILLAGER -> "Bell traps & landing reads";
    }; }
    public static Move light(FighterClass c, AttackDirection aim, boolean air) {
        if (aim == AttackDirection.NEUTRAL) {
            if (air) return neutralAir(c);
            aim = AttackDirection.FORWARD;
        }
        int id = aim.ordinal() * 2 + (air ? 1 : 0);
        String[] names = switch (c) {
            case STEVE -> new String[]{"Sword Swipe", "Air Slash", "Overhead Cut", "Rising Cut", "Shovel Sweep", "Pickaxe Tap"};
            case ALEX -> new String[]{"Quick Slash", "Passing Cut", "Flick Slash", "Scissor Kick", "Low Cut", "Heel Cut"};
            case ZOMBIE -> new String[]{"Claw Sweep", "Raking Claws", "Grave Uppercut", "Sky Rake", "Ankle Rake", "Grave Stomp"};
            case SKELETON -> new String[]{"Bone Swing", "Heel Kick", "Bone Jab", "Up Kick", "Shin Check", "Heel Drop"};
            case VILLAGER -> new String[]{"Parcel Swing", "Air Delivery", "Parcel Lift", "Overhead Delivery", "Low Delivery", "Parcel Bonk"};
        };
        int[] damage = switch (c) {
            case STEVE -> new int[]{7,7,6,6,5,8}; case ALEX -> new int[]{5,6,4,5,4,6};
            case ZOMBIE -> new int[]{9,8,10,9,8,11}; case SKELETON -> new int[]{6,7,5,6,4,6};
            case VILLAGER -> new int[]{6,7,6,6,4,7};
        };
        int startup = c == FighterClass.ALEX ? 2 : c == FighterClass.ZOMBIE ? 4 : 3;
        int lockout = c == FighterClass.ALEX ? 8 : c == FighterClass.ZOMBIE ? 13 : 11;
        if (c == FighterClass.STEVE && id == 5) { startup = 4; lockout = 13; }
        if (c == FighterClass.ZOMBIE && id == 2) { startup = 5; lockout = 15; }
        if (c == FighterClass.ZOMBIE && id == 5) { startup = 5; lockout = 17; }
        if (c == FighterClass.SKELETON && id == 4) { startup = 2; lockout = 9; }
        if (c == FighterClass.SKELETON && id == 3) { startup = 4; lockout = 12; }
        if (c == FighterClass.VILLAGER && aim == AttackDirection.UP) { startup = 4; lockout = 12; }
        double reach = switch (c) { case ALEX -> 1.85; case ZOMBIE -> 2.3; case VILLAGER -> 2.25; default -> 2.6; };
        double x = switch (c) { case ALEX -> .72; case ZOMBIE -> 1.18; case SKELETON -> 1.15; default -> .92; };
        double y = .9;
        if (aim == AttackDirection.UP) { reach = c == FighterClass.ALEX ? 2.25 : c == FighterClass.SKELETON ? 2.6 : 2.4; x = c == FighterClass.SKELETON ? .6 : .32; y = c == FighterClass.ZOMBIE ? 2.05 : 1.6; }
        if (aim == AttackDirection.DOWN) {
            reach = air ? 1.45 : reach - .3;
            x = c == FighterClass.ZOMBIE ? 1.05 : .7;
            y = air ? .65 : c == FighterClass.STEVE ? 1.05 : .5;
        }
        return new Move(id, names[id], AttackKind.LIGHT, aim, air, damage[id], startup, lockout, reach, x, y, -2, c == FighterClass.ZOMBIE ? 22 : 16);
    }
    private static Move neutralAir(FighterClass c) {
        return switch (c) {
            case STEVE -> new Move(10,"Sword Spin",AttackKind.LIGHT,AttackDirection.NEUTRAL,true,6,2,11,1.35,.60,.80,-3,14);
            case ALEX -> new Move(10,"Twisting Cut",AttackKind.LIGHT,AttackDirection.NEUTRAL,true,4,2,9,1.10,.45,.85,-3,12);
            case ZOMBIE -> new Move(10,"Flailing Claws",AttackKind.LIGHT,AttackDirection.NEUTRAL,true,9,4,15,1.55,.90,1.0,-2,22);
            case SKELETON -> new Move(10,"Bone Spin",AttackKind.LIGHT,AttackDirection.NEUTRAL,true,5,3,11,1.40,.60,.65,-3,14);
            case VILLAGER -> new Move(10,"Parcel Twirl",AttackKind.LIGHT,AttackDirection.NEUTRAL,true,6,3,12,1.45,.65,.90,-3,16);
        };
    }
    public static int activeTicks(Move move) {
        return move.kind() == AttackKind.RECOVERY ? 6 : move.aim() == AttackDirection.NEUTRAL ? 4 : 2;
    }
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
        return new Move(8,"Bow Shot",AttackKind.HEAVY,AttackDirection.FORWARD,true,BowRules.damage(charge),0,0,0,.35,.35,-8,10);
    }
    public static Move bell() { return new Move(9,"Bell Ring",AttackKind.HEAVY,AttackDirection.FORWARD,false,12,0,0,1.5,1.1,1.15,0,26); }
}
