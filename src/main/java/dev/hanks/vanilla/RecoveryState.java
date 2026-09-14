package dev.hanks.vanilla;

/** Per-entity recovery budget; reset only by a landing or an explicit respawn. */
public final class RecoveryState {
    public static final double AIR_JUMP = MovementRules.JUMP;
    public static final int DROP_TICKS = 12;
    private boolean used;
    private boolean fastFalling;
    private boolean recoveryUsed, burstUsed, departed;
    private int dropUntil;
    public boolean available() { return !used; }
    public boolean dropping(int now) { return now < dropUntil; }
    public boolean fastFalling() { return fastFalling; }
    public boolean recoveryAvailable() { return !recoveryUsed; }
    public boolean burstAvailable() { return !burstUsed && !recoveryUsed; }
    public boolean helpless() { return recoveryUsed; }
    public boolean recover(boolean grounded) {
        if (recoveryUsed) return false;
        recoveryUsed = used = true; fastFalling = false; departed = !grounded;
        return true;
    }
    public boolean burst(boolean grounded) {
        if (recoveryUsed || !grounded && burstUsed) return false;
        if (!grounded) { burstUsed = true; departed = true; }
        return true;
    }
    public void sync(boolean airJump, boolean recovery, boolean burst) {
        used = !airJump; recoveryUsed = !recovery; burstUsed = !burst;
    }
    public void cancelFastFall() { fastFalling = false; }
    public void grounded(boolean grounded, int now) {
        if (!grounded) departed = true;
        if (grounded && !dropping(now)) {
            if (departed) { used = recoveryUsed = burstUsed = false; departed = false; }
            fastFalling = false;
        }
    }
    public boolean jump(boolean grounded) {
        if (grounded || used || recoveryUsed) return false;
        used = true;
        departed = true;
        fastFalling = false;
        return true;
    }
    public void drop(int now) { dropUntil = now + DROP_TICKS; }
    public boolean fastFall(boolean grounded, double verticalMovement) {
        if (grounded || fastFalling || !Double.isFinite(verticalMovement) || verticalMovement > .08) return false;
        fastFalling = true;
        return true;
    }
    public void reset() { used = recoveryUsed = burstUsed = departed = false; dropUntil = 0; fastFalling = false; }
}
