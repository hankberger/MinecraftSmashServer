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
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.core.component.DataComponents;
import dev.hanks.network.Cosmetics;

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
        return create(level,kind,Cosmetics.DEFAULT);
    }
    public static LivingEntity create(ServerLevel level, FighterClass kind, String skin) {
        skin=Cosmetics.skin(kind.name(),skin).id();
        boolean alternate=!skin.equals(Cosmetics.DEFAULT);
        LivingEntity body = switch (kind) {
            case STEVE, ALEX -> new Avatar(level, kind == FighterClass.ALEX);
            case ZOMBIE -> alternate?new Husk(EntityTypes.HUSK, level):new Zombie(EntityTypes.ZOMBIE, level);
            case SKELETON -> alternate?new Stray(EntityTypes.STRAY, level):new Skeleton(EntityTypes.SKELETON, level);
            case VILLAGER -> new Villager(EntityTypes.VILLAGER, level);
        };
        if(alternate) switch(kind) {
            case STEVE -> {
                body.setItemSlot(EquipmentSlot.CHEST,new ItemStack(Items.DIAMOND_CHESTPLATE));
                body.setItemSlot(EquipmentSlot.FEET,new ItemStack(Items.DIAMOND_BOOTS));
            }
            case ALEX -> {
                var tunic=new ItemStack(Items.LEATHER_CHESTPLATE);tunic.set(DataComponents.DYED_COLOR,new DyedItemColor(0x9c7848));
                var cap=new ItemStack(Items.LEATHER_HELMET);cap.set(DataComponents.DYED_COLOR,new DyedItemColor(0x436d38));
                body.setItemSlot(EquipmentSlot.HEAD,cap);
                body.setItemSlot(EquipmentSlot.CHEST,tunic);
                body.setItemSlot(EquipmentSlot.FEET,new ItemStack(Items.LEATHER_BOOTS));
            }
            case VILLAGER -> {
                var villager=(Villager)body;
                villager.setVillagerData(villager.getVillagerData().withType(level.registryAccess(),VillagerType.DESERT));
            }
            default -> {}
        }
        // Only the visual proxy wears equipment. Combat uses the selected class's server-owned rules.
        return body;
    }
}
