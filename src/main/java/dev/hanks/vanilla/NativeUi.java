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
        p.getInventory().setItem(8, named(Items.PLAYER_HEAD, "Party"));
        p.inventoryMenu.broadcastChanges();
    }
    public static void combatInventory(ServerPlayer p, FighterClass kind) {
        inputInventory(p,true);
    }
    public static void menuInputInventory(ServerPlayer p) { inputInventory(p,false); }
    private static void inputInventory(ServerPlayer p, boolean chargeable) {
        p.getInventory().clearContent();
        for (int i = 0; i < 9; i++) {
            // Hidden native bow supplies hold/release packets for every primary special.
            var stack = named(chargeable ? Items.BOW : Items.STICK, " ");
            stack.set(DataComponents.ITEM_MODEL, Identifier.withDefaultNamespace("air"));
            p.getInventory().setItem(i, stack);
        }
        if (chargeable) p.getInventory().setItem(9, new ItemStack(Items.ARROW));
        p.inventoryMenu.broadcastChanges();
    }
    private static ItemStack named(Item item, String name) {
        var stack = new ItemStack(item); stack.set(DataComponents.CUSTOM_NAME, Component.literal(name)); return stack;
    }
    public static void battleHud(VanillaSmash game) {
        String timer = game.battle.sandbox ? "Practice" : game.match.phase() == MatchState.Phase.ACTIVE
                ? String.format("%d:%02d", game.match.remaining() / 1200, game.match.remaining() / 20 % 60) : "";
        for (var view : game.viewers.values()) {
            game.battle.hud.update(view,timer);
            var own = game.actor(view.player()); if (own == null) continue;
            var text = Component.literal("P" + own.slot + " · YOU  " + (own.eliminated ? "OUT" : own.state.percent + "%"))
                    .withStyle(s -> s.withColor(own.color()).withBold(true));
            if (!own.eliminated) {
                text.append(Component.literal("  " + (game.battle.sandbox ? "∞" : "●".repeat(game.match.stocks(own.id)))));
                text.append(Component.literal("    Jump " + (own.recovery.available() ? "●" : "○") + "  Recovery " + (own.recovery.recoveryAvailable() ? "●" : "○"))
                        .withStyle(s -> s.withColor(0xeeeeee).withBold(false)));
                if (own.state.blocking(game.ticks)) text.append(Component.literal("  Shield " + own.state.guard).withStyle(ChatFormatting.AQUA));
                if (own.state.chargingSpecial()) {
                    int filled=(int)Math.round(ChargeRules.power(own.kind,own.state.chargeTicks(game.ticks))*6);
                    text.append(Component.literal("  " + "▰".repeat(filled) + "▱".repeat(6-filled)).withColor(own.kind.accent & 0xffffff));
                }
            }
            view.player().sendOverlayMessage(text);
        }
    }
}
