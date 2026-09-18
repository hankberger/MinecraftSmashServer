package dev.hanks.vanilla;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.dialog.action.Action;
import net.minecraft.server.dialog.action.StaticAction;
import net.minecraft.server.level.ServerPlayer;

/** Stock dialog events keep UI input separate from Minecraft's chat/command spam budget. */
public final class MenuActions {
    public static final Identifier FIGHTER = Identifier.fromNamespaceAndPath("smash_vanilla", "fighter");
    public static final Identifier MATCH = Identifier.fromNamespaceAndPath("smash_vanilla", "menu");

    private MenuActions() {}

    public static ClickEvent.Custom event(Identifier id, UUID token, int button) {
        var payload = new CompoundTag();
        payload.putString("token", token.toString());
        payload.putInt("button", button);
        return new ClickEvent.Custom(id, Optional.of(payload));
    }

    public static JsonElement dialogAction(Identifier id, UUID token, int button) {
        return Action.CODEC.encodeStart(JsonOps.INSTANCE, new StaticAction(event(id, token, button))).getOrThrow();
    }

    public static boolean handles(Identifier id) { return FIGHTER.equals(id) || MATCH.equals(id); }

    /** Called on the server thread; malformed, stale and foreign tokens are inert. */
    public static void handle(ServerPlayer player, ServerboundCustomClickActionPacket packet) {
        if (!handles(packet.id()) || !(packet.payload().orElse(null) instanceof CompoundTag payload)
                || payload.size() != 2 || !(payload.get("token") instanceof StringTag)
                || !(payload.get("button") instanceof NumericTag number)) return;
        String value = payload.getStringOr("token", "");
        int button = payload.getIntOr("button", -1);
        // Dialog JSON may compact an IntTag to a ByteTag; accept exact integers only.
        if (value.length() != 36 || button < 0 || button > 100 || number.doubleValue() != button) return;
        UUID token;
        try { token = UUID.fromString(value); }
        catch (IllegalArgumentException ignored) { return; }
        if (FIGHTER.equals(packet.id())) VanillaSmash.instance().fighterMenu.action(player, token, button);
        else VanillaSmash.instance().hub.menu.click(player, token, button);
    }
}
