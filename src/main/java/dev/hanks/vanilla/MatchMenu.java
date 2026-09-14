package dev.hanks.vanilla;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import java.util.*;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.common.ClientboundClearDialogPacket;

/** Native Java dialogs. Button commands carry a one-use, player-bound menu token. */
public final class MatchMenu {
    public record Button(String label, Runnable action) {}
    private record Open(UUID token, List<Button> buttons, boolean main) {}
    private final Map<UUID, Open> open = new HashMap<>();
    public boolean mainOpen(UUID player) { var menu = open.get(player); return menu != null && menu.main; }
    public void forget(UUID player) { open.remove(player); }
    public void clear(ServerPlayer p) { forget(p.getUUID()); p.connection.send(ClientboundClearDialogPacket.INSTANCE); }
    public void show(ServerPlayer p, String title, String body, List<Button> buttons, boolean main, Runnable back) {
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
