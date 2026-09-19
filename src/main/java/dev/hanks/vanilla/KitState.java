package dev.hanks.vanilla;

/** Small per-stock budgets. A contact never gives back jumps, recovery, or an air step. */
public final class KitState {
    public long utilityReadyAt;
    private boolean departed, stepUsed, bounceUsed;
    public void grounded(boolean grounded) {
        if (!grounded) departed = true;
        else if (departed) { departed = stepUsed = bounceUsed = false; }
    }
    public boolean stepAvailable() { return !stepUsed; }
    public void step() { stepUsed = true; }
    public boolean bounce() { if (bounceUsed) return false; bounceUsed = true; return true; }
    public void reset() { utilityReadyAt = 0; departed = stepUsed = bounceUsed = false; }
}
