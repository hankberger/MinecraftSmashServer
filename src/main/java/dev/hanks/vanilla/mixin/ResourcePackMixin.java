package dev.hanks.vanilla.mixin;

import dev.hanks.vanilla.VanillaSmash;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.server.network.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ResourcePackMixin {
    @Inject(method="handleResourcePackResponse",at=@At("TAIL"))
    private void packResponse(ServerboundResourcePackPacket packet,CallbackInfo ci) {
        if ((Object)this instanceof ServerGamePacketListenerImpl play) {
            var p=play.player; p.level().getServer().execute(()->VanillaSmash.instance().uiPack.response(p,packet));
        }
    }
}
