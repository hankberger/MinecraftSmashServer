package dev.hanks.vanilla.mixin;

import dev.hanks.vanilla.MenuActions;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class MenuActionsMixin {
    @Inject(method = "handleCustomClickAction", at = @At("HEAD"), cancellable = true)
    private void menuClick(ServerboundCustomClickActionPacket packet, CallbackInfo ci) {
        if (!MenuActions.handles(packet.id())) return;
        if ((Object)this instanceof ServerGamePacketListenerImpl play) {
            PacketUtils.ensureRunningOnSameThread(packet, play, play.player.level().getServer().packetProcessor());
            MenuActions.handle(play.player, packet);
        }
        ci.cancel();
    }
}
