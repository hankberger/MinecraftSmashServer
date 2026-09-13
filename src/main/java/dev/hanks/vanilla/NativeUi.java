package dev.hanks.vanilla;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ResolvableProfile;

/** Native menu and action bar. No custom client screen or resource pack dependency. */
public final class NativeUi {
    public static ResolvableProfile profile(boolean alex) {
        return ResolvableProfile.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"texture\":\"minecraft:entity/player/"
                + (alex ? "slim/alex" : "wide/steve") + "\",\"model\":\"" + (alex ? "slim" : "wide") + "\"}")).getOrThrow();
    }
    public static void openPicker(VanillaSmash game, ServerPlayer p) {
        var items = new SimpleContainer(27);
        Item[] icons = {Items.PLAYER_HEAD, Items.PLAYER_HEAD, Items.ZOMBIE_HEAD, Items.SKELETON_SKULL, Items.VILLAGER_SPAWN_EGG};
        for (int i = 0; i < 5; i++) {
            var stack = named(icons[i], FighterClass.values()[i].label);
            if (i < 2) stack.set(DataComponents.PROFILE, profile(i == 1));
            items.setItem(11 + i, stack);
        }
        p.openMenu(new SimpleMenuProvider((id, inv, player) -> new ChestMenu(MenuType.GENERIC_9x3, id, inv, items, 3) {
            @Override public void clicked(int slot, int button, ContainerInput input, Player player) {
                if (input != ContainerInput.PICKUP || slot < 11 || slot > 15 || !game.pickers.containsKey(p.getUUID())) return;
                var mode = game.pickers.remove(p.getUUID()); p.closeContainer(); game.choose(p, FighterClass.values()[slot - 11], mode);
            }
            @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
            @Override public void removed(Player player) { super.removed(player); game.pickers.remove(p.getUUID()); }
        }, Component.literal("Choose your fighter")));
    }
    public static void lobbyInventory(ServerPlayer p) {
        p.getInventory().clearContent();
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
