package dev.hanks.vanilla;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import java.util.*;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.common.ClientboundClearDialogPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.*;

/** Compact vanilla main menu; secondary dialogs retain one-use, player-bound button tokens. */
public final class MatchMenu {
    public record Button(String label, Runnable action) {}
    private record Open(UUID token, List<Button> buttons, boolean main) {}
    private final Map<UUID, Open> open = new HashMap<>();
    private record Grid(ChestMenu container,Map<Integer,Button> buttons) {}
    private final Map<UUID,Grid> grids = new HashMap<>();
    public boolean mainOpen(UUID player) { var menu = open.get(player); return grids.containsKey(player) || menu != null && menu.main; }
    public void forget(UUID player) { open.remove(player); grids.remove(player); }
    public void clear(ServerPlayer p) {
        var grid = grids.remove(p.getUUID()); open.remove(p.getUUID());
        if (grid != null && p.containerMenu == grid.container) p.closeContainer();
        p.connection.send(ClientboundClearDialogPacket.INSTANCE);
    }
    public void show(ServerPlayer p, String title, String body, List<Button> buttons, boolean main, Runnable back) {
        if (main) { showGrid(p,body,buttons,back); return; }
        clear(p);
        var entries = new ArrayList<>(buttons); entries.add(new Button("Back", back));
        var menu = new Open(UUID.randomUUID(), List.copyOf(entries), main); open.put(p.getUUID(), menu);
        var json = new JsonObject(); json.addProperty("type", "minecraft:multi_action");
        json.addProperty("title", title); json.addProperty("pause", false); json.addProperty("after_action", "close"); json.addProperty("columns", 2);
        var message = new JsonObject(); message.addProperty("type", "minecraft:plain_message"); message.addProperty("contents", body); message.addProperty("width", 310);
        json.add("body", message);
        var actions = new JsonArray();
        for (int i = 0; i < buttons.size(); i++) actions.add(action(menu, i));
        if (buttons.isEmpty()) { actions.add(action(menu, entries.size() - 1)); }
        json.add("actions", actions); json.add("exit_action", action(menu, entries.size() - 1));
        var ops = p.level().registryAccess().createSerializationContext(JsonOps.INSTANCE);
        p.openDialog(Dialog.CODEC.parse(ops, json).getOrThrow());
    }
    private static ItemStack icon(Item item,String name,int color,List<Component> lore) {
        var stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME,Component.literal(name).withStyle(s -> s.withColor(color).withItalic(false).withBold(true)));
        stack.set(DataComponents.LORE,new ItemLore(lore));
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS,ItemAttributeModifiers.EMPTY);
        return stack;
    }
    private void showGrid(ServerPlayer p,String body,List<Button> buttons,Runnable back) {
        clear(p);
        var contents = new SimpleContainer(27); var actions = new HashMap<Integer,Button>();
        var divider = icon(Items.STAINED_GLASS_PANE.gray()," ",0xffffff,List.of());
        divider.set(DataComponents.TOOLTIP_DISPLAY,new TooltipDisplay(true,new LinkedHashSet<>()));
        var header = new ItemStack(Items.STAINED_GLASS_PANE.blue()); header.set(DataComponents.TOOLTIP_DISPLAY,new TooltipDisplay(true,new LinkedHashSet<>()));
        for (int i=0;i<18;i++) contents.setItem(i,i<9?header.copy():divider.copy());
        var roster = body.lines().filter(line -> !line.isBlank()).map(line -> (Component)Component.literal(line).withStyle(s -> s.withColor(0xc6d6e6).withItalic(false))).toList();
        contents.setItem(4,icon(Items.PLAYER_HEAD,"Party",0x93c5fd,roster));
        for (var button : buttons) {
            String name = button.label; int slot; Item item; int color=0xffd66b; List<Component> lore=List.of();
            switch(name) {
                case "1v1" -> { slot=20; item=Items.IRON_SWORD; lore=List.of(Component.literal("2 players · 3 stocks")); }
                case "Free-for-all" -> { slot=22; item=Items.FIREWORK_ROCKET; lore=List.of(Component.literal("4 players · 3 stocks")); }
                case "Practice" -> { slot=24; item=Items.ARMOR_STAND; lore=List.of(Component.literal("Sparring dummy")); }
                case "Last match" -> { slot=0; item=Items.CLOCK; }
                case "Create party", "Manage party" -> { slot=4; item=Items.PLAYER_HEAD; color=0x93c5fd; lore=roster; }
                case "Invite player" -> { slot=6; item=Items.EMERALD; color=0x93c5fd; }
                case "Leave party" -> { slot=8; item=Items.OAK_DOOR; color=0x93c5fd; }
                case "Change fighter" -> { slot=22; item=Items.ARMOR_STAND; }
                case "Cancel matchmaking" -> { slot=24; item=Items.DYE.red(); }
                default -> {
                    if(name.startsWith("Invitations")) { slot=2; item=Items.WRITABLE_BOOK; color=0x93c5fd; }
                    else if(name.startsWith("Ready as")) { slot=20; item=Items.DYE.lime(); }
                    else throw new IllegalArgumentException("Unknown main-menu action: " + name);
                }
            }
            contents.setItem(slot,icon(item,name,color,lore)); actions.put(slot,button);
        }
        contents.setItem(26,icon(Items.BARRIER,"Back",0xb0b0b0,List.of())); actions.put(26,new Button("Back",back));
        p.openMenu(new SimpleMenuProvider((id,inventory,player) -> {
            var container = new ChestMenu(MenuType.GENERIC_9x3,id,inventory,contents,3) {
                @Override public boolean stillValid(net.minecraft.world.entity.player.Player player) { return true; }
                @Override public void removed(net.minecraft.world.entity.player.Player player) {
                    super.removed(player);
                    var current=grids.get(player.getUUID()); if(current!=null && current.container==this) grids.remove(player.getUUID());
                }
            };
            grids.put(p.getUUID(),new Grid(container,Map.copyOf(actions))); return container;
        },Component.literal("Smash  ·  Play").withStyle(s -> s.withColor(0xffd66b))));
    }
    public boolean gridClick(ServerPlayer p,ServerboundContainerClickPacket packet) {
        var grid=grids.get(p.getUUID()); if(grid==null) return false;
        if(p.containerMenu!=grid.container || packet.containerId()!=grid.container.containerId) return true;
        var button=grid.buttons.get((int)packet.slotNum());
        if(button!=null && packet.containerInput()==ContainerInput.PICKUP && (packet.buttonNum()==0 || packet.buttonNum()==1)) {
            clear(p); button.action.run();
        } else p.containerMenu.sendAllDataToRemote();
        return true;
    }
    private JsonObject action(Open menu, int index) {
        var json = new JsonObject(); json.addProperty("label", menu.buttons.get(index).label); json.addProperty("width", 150);
        var action = new JsonObject(); action.addProperty("type", "run_command");
        action.addProperty("command", "smash ui " + menu.token + " " + index); json.add("action", action); return json;
    }
    public boolean click(ServerPlayer p, UUID token, int index) {
        var menu = open.get(p.getUUID());
        if (menu == null || !menu.token.equals(token) || index < 0 || index >= menu.buttons.size()) return false;
        open.remove(p.getUUID()); menu.buttons.get(index).action.run(); return true;
    }
}
