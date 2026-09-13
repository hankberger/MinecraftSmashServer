package dev.hanks.vanilla.mixin;

import dev.hanks.vanilla.VanillaSmash;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.PacketUtils;
import dev.hanks.vanilla.MvpWorlds;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Server packet listener only. TAIL runs after vanilla's server-thread handoff. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class InputPacketsMixin {
    @Shadow public ServerPlayer player;
    @Inject(method = "handleAnimate", at = @At("TAIL"))
    private void smashSwing(ServerboundSwingPacket packet, CallbackInfo ci) {
        if (packet.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND)
            VanillaSmash.instance().attack(player, false);
    }

    @Inject(method = "handlePlayerAction", at = @At("HEAD"), cancellable = true)
    private void smashActions(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
        PacketUtils.ensureRunningOnSameThread(packet, (ServerGamePacketListenerImpl)(Object)this, player.level().getServer().packetProcessor());
        if (!MvpWorlds.managed(player.level())) return;
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
        if (!VanillaSmash.instance().pickers.containsKey(player.getUUID())) {
            player.containerMenu.sendAllDataToRemote(); ci.cancel();
        }
    }
}
