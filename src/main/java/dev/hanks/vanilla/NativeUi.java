package dev.hanks.vanilla;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ResolvableProfile;

/** Native inventories and action bar. No custom client screen or resource pack dependency. */
public final class NativeUi {
    public static ResolvableProfile profile(boolean alex) {
        return ResolvableProfile.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"texture\":\"minecraft:entity/player/"
                + (alex ? "slim/alex" : "wide/steve") + "\",\"model\":\"" + (alex ? "slim" : "wide") + "\"}")).getOrThrow();
    }
    public static void lobbyInventory(ServerPlayer p) {
        p.getInventory().clearContent();
        p.getInventory().setSelectedSlot(0);
        p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket(0));
        p.getInventory().setItem(0, named(Items.COMPASS, "Play"));
        p.getInventory().setItem(4, named(Items.ARMOR_STAND, "Practice"));
        p.inventoryMenu.broadcastChanges();
    }
    public static void combatInventory(ServerPlayer p, FighterClass kind) {
        p.getInventory().clearContent();
        for (int i = 0; i < 9; i++) {
            var stack = named(kind == FighterClass.SKELETON ? Items.BOW : Items.STICK, " ");
            stack.set(DataComponents.ITEM_MODEL, Identifier.withDefaultNamespace("air"));
            p.getInventory().setItem(i, stack);
        }
        if (kind == FighterClass.SKELETON) p.getInventory().setItem(9, new ItemStack(Items.ARROW));
        p.inventoryMenu.broadcastChanges();
    }
    private static ItemStack named(Item item, String name) {
        var stack = new ItemStack(item); stack.set(DataComponents.CUSTOM_NAME, Component.literal(name)); return stack;
    }
    public static void battleHud(VanillaSmash game) {
        for (var view : game.viewers.values()) {
            var text = Component.empty();
            for (var f : game.battle.actors.values()) {
                if (!text.getString().isEmpty()) text.append("    ");
                String name = f.name(); if (name.length() > 12) name = name.substring(0, 12);
                text.append(Component.literal(name + " " + f.state.percent + "% " + (game.battle.sandbox ? "∞" : "•" + game.match.stocks(f.id)))
                        .withStyle(s -> s.withColor(f.kind.accent & 0xffffff)));
            }
            var own = game.actor(view.player());
            if (own != null && own.state.blocking(game.ticks)) text.append(Component.literal("    Shield " + own.state.guard).withStyle(ChatFormatting.AQUA));
            view.player().sendOverlayMessage(text);
        }
        String timer = game.battle.sandbox ? "Practice" : game.match.phase() == MatchState.Phase.ACTIVE
                ? String.format("%d:%02d", game.match.remaining() / 1200, game.match.remaining() / 20 % 60) : "";
        game.battle.timer.setText(Component.literal(timer));
    }
}
