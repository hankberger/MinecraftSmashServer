package dev.hanks.vanilla;

/** Internal server intent derived from vanilla input. Never sent as a custom payload. */
public record AttackIntent(AttackKind kind, AttackDirection direction, int axis, boolean release, int sequence, int facing) {
    public AttackIntent(AttackKind kind, AttackDirection direction, int axis, boolean release, int sequence) { this(kind, direction, axis, release, sequence, 0); }
    public AttackIntent(AttackKind kind) { this(kind, AttackDirection.FORWARD, 0, false, 0); }
    public AttackIntent(AttackKind kind, AttackDirection direction, int axis, boolean release) { this(kind, direction, axis, release, 0); }
    public static final AttackIntent INSTANCE = new AttackIntent(AttackKind.LIGHT);
}
