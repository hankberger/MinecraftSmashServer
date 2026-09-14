package dev.hanks.vanilla;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.npc.villager.Villager;

/** Shared native models for the showcase and the arena; no client assets required. */
public final class FighterModels {
    private FighterModels() {}
    private static final class Avatar extends Mannequin {
        Avatar(ServerLevel level, boolean alex) {
            super(EntityTypes.MANNEQUIN, level);
            entityData.set(DATA_PROFILE, NativeUi.profile(alex));
        }
    }
    public static LivingEntity create(ServerLevel level, FighterClass kind) {
        return switch (kind) {
            case STEVE, ALEX -> new Avatar(level, kind == FighterClass.ALEX);
            case ZOMBIE -> new Zombie(EntityTypes.ZOMBIE, level);
            case SKELETON -> new Skeleton(EntityTypes.SKELETON, level);
            case VILLAGER -> new Villager(EntityTypes.VILLAGER, level);
        };
    }
}
