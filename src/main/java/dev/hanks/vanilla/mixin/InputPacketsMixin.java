package dev.hanks.vanilla.mixin;

import dev.hanks.vanilla.VanillaSmash;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.PacketUtils;
import dev.hanks.vanilla.MvpWorlds;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Server-only input routing; HEAD handlers explicitly perform the server-thread handoff. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class InputPacketsMixin {
    @Shadow public ServerPlayer player;
    @Inject(method = "handleAttack", at = @At("HEAD"), cancellable = true)
    private void cameraAttack(net.minecraft.network.protocol.game.ServerboundAttackPacket packet, CallbackInfo ci) {
        PacketUtils.ensureRunningOnSameThread(packet, (ServerGamePacketListenerImpl)(Object)this, player.level().getServer().packetProcessor());
        var game = VanillaSmash.instance();
        if (game.stage.active(player)) { game.stage.clickEntity(player,packet.entityId()); ci.cancel(); }
        else if (game.viewers.containsKey(player.getUUID())) { game.attack(player,false); ci.cancel(); }
        else if (game.hub.results.scene.active(player) || player.level().dimension().equals(MvpWorlds.SHOWCASE)) ci.cancel();
        // Detached cameras can pick their owner's hidden player. Handle UI/combat input
        // before vanilla rejects that target as a self-attack; ordinary worlds stay vanilla.
    }
    @Inject(method = "handleInteract", at = @At("HEAD"), cancellable = true)
    private void pickerInteract(net.minecraft.network.protocol.game.ServerboundInteractPacket packet, CallbackInfo ci) {
        PacketUtils.ensureRunningOnSameThread(packet, (ServerGamePacketListenerImpl)(Object)this, player.level().getServer().packetProcessor());
        if (VanillaSmash.instance().stage.active(player)) {
            if (packet.hand() == net.minecraft.world.InteractionHand.MAIN_HAND) VanillaSmash.instance().stage.clickEntity(player,packet.entityId());
            ci.cancel();
        }
    }
    @Inject(method = "handleAnimate", at = @At("TAIL"))
    private void smashSwing(ServerboundSwingPacket packet, CallbackInfo ci) {
        if (packet.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND)
            VanillaSmash.instance().attack(player, false);
    }

    @Inject(method = "handlePlayerAction", at = @At("HEAD"), cancellable = true)
    private void smashActions(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
        PacketUtils.ensureRunningOnSameThread(packet, (ServerGamePacketListenerImpl)(Object)this, player.level().getServer().packetProcessor());
        if (!MvpWorlds.managed(player.level())) return;
        if (VanillaSmash.instance().hub.results.scene.active(player)
                && (packet.getAction() == ServerboundPlayerActionPacket.Action.DROP_ITEM
                || packet.getAction() == ServerboundPlayerActionPacket.Action.DROP_ALL_ITEMS)) {
            VanillaSmash.instance().hub.results.dismiss(player); ci.cancel(); return;
        }
        if (VanillaSmash.instance().stage.active(player)
                && (packet.getAction() == ServerboundPlayerActionPacket.Action.DROP_ITEM
                || packet.getAction() == ServerboundPlayerActionPacket.Action.DROP_ALL_ITEMS)) {
            VanillaSmash.instance().stage.cancel(player); ci.cancel(); return;
        }
        if (packet.getAction() == ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM) {
            VanillaSmash.instance().releaseBow(player); ci.cancel();
        } else if (packet.getAction() == ServerboundPlayerActionPacket.Action.DROP_ITEM
                || packet.getAction() == ServerboundPlayerActionPacket.Action.DROP_ALL_ITEMS
                || packet.getAction() == ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND) {
            player.inventoryMenu.sendAllDataToRemote(); ci.cancel();
        }
    }
    @Inject(method = "handleContainerClick", at = @At("HEAD"), cancellable = true)
    private void protectedInventory(ServerboundContainerClickPacket packet, CallbackInfo ci) {
        PacketUtils.ensureRunningOnSameThread(packet, (ServerGamePacketListenerImpl)(Object)this, player.level().getServer().packetProcessor());
        if (!MvpWorlds.managed(player.level())) return;
        if (VanillaSmash.instance().hub.menu.gridClick(player,packet)) { ci.cancel(); return; }
        player.containerMenu.sendAllDataToRemote(); ci.cancel();
    }
    @Inject(method = "handleSetCarriedItem", at = @At("HEAD"), cancellable = true)
    private void showcaseSelection(ServerboundSetCarriedItemPacket packet, CallbackInfo ci) {
        PacketUtils.ensureRunningOnSameThread(packet, (ServerGamePacketListenerImpl)(Object)this, player.level().getServer().packetProcessor());
        if (VanillaSmash.instance().hub.results.scene.selectSlot(player, packet.getSlot())
                || VanillaSmash.instance().stage.selectSlot(player, packet.getSlot())) ci.cancel();
    }
}
