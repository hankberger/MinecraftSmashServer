package dev.hanks.vanilla.mixin;

import com.mojang.authlib.GameProfile;
import dev.hanks.vanilla.VanillaSmash;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.BrandPayload;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ClientBrandMixin {
    @Shadow protected abstract GameProfile playerProfile();
    @Inject(method = "handleCustomPayload", at = @At("HEAD"))
    private void logBrand(ServerboundCustomPayloadPacket packet, CallbackInfo ci) {
        if (packet.payload() instanceof BrandPayload brand)
            VanillaSmash.LOG.info("VANILLA_PROBE_BRAND player={} brand={}", playerProfile().name(), brand.brand());
    }
}
