package dev.hanks.vanilla;

/** Server-side grace period for down-attack chords before dropping or fast falling. */
public final class DownIntent {
    private boolean held, dropQueued;
    private int deadline;
    private int claimedSequence = -1;
    private boolean claimedDrop;
    public void observe(boolean down, int now, boolean onPlatform) {
        if (down && !held) { deadline = now + FighterMoves.DOWN_INTENT_TICKS; dropQueued = onPlatform; claimedSequence = -1; claimedDrop = false; }
        held = down;
    }
    public void claimAttack(int now) { claimAttack(now,0); }
    public void claimAttack(int now,int sequence) {
        if (held && now <= deadline) { claimedDrop = dropQueued; claimedSequence = sequence; dropQueued = false; }
    }
    public void rejectAttack(int sequence) {
        if (sequence == claimedSequence) { dropQueued |= claimedDrop; claimedSequence = -1; claimedDrop = false; }
    }
    public void cancelPending() { dropQueued = claimedDrop = false; claimedSequence = -1; }
    public boolean takeDrop(int now) {
        if (!dropQueued || now < deadline) return false;
        dropQueued = false; return true;
    }
    public boolean fastFall(int now) { return held && now >= deadline; }
    public void clear() { held = false; deadline = 0; cancelPending(); }
}
