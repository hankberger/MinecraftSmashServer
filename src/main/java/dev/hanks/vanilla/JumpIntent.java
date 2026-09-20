package dev.hanks.vanilla;

/** Edge-triggered input forgiveness. Intent expires; holding jump never repeats it. */
public final class JumpIntent {
    public static final int BUFFER_TICKS = 3, GRACE_TICKS = 2;
    private int pressedAt = Integer.MIN_VALUE, groundedAt = Integer.MIN_VALUE;
    public void observe(boolean pressed, boolean previous, boolean grounded, int now) {
        if (grounded) groundedAt = now;
        if (pressed && !previous) pressedAt = now;
    }
    public boolean pending(int now) { return (long)now - pressedAt <= BUFFER_TICKS; }
    public boolean groundJump(boolean grounded, int now) { return grounded || (long)now - groundedAt <= GRACE_TICKS; }
    public void consume() { pressedAt = groundedAt = Integer.MIN_VALUE; }
    public void clear() { consume(); }
}
