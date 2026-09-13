package dev.hanks.vanilla;

/** Server-owned move tuning. Cooldown includes startup and recovery. */
public enum AttackKind {
    LIGHT(8, 3, 12, 2.6, 1, 1, 18),
    HEAVY(18, 4, 20, 3.1, 1.45, 1.2, 42),
    RECOVERY(0, 1, 14, 1.1, .4, .7, 8);

    public final int damage, windup, cooldown, shieldDamage;
    public final double reach, horizontalLaunch, verticalLaunch;
    AttackKind(int damage, int windup, int cooldown, double reach, double horizontalLaunch, double verticalLaunch, int shieldDamage) {
        this.damage = damage; this.windup = windup; this.cooldown = cooldown; this.reach = reach;
        this.horizontalLaunch = horizontalLaunch; this.verticalLaunch = verticalLaunch; this.shieldDamage = shieldDamage;
    }
}
