package dev.hanks.vanilla;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.skeleton.Stray;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerType;
import dev.hanks.network.Cosmetics;

/** Shared native models for the showcase and arena; outfits use the existing server pack. */
public final class FighterModels {
    private FighterModels() {}
    private static final class Avatar extends Mannequin {
        Avatar(ServerLevel level, boolean alex, boolean alternate) {
            super(EntityTypes.MANNEQUIN, level);
            entityData.set(DATA_PROFILE, NativeUi.profile(alex, alternate));
        }
    }
    public static LivingEntity create(ServerLevel level, FighterClass kind) {
        return create(level,kind,Cosmetics.DEFAULT);
    }
    public static LivingEntity create(ServerLevel level, FighterClass kind, String skin) {
        skin=Cosmetics.skin(kind.name(),skin).id();
        boolean alternate=!skin.equals(Cosmetics.DEFAULT);
        LivingEntity body = switch (kind) {
            case STEVE, ALEX -> new Avatar(level, kind == FighterClass.ALEX, alternate);
            case ZOMBIE -> alternate?new Husk(EntityTypes.HUSK, level):new Zombie(EntityTypes.ZOMBIE, level);
            case SKELETON -> alternate?new Stray(EntityTypes.STRAY, level):new Skeleton(EntityTypes.SKELETON, level);
            case VILLAGER -> new Villager(EntityTypes.VILLAGER, level);
        };
        if(alternate) switch(kind) {
            case VILLAGER -> {
                var villager=(Villager)body;
                villager.setVillagerData(villager.getVillagerData().withType(level.registryAccess(),VillagerType.DESERT));
            }
            default -> {}
        }
        // Outfits never equip armor or change the class's combat rules.
        return body;
    }
}
