package dev.hanks.vanilla;

import java.util.*;
import net.minecraft.core.particles.*;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.zombie.*;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.*;
import com.mojang.math.Transformation;
import org.joml.Vector3f;

/** Server-owned fighters using vanilla renderable entities and the retained class rules. */
public final class Battle {
    public final VanillaSmash game;
    public final ServerLevel level;
    public final boolean sandbox;
    public final Map<UUID, Actor> actors = new LinkedHashMap<>();
    public final BattleObjects objects;
    public final CombatEffects effects = new CombatEffects(this);
    public final BattleHud hud = new BattleHud(this);
    public final Display.TextDisplay timer;
    public boolean dummySpar;
    public dev.hanks.network.Wire.MatchResult result;
    public final Set<Entity> displays = new HashSet<>();
    private record KoBurst(double x, double y, double dx, double dy, int startedAt) {}
    private final List<KoBurst> koBursts = new ArrayList<>();
    public static final class Actor {
        public final UUID id;
        public final ServerPlayer owner;
        public final FighterClass kind;
        public final LivingEntity body;
        public final CombatState state = new CombatState();
        public final RecoveryState recovery = new RecoveryState();
        public final LedgeState ledge = new LedgeState();
        public final KitState kit = new KitState();
        public final DownIntent down = new DownIntent();
        public final JumpIntent jump = new JumpIntent();
        public final JumpHeight jumpHeight = new JumpHeight();
        public final CombatPose pose;
        public int slot, damageDealt;
        public Display.TextDisplay marker;
        public int color() { return PlayerIdentity.color(slot); }
        public double x, y = 81, vx, vy;
        public int facing = 1;
        public boolean grounded = true, eliminated;
        public Input previous = Input.EMPTY;
        public int lights, specials, recoveries;
        Actor(ServerPlayer owner, FighterClass kind, LivingEntity body, double x) {
            this.owner = owner; this.id = owner == null ? body.getUUID() : owner.getUUID();
            this.kind = kind; this.body = body; this.x = x; state.fighterClass = kind; pose = new CombatPose(x, y);
        }
        public String name() { return owner == null ? "Dummy" : owner.getPlainTextName(); }
        public AABB box() { return new AABB(pose.x - body.getBbWidth() / 2, pose.y, .20,
                pose.x + body.getBbWidth() / 2, pose.y + body.getBbHeight(), .80); }
    }
    public Battle(VanillaSmash game, ServerLevel level, boolean sandbox) {
        this.game = game; this.level = level; this.sandbox = sandbox; objects = new BattleObjects(this);
        timer = new Display.TextDisplay(EntityTypes.TEXT_DISPLAY, level);
        timer.setFlags(Display.TextDisplay.FLAG_SHADOW); timer.setBrightnessOverride(new net.minecraft.util.Brightness(15, 15));
        timer.setText(Component.empty()); timer.setBackgroundColor(0); timer.setTextOpacity((byte)255);
        timer.setBillboardConstraints(Display.BillboardConstraints.CENTER);
        timer.setTransformation(new Transformation(null, null, new Vector3f(3), null));
        timer.setPos(.5, 98, .5); timer.setViewRange(3); timer.setLineWidth(150);
        level.addFreshEntity(timer); timer.addTag(VanillaSmash.TEMP);
    }
    public Actor add(ServerPlayer owner, FighterClass kind, double x) {
        LivingEntity body = owner == null ? new Husk(EntityTypes.HUSK, level) : FighterModels.create(level, kind);
        body.setNoGravity(true); body.setInvulnerable(true); body.setSilent(true);
        if (body instanceof Mob mob) { mob.setNoAi(true); mob.setPersistenceRequired(); }
        var f = new Actor(owner, kind, body, x); actors.put(f.id, f);
        f.slot = actors.size();
        f.marker = display(1.8f, 120); f.marker.setText(Component.literal("P" + f.slot + " ▾").withStyle(s -> s.withColor(f.color())));
        sync(f); level.addFreshEntity(body); body.addTag(VanillaSmash.TEMP);
        return f;
    }
    private Display.TextDisplay display(float scale, int width) {
        var d = new Display.TextDisplay(EntityTypes.TEXT_DISPLAY, level); displays.add(d);
        d.setBrightnessOverride(new net.minecraft.util.Brightness(15, 15));
        d.setFlags((byte)(Display.TextDisplay.FLAG_SHADOW | Display.TextDisplay.FLAG_SEE_THROUGH));
        d.setText(Component.empty()); d.setBackgroundColor(0); d.setTextOpacity((byte)255);
        d.setBillboardConstraints(Display.BillboardConstraints.CENTER); d.setViewRange(3); d.setLineWidth(width);
        d.setTransformation(new Transformation(null, null, new Vector3f(scale), null));
        d.addTag(VanillaSmash.TEMP); level.addFreshEntity(d); return d;
    }
    public void captureResult() {
        if (result != null || game.match.phase() != MatchState.Phase.RESULTS) return;
        var reservation = game.network.arena() ? game.network.reservation() : game.hub.reservation();
        if (reservation == null || dev.hanks.network.Wire.capacity(reservation.roster().getFirst().mode()) < 2) return;
        var rows = actors.values().stream().filter(f -> f.owner != null).map(f -> new dev.hanks.network.Wire.ResultRow(
                f.id, f.name(), f.kind.name(), f.slot, game.match.stocks(f.id), f.state.knockouts, f.state.falls, f.damageDealt)).toList();
        result = new dev.hanks.network.Wire.MatchResult(reservation.id(), reservation.roster().getFirst().mode(), game.match.winner(), reservation.roster(), rows);
        game.network.result(result);
        var winner = actors.get(game.match.winner());
        if (winner != null) { particles(winner, ParticleTypes.FIREWORK, 15); winner.body.swing(InteractionHand.MAIN_HAND); }
        arenaSound(SoundEvents.PLAYER_LEVELUP, .6f, 1.2f);
    }
    public void addDummy(boolean spar) { var f = add(null, FighterClass.ZOMBIE, 14.5); f.facing = -1; dummySpar = spar; }
    public Actor dummy() { return actors.values().stream().filter(f -> f.owner == null).findFirst().orElse(null); }
    public int now() { return game.ticks; }
    public Map<UUID, Integer> damage() { var result = new HashMap<UUID, Integer>(); actors.forEach((id, f) -> result.put(id, f.state.percent)); return result; }

    public boolean request(Actor f, boolean special, Input in) {
        var aim = AttackDirection.input(in.forward(), in.backward(), in.left(), in.right());
        var kind = special ? in.forward() ? AttackKind.RECOVERY : AttackKind.HEAVY : AttackKind.LIGHT;
        return request(f, kind, aim, in);
    }
    public boolean requestSecondary(Actor f, Input in) {
        // DOWN selects the utility move; keep real input so F never invents a crouch/drop or recovery chord.
        return request(f, AttackKind.HEAVY, AttackDirection.DOWN, in);
    }
    private boolean request(Actor f, AttackKind kind, AttackDirection aim, Input in) {
        if (!game.fighting(f)) return false;
        int axis = (in.right() ? 1 : 0) - (in.left() ? 1 : 0);
        f.down.observe(in.backward(), now(), ArenaRules.standingOnPlatform(f.x, f.y, .5));
        var intent = new AttackIntent(kind, aim, axis, false, 0, axis == 0 ? f.facing : axis);
        boolean accepted = submit(f, intent);
        if (accepted) f.down.claimAttack(now());
        return accepted;
    }
    private boolean submit(Actor f, AttackIntent intent) {
        if (f.ledge.attached() || f.recovery.helpless() || !game.fighting(f)) return false;
        return begin(f, intent) || f.state.buffer(now(), intent);
    }
    private boolean begin(Actor f, AttackIntent intent) {
        var s = f.state; int t = now(); boolean air = !f.grounded;
        if (!game.fighting(f) || f.ledge.attached() || f.recovery.helpless() || s.floating(t) || t < s.stunUntil) return false;
        boolean ring = f.kind == FighterClass.VILLAGER && objects.bellArmed(f);
        boolean utility = intent.kind() == AttackKind.HEAVY && intent.direction() == AttackDirection.DOWN;
        if (utility && (t < f.kit.utilityReadyAt || f.kind == FighterClass.ALEX && !f.kit.stepAvailable())) return false;
        if (intent.kind() == AttackKind.HEAVY && !utility) {
            if (f.kind == FighterClass.SKELETON && objects.hasArrow(f)) return false;
            if (f.kind == FighterClass.VILLAGER && (objects.hasBell(f) && !ring || t < s.bellReadyAt)) return false;
            if (f.kind == FighterClass.ALEX && air && !f.recovery.burstAvailable()) return false;
        }
        var move = intent.kind() == AttackKind.LIGHT ? FighterMoves.light(f.kind, intent.direction(), air)
                : intent.kind() == AttackKind.RECOVERY ? FighterMoves.recovery(f.kind) : FighterMoves.special(f.kind,intent.direction(),air,ring);
        if (f.kind == FighterClass.ALEX && !air && move.id() == 0 && s.specialConfirm(t)) {
            if (s.move.id() == 0) move = FighterMoves.combo(2);
            else if (s.move.id() == 11) move = FighterMoves.combo(3);
        }
        if (!objects.kits.available(f,move)) return false;
        if ((move.technique() == FighterMoves.Technique.SCATTER && objects.hasArrow(f))
                || (move.technique() == FighterMoves.Technique.QUICK_ARROW || move.technique() == FighterMoves.Technique.UP_ARROW || move.technique() == FighterMoves.Technique.DOWN_ARROW) && objects.arrowCount(f) >= 2) return false;
        int direction = intent.facing() != 0 ? intent.facing() : intent.axis() == 0 ? f.facing : intent.axis();
        if (!s.beginMove(t, direction, move)) return false;
        move = s.move;
        effects.remove(f);
        f.facing = direction;
        if (utility) f.kit.utilityReadyAt = t + FighterMoves.utilityCooldown(f.kind);
        if (move.technique() == FighterMoves.Technique.GOLEM) objects.kits.startGolem(f);
        if (move.technique() == FighterMoves.Technique.WIND_STEP) f.kit.step();
        if (move.id() == 11 || move.id() == 12) { s.motionType = 10; s.motionX = direction*.34; s.motionUntil = t+4; }
        if (intent.kind() == AttackKind.RECOVERY) {
            f.jump.clear(); f.jumpHeight.clear(); f.recovery.recover(f.grounded); f.grounded = false;
            f.vx = intent.axis() * FighterMoves.recoveryX(f.kind); f.vy = FighterMoves.recoveryY(f.kind);
            if (f.kind == FighterClass.VILLAGER) { s.motionType = 3; s.motionUntil = t + 13; }
            f.recoveries++; effects.departure(f);
            sound(f, SoundEvents.PLAYER_ATTACK_KNOCKBACK, .6f, 1.5f);
        } else if (intent.kind() == AttackKind.HEAVY) {
            f.specials++; if (f.kind == FighterClass.ALEX && !utility) f.recovery.burst(f.grounded);
            if (f.kind == FighterClass.SKELETON && !utility && intent.release()) {
                s.chargeReleased = true; s.releasedCharge = 0;
            }
        } else f.lights++;
        // One native swing provides immediate anticipation; restarting it on contact cuts the pose in half.
        if (!(f.kind == FighterClass.SKELETON && !move.melee()) && (move.kind() == AttackKind.LIGHT
                || move.damage() > 0 && !FighterMoves.isSlam(move)
                && !(f.kind == FighterClass.ALEX && move.id() == 6))) f.body.swing(InteractionHand.MAIN_HAND);
        return true;
    }
    public void releaseBow(Actor f) {
        var queued = f.state.pending(now());
        if (f.kind == FighterClass.SKELETON && queued != null && queued.kind() == AttackKind.HEAVY)
            f.state.buffered = new AttackIntent(queued.kind(), queued.direction(), queued.axis(), true, queued.sequence(), queued.facing());
        if (!f.state.drawingBow()) return;
        f.state.chargeReleased = true;
        f.state.releasedCharge = (int)Math.min(20, now() - f.state.startedAt);
        f.state.impactAt = now();
    }

    public void tick() {
        tickKoEffects();
        for (var f : actors.values()) {
            if (f.eliminated) continue;
            if (!game.fighting(f)) { f.vx = f.vy = 0; sync(f); continue; }
            Input in = f.owner == null ? dummyInput(f) : f.owner.containerMenu == f.owner.inventoryMenu ? f.owner.getLastClientInput() : Input.EMPTY;
            double beforeX = f.x, beforeY = f.y;
            if (f.state.paused(now())) {
                f.jump.observe(in.jump(), f.previous.jump(), f.grounded, now()); f.previous = in;
            } else { prepare(f, in); move(f, in); }
            f.pose.advance(f.x, f.y);
            if (f.state.strongLaunch && now() < f.state.launchUntil && !f.grounded) {
                // Interpolate the trail so a fast launch reads as a streak, not isolated puffs.
                for (int i = 0; i < 4; i++) {
                    double a = i / 4.0;
                    level.sendParticles(ParticleTypes.FIREWORK, true, false, beforeX + (f.x - beforeX) * a,
                            beforeY + (f.y - beforeY) * a + 1, .8, 1, .04, .04, .02, 0);
                }
                if (now() % 2 == 0) particles(f, ParticleTypes.CLOUD, 2);
            }
            if (ArenaRules.outside(f.x, f.y, .5)) ringOut(f);
            if (!f.eliminated) sync(f);
        }
        // Capture all ready attacks before applying hits so simultaneous strikes can trade.
        record Target(Actor actor, CombatGeometry.Contact contact) {}
        record Strike(Actor attacker, FighterMoves.Move move, int direction, List<Target> targets) {}
        var strikes = new ArrayList<Strike>();
        for (var f : actors.values()) if (game.fighting(f)) {
            if (f.state.paused(now())) continue;
            resolveStartup(f);
            var s = f.state;
            if (s.move == null || !s.move.melee() || s.activeUntil <= now() || s.move.damage() == 0) continue;
            if (s.move.technique() == FighterMoves.Technique.BITE && !s.hitTargets.isEmpty()) continue;
            var targets = new ArrayList<Target>();
            for (var v : actors.values()) if (v != f && game.fighting(v) && v.state.hittable(now()) && !s.hitTargets.contains(v.id)) {
                boolean sweep = s.activeStartedAt < now();
                var contact = CombatGeometry.sweep(s.move, s.attackDirection,
                        sweep ? f.pose.previousX : f.pose.x, sweep ? f.pose.previousY : f.pose.y, f.pose.x, f.pose.y,
                        sweep ? v.pose.previousX : v.pose.x, sweep ? v.pose.previousY : v.pose.y, v.pose.x, v.pose.y,
                        v.body.getBbWidth(), v.body.getBbHeight());
                if (contact != null) { s.hitTargets.add(v.id); targets.add(new Target(v, contact)); if(s.move.technique() == FighterMoves.Technique.BITE) break; }
            }
            objects.strikeBells(f, CombatGeometry.shape(s.move, s.attackDirection, f.pose.x, f.pose.y));
            strikes.add(new Strike(f, s.move, s.attackDirection, targets));
        }
        for (var strike : strikes) for (var target : strike.targets) {
            var victim = target.actor; var contact = target.contact;
            var f = strike.attacker; var move = strike.move;
            move = FighterMoves.contact(f.kind, move, Math.abs(contact.targetX() - contact.attackerX()));
            if (f.kind == FighterClass.ZOMBIE && move.id() == 5 && !victim.grounded && contact.targetY() + 1.1 < contact.attackerY() && Math.abs(contact.targetX() - contact.attackerX()) < .5)
                move = move.power(move.damage(), .15, -1.5);
            hit(f, victim, move.technique() == FighterMoves.Technique.BITE ? -strike.direction : FighterMoves.isSlam(move) || move.aim() == AttackDirection.NEUTRAL
                    ? contact.targetX() < contact.attackerX() ? -1 : 1 : strike.direction, move, contact.point());
        }
        if (game.match.phase() == MatchState.Phase.ACTIVE) objects.tick();
        effects.tick();
    }
    private Input dummyInput(Actor dummy) {
        if (!dummySpar) return Input.EMPTY;
        var target = actors.values().stream().filter(f -> f.owner != null && game.fighting(f)).findFirst().orElse(null);
        if (target == null) return Input.EMPTY;
        double dx = target.x - dummy.x;
        if (Math.abs(dx) > .2 && !dummy.state.facingLocked(now())) dummy.facing = dx > 0 ? 1 : -1;
        if (Math.abs(dx) < 2.6 && Math.abs(target.y - dummy.y) < 2 && now() % 24 == 0) request(dummy, false, Input.EMPTY);
        return new Input(false, false, dx < -2, dx > 2, false, false, false);
    }
    private void prepare(Actor f, Input in) {
        var s = f.state; int t = now();
        if (f.ledge.attached()) { s.requestGuard(t, false, false); s.tickGuard(t, false); return; }
        s.requestGuard(t, in.shift() && !f.recovery.helpless(), f.grounded);
        if (s.tickGuard(t, in.shift() && !f.recovery.helpless())) particles(f, ParticleTypes.CRIT, 20);
        var buffered = s.pending(t);
        if (buffered != null) begin(f, buffered);
        effects.anticipation(f);
        if (s.blocking(t) && t % 6 == 0) particles(f, ParticleTypes.SOUL_FIRE_FLAME, 5);
        if (s.motionType == 4 && t < s.motionUntil && t >= s.stunUntil) {
            // A fast falling model is still interpolating after physics lands. The shockwave
            // starts when its visible feet reach the platform, rather than bursting in midair.
            if (f.grounded && Math.abs(f.pose.y - f.y) < .06) {
                s.motionType = 0; s.motionUntil = 0; s.impactAt = t; s.readyAt = t + FighterMoves.SLAM_LANDING_LOCKOUT;
            } else if (!f.grounded) { f.vx = 0; f.vy = FighterMoves.SLAM_FALL_SPEED; }
        } else if (s.motionType == 4 && s.motionUntil > 0 && t >= s.motionUntil) { s.interrupt(); s.readyAt = t + 10; }
        if (s.motionType == 3 && t < s.motionUntil && t >= s.stunUntil) { f.vy = FighterMoves.recoveryY(f.kind); if (t % 2 == 0) particles(f, ParticleTypes.FIREWORK, 2); }
    }
    private void move(Actor f, Input in) {
        var s = f.state; int t = now();
        if (s.floating(t)) {
            f.jump.clear(); f.jumpHeight.clear();
            f.x = RespawnRules.X; f.y = RespawnRules.y(t - s.floatingStartedAt); f.vx = f.vy = 0; f.grounded = false; f.previous = in; return;
        }
        if (s.floatingStartedAt >= 0 && t >= s.floatingUntil) {
            f.x = RespawnRules.X; f.y = 89; f.vx = f.vy = 0; f.grounded = true; f.recovery.reset(); s.floatingStartedAt = -1;
        }
        f.recovery.grounded(f.grounded, t);
        f.kit.grounded(f.grounded);
        boolean locked = s.blocking(t) || t < s.stunUntil;
        f.jump.observe(in.jump(), f.previous.jump(), f.grounded, t);
        if (t < s.stunUntil || s.slamCommitted(t)) f.jump.clear();
        int axis = (in.right() ? 1 : 0) - (in.left() ? 1 : 0);
        if (!locked && !s.facingLocked(t) && axis != 0) {
            f.facing = axis;
            if (s.drawingBow()) s.attackDirection = axis;
        }
        if (f.ledge.attached()) { moveOnLedge(f, in); return; }
        f.down.observe(in.backward(), t, ArenaRules.standingOnPlatform(f.x, f.y, .5));
        if (!locked && !s.slamCommitted(t)) {
            if (f.down.takeDrop(t) && f.grounded && ArenaRules.standingOnPlatform(f.x, f.y, .5)) { f.jump.clear(); f.jumpHeight.clear(); f.recovery.drop(t); f.grounded = false; f.vy = -.12; }
            if (f.jump.pending(t) && !f.recovery.helpless()) {
                if (f.jump.groundJump(f.grounded, t) || f.recovery.jump(false)) {
                    f.jump.consume(); f.jumpHeight.start(); f.vy = MovementRules.JUMP; f.grounded = false; f.recovery.cancelFastFall();
                } else if (!f.grounded && f.recovery.recoveryAvailable()) {
                    // A fresh press after the air jump uses the existing recovery, with its normal attack buffer and limits.
                    submit(f, new AttackIntent(AttackKind.RECOVERY, AttackDirection.UP, axis, false, 0, axis == 0 ? f.facing : axis));
                    f.jump.consume();
                }
            }
            if (f.down.fastFall(t)) f.recovery.fastFall(f.grounded, f.vy);
        }
        if (s.blocking(t)) f.vx = f.grounded ? 0 : f.vx * .98;
        else if (t < s.stunUntil) f.vx *= f.grounded ? .80 : MovementRules.LAUNCH_DRAG;
        else if ((s.motionType == 1 || s.motionType >= 5) && t < s.motionUntil) f.vx = s.motionX;
        else if (s.motionType == 4 && t < s.motionUntil) f.vx = 0;
        else {
            f.vx = MovementRules.steer(f.vx, axis, in.sprint(), f.grounded, FighterMoves.run(f.kind),
                    FighterMoves.air(f.kind), s.drawingBow() ? BowRules.DRAW_MOVEMENT : 1);
        }
        if (!f.grounded) {
            f.vy = f.jumpHeight.apply(f.vy, in.jump());
            f.vy = MovementRules.gravity(f.vy);
            if (f.recovery.fastFalling()) f.vy = Math.min(f.vy, MovementRules.FAST_FALL_START);
            if (f.recovery.fastFalling()) f.vy = MovementRules.fastFallVelocity(f.vy);
        }
        double before = f.y, beforeX = f.x;
        f.x += f.vx; f.y += f.vy; f.grounded = false;
        double floor = f.x >= ArenaRules.FLOOR_LEFT && f.x <= ArenaRules.FLOOR_RIGHT ? ArenaRules.DECK_Y : -100;
        if (!f.recovery.dropping(t)) {
            if (f.x >= -3.3 && f.x <= 4.3 && before >= 89 && f.y <= 89) floor = 89;
            else if (((f.x >= -12.3 && f.x <= -3.7) || (f.x >= 4.7 && f.x <= 13.3)) && before >= 85 && f.y <= 85) floor = 85;
        }
        if (f.vy <= 0 && before >= floor && f.y <= floor) { f.y = floor; f.vy = 0; f.grounded = true; s.launchUntil = 0; f.jumpHeight.clear(); f.ledge.land(); }
        if (!f.grounded && f.vy == 0) f.vy = -MovementRules.FALL_GRAVITY;
        if (!f.grounded && !in.backward() && t >= s.stunUntil && !s.slamCommitted(t) && s.impactAt < 0 && t >= s.activeUntil) {
            int side = f.ledge.candidate(t, beforeX, before, f.x, f.y, f.vy);
            if (side != 0) catchLedge(f, side);
        }
        f.previous = in;
    }
    private void catchLedge(Actor f, int side) {
        // A returning fighter displaces a hanger rather than being denied the edge in a free-for-all.
        for (var other : actors.values()) if (other != f && other.ledge.side() == side && !other.ledge.climbing()) leaveLedge(other, false, 0);
        var s = f.state; int t = now();
        f.ledge.grab(side, t); s.interrupt(); s.guardUntil = 0; s.launchUntil = 0; s.strongLaunch = false;
        s.protectedUntil = f.ledge.firstGrab() ? t + LedgeState.PROTECTION_TICKS : 0;
        f.jump.clear(); f.jumpHeight.clear(); f.down.clear(); f.recovery.cancelFastFall(); effects.remove(f);
        f.x = LedgeState.hangX(side); f.y = LedgeState.HANG_Y; f.vx = f.vy = 0; f.grounded = false; f.facing = -side;
        if (f.owner != null) f.owner.stopUsingItem();
        level.sendParticles(ParticleTypes.END_ROD, true, false, LedgeState.edge(side), ArenaRules.DECK_Y, .8, 4, .12, .08, .05, .01);
        arenaSound(SoundEvents.CHAIN_PLACE, .25f, 1.5f);
    }
    private void moveOnLedge(Actor f, Input in) {
        int t = now(), axis = (in.right() ? 1 : 0) - (in.left() ? 1 : 0);
        f.jump.observe(in.jump(), f.previous.jump(), false, t); f.down.clear(); f.vx = f.vy = 0;
        if (f.ledge.climbing()) {
            f.x = f.ledge.x(t); f.y = f.ledge.y(t);
            if (f.ledge.climbFinished(t)) {
                f.ledge.land(); f.grounded = true; f.recovery.grounded(true, t); f.kit.grounded(true);
                f.state.protectedUntil = 0; f.state.readyAt = t;
            }
        } else switch (f.ledge.action(t, f.jump.pending(t), in.backward(), axis)) {
            case CLIMB -> { f.jump.clear(); f.ledge.climb(t); }
            case JUMP -> leaveLedge(f, true, axis);
            case DROP -> leaveLedge(f, false, axis);
            case NONE -> { }
        }
        f.previous = in;
    }
    private void leaveLedge(Actor f, boolean jump, int axis) {
        int side = f.ledge.side();
        f.ledge.release(now()); f.state.protectedUntil = 0;
        f.jump.clear(); f.down.clear(); f.jumpHeight.clear();
        f.grounded = false; f.vx = jump ? axis * .3 : side * .18; f.vy = jump ? MovementRules.JUMP : -.18;
        if (jump) f.jumpHeight.start();
    }
    private void resolveStartup(Actor f) {
        var s = f.state; int t = now();
        if (s.move == null || s.impactAt < 0 || t < s.impactAt) return;
        if (!s.move.melee()) {
            switch (s.move.technique()) {
                case QUICK_ARROW, UP_ARROW, DOWN_ARROW, SCATTER -> objects.quickArrows(f);
                case WIND_STEP -> {
                    s.motionType = 8; s.motionX = -s.attackDirection*.85; s.motionUntil = t+5;
                    f.vy = f.grounded ? .55 : Math.max(f.vy,.25); f.grounded = false; f.jumpHeight.clear(); f.recovery.cancelFastFall(); effects.departure(f);
                }
                case GOLEM -> { } // Its visible windup began when the input was accepted.
                default -> objects.kits.spawn(f);
            }
            s.impactAt = -1; return;
        }
        if (s.move.id() == 6 && f.kind == FighterClass.SKELETON) {
            if (!s.chargeReleased) { s.impactAt = t + 1; s.readyAt = t + 1; return; }
            objects.arrow(f, s.releasedCharge); s.impactAt = -1; s.readyAt = t + 10; return;
        }
        if (s.move.id() == 6 && f.kind == FighterClass.VILLAGER) {
            f.body.swing(InteractionHand.MAIN_HAND);
            if (s.move.name().equals("Bell Ring")) objects.ring(f); else objects.bell(f);
            s.impactAt = -1; return;
        }
        if (s.move.id() == 6 && f.kind == FighterClass.ALEX && !s.burstStarted) {
            f.body.swing(InteractionHand.MAIN_HAND);
            s.burstStarted = true; s.motionType = 1; s.motionX = s.attackDirection * .92;
            s.motionUntil = t + 4; s.impactAt = t + 2; return;
        }
        if (FighterMoves.isSlam(s.move) && !f.grounded) {
            s.motionType = 4; s.motionUntil = t + FighterMoves.SLAM_DIVE_TICKS; s.impactAt = -1;
            s.readyAt = s.motionUntil + FighterMoves.SLAM_LANDING_LOCKOUT;
            f.vx = 0; f.vy = FighterMoves.SLAM_FALL_SPEED; f.jumpHeight.clear(); f.recovery.cancelFastFall(); return;
        }
        if (f.kind == FighterClass.SKELETON && s.move.id() == 4) {
            s.motionType = 5; s.motionX = -s.attackDirection * .42; s.motionUntil = t + 3;
        }
        if (f.kind == FighterClass.ALEX && (s.move.id() == 4 || s.move.id() == 5)) {
            s.motionType = s.move.id() == 4 ? 6 : 7; s.motionX = s.attackDirection * (s.move.id() == 4 ? .6 : .5); s.motionUntil = t+5;
            if (s.move.id() == 5) { f.vy = Math.min(f.vy,-.9); f.jumpHeight.clear(); }
        }
        s.impactAt = -1; s.activeStartedAt = t; s.activeUntil = t + FighterMoves.activeTicks(s.move);
        if (FighterMoves.isSlam(s.move)) f.body.swing(InteractionHand.MAIN_HAND);
        if (s.move.damage() > 0) effects.strike(f);
        effects.swing(f);
    }
    public static AABB hitbox(Actor f, FighterMoves.Move move, int direction) {
        var bounds = CombatGeometry.shape(move, direction, f.pose.x, f.pose.y).bounds();
        return new AABB(bounds.minX(), bounds.minY(), -.15, bounds.maxX(), bounds.maxY(), 1.15);
    }
    public void hit(Actor attacker, Actor target, int direction, FighterMoves.Move move) {
        hit(attacker, target, direction, move, new CombatGeometry.Point(target.pose.x, target.pose.y + .9));
    }
    public void hit(Actor attacker, Actor target, int direction, FighterMoves.Move move, CombatGeometry.Point contact) {
        if (!game.fighting(target)) return;
        int before = target.state.percent;
        var result = target.state.receiveHit(now(), attacker.id, direction, move, target.grounded);
        if (result.launch() != null || result.armored()) attacker.damageDealt += target.state.percent - before;
        if (result.blocked()) {
            effects.contact(contact, attacker.kind, move, true);
            if (attacker.kind == FighterClass.ALEX && move.id() == 6) {
                attacker.state.interrupt(); attacker.state.readyAt = Math.max(attacker.state.readyAt, now() + 10);
                attacker.vx = 0;
            } else if (!move.detached()) {
                attacker.state.interrupt(); attacker.state.motionType = 9;
                attacker.state.motionX = -direction*.38; attacker.state.motionUntil = now()+3;
                attacker.state.readyAt = Math.max(attacker.state.readyAt,now()+7);
            }
            if (move.damage() >= 12) { if (!move.detached()) attacker.state.pause(now(), 1); target.state.pause(now(), 1); }
            arenaSound(result.guardBroken() ? SoundEvents.SHIELD_BREAK.value() : SoundEvents.SHIELD_BLOCK.value(), .8f, 1);
        } else if (result.armored()) {
            effects.armor(target);
            arenaSound(SoundEvents.SHIELD_BLOCK.value(), .35f, .65f);
            level.getChunkSource().sendToTrackingPlayers(target.body, new ClientboundHurtAnimationPacket(target.body));
        } else if (result.launch() != null) {
            if (target.ledge.attached()) target.ledge.release(now());
            if (move.technique() == FighterMoves.Technique.BITE) {
                attacker.state.percent = Math.max(0,attacker.state.percent-6);
                particles(attacker,ParticleTypes.HEART,3); arenaSound(SoundEvents.GENERIC_EAT.value(),.55f,.75f);
            }
            if (attacker.kind == FighterClass.ALEX && move.id() == 5 && attacker.kit.bounce()) {
                attacker.state.motionUntil = 0; attacker.state.motionType = 0;
                attacker.vy = .80; attacker.grounded = false; attacker.jumpHeight.clear(); attacker.recovery.cancelFastFall();
            }
            if (attacker.state.confirm(now(), move)) effects.confirmed(attacker);
            target.jump.clear(); target.jumpHeight.clear();
            target.vx = result.launch().x(); target.vy = result.launch().y(); target.grounded = false;
            target.recovery.cancelFastFall(); if (target.owner != null) target.owner.stopUsingItem();
            effects.contact(contact, attacker.kind, move, false);
            int pause = move.damage() >= 12 ? 2 : target.state.strongLaunch ? 1 : 0;
            if (pause > 0) {
                target.state.pause(now(), pause);
                // A distant trap does not stop its owner's unrelated movement or attack.
                if (!move.detached()) attacker.state.pause(now(), pause);
            }
            float pitch = move.kind() != AttackKind.LIGHT ? .8f
                    : move.aim() == AttackDirection.UP ? 1.45f : move.aim() == AttackDirection.DOWN ? 1.05f : 1.25f;
            arenaSound(move.kind() == AttackKind.LIGHT ? SoundEvents.PLAYER_ATTACK_STRONG : SoundEvents.PLAYER_ATTACK_CRIT, .65f, pitch);
            if (target.state.strongLaunch) arenaSound(SoundEvents.PLAYER_ATTACK_KNOCKBACK, .75f, .7f);
            level.getChunkSource().sendToTrackingPlayers(target.body, new ClientboundHurtAnimationPacket(target.body));
        }
    }
    public void ringOut(Actor f) {
        if (!game.fighting(f)) return;
        koEffect(f);
        objects.remove(f); effects.remove(f); f.state.falls++;
        var attacker = actors.get(f.state.creditedAttacker(now())); if (attacker != null) attacker.state.knockouts++;
        if (!sandbox) game.match.ringOut(f.id);
        if (!sandbox && game.match.stocks(f.id) == 0) { eliminate(f); return; }
        f.state.respawn(now()); f.state.beginFloat(now()); f.recovery.reset(); f.ledge.land(); f.kit.reset(); f.down.clear(); f.jump.clear(); f.jumpHeight.clear();
        f.x = .5; f.y = RespawnRules.TOP_Y; f.vx = f.vy = 0; f.grounded = false;
        f.pose.reset(f.x, f.y);
        if (f.owner != null) f.owner.stopUsingItem();
    }
    public void eliminate(Actor f) { f.eliminated = true; f.ledge.land(); objects.remove(f); effects.remove(f); f.state.interrupt(); f.jump.clear(); f.body.discard(); f.marker.setText(Component.empty()); if (f.owner != null) f.owner.stopUsingItem(); }
    public void reset(Actor f, double x, double y) {
        objects.remove(f); effects.remove(f); f.state.respawn(now()); f.recovery.reset(); f.ledge.land(); f.kit.reset(); f.down.clear(); f.jump.clear(); f.jumpHeight.clear();
        f.x = x; f.y = y; f.vx = f.vy = 0; f.grounded = y == 81 || y == 85 || y == 89;
        f.pose.reset(x, y);
        f.previous = Input.EMPTY; if (f.owner != null) f.owner.stopUsingItem(); sync(f);
    }
    public void resetTraining() { int index = 0; for (var f : actors.values()) reset(f, f.owner == null ? 14.5 : ArenaRules.spawnX(index++), 81); }
    private void sync(Actor f) {
        boolean crouching = !f.ledge.attached() && game.fighting(f) && !f.state.floating(now()) && now() >= f.state.stunUntil && f.previous.backward()
                || f.kind == FighterClass.ALEX && f.state.motionType == 6 && now() < f.state.motionUntil;
        // Humanoid clients render CROUCHING directly; the native villager model has no crouch animation.
        double crouchDip = crouching && f.kind == FighterClass.VILLAGER ? .24 : 0;
        if (f.marker != null) f.marker.setPos(f.pose.x, f.pose.y + f.body.getBbHeight() + .45, .7);
        f.body.clearFire(); f.body.setDeltaMovement(Vec3.ZERO); f.body.setPos(f.x, f.y - crouchDip, .5);
        f.body.setPose(crouching ? Pose.CROUCHING : Pose.STANDING);
        f.body.setXRot(f.ledge.attached() ? -20 : crouchDip > 0 ? 15 : 0);
        int facing = f.state.facingLocked(now()) ? f.state.attackDirection : f.facing;
        f.body.setYRot(facing > 0 ? -90 : 90); f.body.setYHeadRot(f.body.getYRot()); f.body.yBodyRot = f.body.getYRot();
        f.body.setOnGround(f.grounded); f.body.needsSync = true;
        f.body.setInvisible(RespawnRules.dim((int)(f.state.protectedUntil - now())));
        boolean guard = f.state.blocking(now()), drawing = f.state.drawingBow()
                || f.kind == FighterClass.SKELETON && f.state.move != null && !f.state.move.melee() && f.state.impactAt >= now();
        Item tool = switch (f.kind) {
            case STEVE -> f.state.move == null ? Items.IRON_SWORD
                    : f.state.move.technique() == FighterMoves.Technique.TNT ? Items.TNT
                    : f.state.move.technique() == FighterMoves.Technique.ANVIL || f.state.move.id() == 6 || f.state.move.kind() == AttackKind.RECOVERY ? Items.IRON_PICKAXE
                    : f.state.move.aim() == AttackDirection.DOWN ? Items.IRON_SHOVEL : Items.IRON_SWORD;
            case ALEX -> Items.GOLDEN_SWORD;
            case SKELETON -> drawing || f.state.move != null && !f.state.move.melee() ? Items.BOW : Items.BONE;
            default -> Items.AIR;
        };
        if (f.ledge.attached()) tool = Items.AIR;
        if (!f.body.getMainHandItem().is(tool)) f.body.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(tool));
        if (guard && !f.body.getOffhandItem().is(Items.SHIELD)) f.body.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        if (!guard && !f.body.getOffhandItem().isEmpty()) f.body.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        if (guard || drawing) {
            var hand = guard ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            if (!f.body.isUsingItem() || f.body.getUsedItemHand() != hand) f.body.startUsingItem(hand);
        } else f.body.stopUsingItem();
        boolean winding = f.state.impactAt > now() && f.state.move != null && f.state.move.damage() > 0;
        if (f.body instanceof Mob mob) mob.setAggressive(drawing || winding);
        var held = f.body.getMainHandItem();
        if (!held.isEmpty()) held.set(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE,
                winding && f.state.move.kind() == AttackKind.HEAVY || f.state.specialConfirm(now()));
    }
    public void particles(Actor f, SimpleParticleType type, int count) { level.sendParticles(type, true, false, f.x, f.y + 1, .8, count, .3, .4, .1, .03); }
    private void sound(Actor f, SoundEvent sound, float volume, float pitch) { level.playSound(null, f.body.blockPosition(), sound, SoundSource.PLAYERS, volume, pitch); }
    void arenaSound(SoundEvent sound, float volume, float pitch) {
        // Listeners are at the side camera; a blast-zone sound at the actor would be too far away.
        for (var view : game.viewers.values()) view.player().connection.send(new ClientboundSoundPacket(
                Holder.direct(sound), SoundSource.PLAYERS, view.camera().getX(), view.camera().getY(), view.camera().getZ(),
                volume, pitch, level.getRandom().nextLong()));
    }
    private void koEffect(Actor f) {
        double x = Double.isFinite(f.x) ? Math.clamp(f.x, ArenaRules.BLAST_LEFT + 2, ArenaRules.BLAST_RIGHT - 2) : .5;
        double y = Double.isFinite(f.y) ? Math.clamp(f.y + 1, 61, 102) : 84;
        double dx = f.x < ArenaRules.BLAST_LEFT ? -1 : f.x > ArenaRules.BLAST_RIGHT ? 1 : 0,
                dy = f.y > ArenaRules.BLAST_TOP ? 1 : f.y < ArenaRules.BLAST_BOTTOM ? -1 : 0;
        koBursts.add(new KoBurst(x, y, dx, dy, now()));
        level.sendParticles(ParticleTypes.EXPLOSION, true, false, x, y, .8, 1, 0, 0, 0, 0);
        level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 1f, .8f, .35f), true, false, x, y, .8, 1, 0, 0, 0, 0);
        arenaSound(SoundEvents.GENERIC_EXPLODE.value(), .75f, 1.35f);
        arenaSound(SoundEvents.FIREWORK_ROCKET_BLAST, .8f, .8f);
    }
    private void tickKoEffects() {
        koBursts.removeIf(b -> now() - b.startedAt() > 10);
        for (var b : koBursts) {
            int age = now() - b.startedAt();
            if (age % 2 != 0) continue;
            double radius = .3 + age * .22;
            for (int i = 0; i < 12; i++) {
                double angle = Math.PI * 2 * i / 12;
                level.sendParticles(ParticleTypes.FIREWORK, true, false,
                        b.x() + b.dx() * age * .18 + Math.cos(angle) * radius,
                        b.y() + b.dy() * age * .18 + Math.sin(angle) * radius, .8, 1, 0, 0, 0, 0);
            }
        }
    }
    public void close() { hud.close(); koBursts.clear(); effects.close(); objects.clear(); for (var f : actors.values()) f.body.discard(); actors.clear(); displays.forEach(Entity::discard); displays.clear(); timer.discard(); }
}
