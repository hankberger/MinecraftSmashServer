package dev.hanks.vanilla.mixin;

import dev.hanks.vanilla.MvpWorlds;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Camera controllers must never be tracked as visible players beside the fighters. */
@Mixin(ServerPlayer.class)
public abstract class ControllerVisibilityMixin {
    @Inject(method = "broadcastToPlayer", at = @At("HEAD"), cancellable = true)
    private void hideController(ServerPlayer viewer, CallbackInfoReturnable<Boolean> result) {
        var self = (ServerPlayer)(Object)this;
        if (MvpWorlds.battle(self.level()) || self.level().dimension().equals(MvpWorlds.SHOWCASE))
            result.setReturnValue(false);
    }
}
