package dev.hanks.vanilla;

import java.util.UUID;
import java.util.HashSet;
import java.util.Set;

/** All mutating methods run on the server thread. No client-supplied damage or timing. */
public final class CombatState {
    public int percent;
    public int knockouts;
    public int falls;
    public long readyAt;
    public long stunUntil;
    public long launchUntil;
    public boolean strongLaunch;
    public long protectedUntil;
    public long floatingStartedAt = -1;
    public long floatingUntil;
    public long hitImmuneUntil;
    public long impactAt = -1;
    public int attackDirection = 1;
    public AttackKind attack = AttackKind.LIGHT;
    public int guard = CombatRules.GUARD_CAPACITY;
    public long guardUntil, guardRegenAt;
    public UUID lastAttacker;
    public long lastHitAt;
    public FighterClass fighterClass = FighterClass.STEVE;
    public FighterMoves.Move move;
    public long startedAt, activeUntil, motionUntil, bellReadyAt;
    public int motionType;
    public double motionX;
    public boolean chargeReleased, burstStarted;
    public int releasedCharge;
    public final Set<UUID> hitTargets = new HashSet<>();
    public AttackIntent buffered;

    public boolean beginMove(long now, int direction, FighterMoves.Move next) {
        if (!beginAttack(now, direction, next.kind())) return false;
        move = next; startedAt = now; activeUntil = 0;
        readyAt = now + next.lockout(); impactAt = now + next.startup();
        hitTargets.clear(); chargeReleased = burstStarted = false; releasedCharge = 0;
        return true;
    }

    public void interrupt() {
        impactAt = -1; activeUntil = 0; motionUntil = 0; motionType = 0; buffered = null;
    }

    public boolean beginAttack(long now, int direction) {
        return beginAttack(now, direction, AttackKind.LIGHT);
    }

    public boolean beginAttack(long now, int direction, AttackKind kind) {
        if (blocking(now) || floating(now) || now < readyAt || now < stunUntil || impactAt >= 0) return false;
        attack = kind;
        readyAt = now + kind.cooldown;
        impactAt = now + kind.windup;
        attackDirection = direction < 0 ? -1 : 1;
        protectedUntil = 0; // Attacking ends spawn protection.
        return true;
    }

    public boolean floating(long now) { return now < floatingUntil; }
    public boolean hittable(long now) { return !floating(now) && now >= protectedUntil && now >= hitImmuneUntil; }
    public boolean blocking(long now) { return now < guardUntil && guard > 0; }
    public boolean heavyWindup(long now) { return attack == AttackKind.HEAVY && impactAt >= now; }
    public boolean drawingBow() { return fighterClass == FighterClass.SKELETON && move != null && move.id() == 6 && impactAt >= 0; }
    public boolean slamCommitted(long now) {
        return FighterMoves.isSlam(move) && (impactAt >= 0 || now < activeUntil || motionType == 4 && now < motionUntil);
    }

    public boolean requestGuard(long now, boolean held, boolean grounded) {
        if (!held) { guardUntil = 0; return true; }
        if (!grounded || floating(now) || now < stunUntil || now < readyAt || impactAt >= 0
                || (!blocking(now) && guard < 20)) return false;
        guardUntil = now + CombatRules.GUARD_LEASE;
        protectedUntil = 0;
        return true;
    }

    /** Called once per server tick, never per intent packet. Returns true on guard break. */
    public boolean tickGuard(long now, boolean canHold) {
        if (!canHold || floating(now) || now < stunUntil) guardUntil = 0;
        if (blocking(now)) {
            guardRegenAt = now + CombatRules.GUARD_REGEN_DELAY;
            if (--guard == 0) { breakGuard(now); return true; }
        } else if (now >= guardRegenAt && now >= stunUntil) guard = Math.min(CombatRules.GUARD_CAPACITY, guard + 2);
        return false;
    }

    private void breakGuard(long now) {
        guard = 0; guardUntil = 0;
        stunUntil = Math.max(stunUntil, now + CombatRules.GUARD_BREAK_STUN);
        readyAt = Math.max(readyAt, stunUntil);
        impactAt = -1;
        interrupt();
    }

    public void beginFloat(long now) {
        guardUntil = 0; impactAt = -1;
        interrupt();
        floatingStartedAt = now;
        floatingUntil = now + RespawnRules.FLOAT_TICKS;
        protectedUntil = floatingUntil + RespawnRules.LANDING_PROTECTION;
    }

    public CombatRules.Launch hit(long now, UUID attacker, int direction) {
        return receiveHit(now, attacker, direction, AttackKind.LIGHT).launch();
    }

    public record Impact(CombatRules.Launch launch, boolean blocked, boolean guardBroken) {}
    public Impact receiveHit(long now, UUID attacker, int direction, AttackKind kind) {
        return receiveHit(now, attacker, direction, new FighterMoves.Move(-1, "Punch", kind, AttackDirection.FORWARD,
                false, kind.damage, kind.windup, kind.cooldown, kind.reach, kind.horizontalLaunch, kind.verticalLaunch,
                kind == AttackKind.HEAVY ? 5 : 0, kind.shieldDamage), true);
    }

    public Impact receiveHit(long now, UUID attacker, int direction, FighterMoves.Move hit) {
        return receiveHit(now, attacker, direction, hit, false);
    }

    private Impact receiveHit(long now, UUID attacker, int direction, FighterMoves.Move hit, boolean legacy) {
        if (!hittable(now)) return new Impact(null, false, false);
        if (blocking(now)) {
            guard = Math.max(0, guard - hit.shieldDamage());
            guardRegenAt = now + CombatRules.GUARD_REGEN_DELAY;
            hitImmuneUntil = now + CombatRules.HIT_IMMUNITY;
            boolean broken = guard == 0;
            if (broken) breakGuard(now);
            return new Impact(null, true, broken);
        }
        percent = Math.min(CombatRules.MAX_PERCENT, percent + hit.damage());
        CombatRules.Launch base = CombatRules.launch(percent, direction);
        CombatRules.Launch launch = legacy ? new CombatRules.Launch(base.x() * hit.horizontal(),
                base.y() * hit.vertical(), Math.min(32, base.stun() + hit.stunBonus())) : hit.launch(percent, direction, FighterMoves.weight(fighterClass));
        stunUntil = now + launch.stun();
        launchUntil = stunUntil;
        strongLaunch = Math.hypot(launch.x(), launch.y()) >= 2.25;
        hitImmuneUntil = now + CombatRules.HIT_IMMUNITY;
        interrupt();
        lastAttacker = attacker;
        lastHitAt = now;
        return new Impact(launch, false, false);
    }

    public UUID creditedAttacker(long now) {
        return lastAttacker != null && now - lastHitAt <= CombatRules.CREDIT_WINDOW ? lastAttacker : null;
    }

    public void respawn(long now) {
        percent = 0;
        readyAt = now;
        stunUntil = 0;
        launchUntil = 0; strongLaunch = false;
        // Joining and GO also reset combat. Only beginFloat (an actual KO) grants protection.
        protectedUntil = 0;
        hitImmuneUntil = 0;
        impactAt = -1;
        lastAttacker = null;
        floatingStartedAt = -1; floatingUntil = 0;
        attack = AttackKind.LIGHT; guard = CombatRules.GUARD_CAPACITY; guardUntil = guardRegenAt = 0;
        move = null; startedAt = activeUntil = motionUntil = bellReadyAt = 0; motionType = 0;
        chargeReleased = burstStarted = false; releasedCharge = 0; hitTargets.clear(); buffered = null;
    }
}
