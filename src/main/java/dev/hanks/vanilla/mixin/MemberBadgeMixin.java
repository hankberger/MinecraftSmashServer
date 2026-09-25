package dev.hanks.vanilla.mixin;

import dev.hanks.vanilla.VanillaSmash;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public abstract class MemberBadgeMixin {
    @Inject(method="getTabListDisplayName",at=@At("HEAD"),cancellable=true)
    private void ringshiftMemberName(CallbackInfoReturnable<Component> result) {
        var game=VanillaSmash.instance();var player=(ServerPlayer)(Object)this;
        if(game!=null&&game.points.member(player.getUUID()))result.setReturnValue(Component.literal("[PLUS] ").withStyle(ChatFormatting.GREEN).append(Component.literal(player.getGameProfile().name()).withStyle(ChatFormatting.WHITE)));
    }
}
