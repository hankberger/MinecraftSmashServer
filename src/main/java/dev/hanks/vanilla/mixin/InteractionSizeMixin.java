package dev.hanks.vanilla.mixin;

import net.minecraft.world.entity.Interaction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Uses vanilla's synchronized interaction dimensions, also understood by stock clients. */
@Mixin(Interaction.class)
public interface InteractionSizeMixin {
    @Invoker("setWidth") void smashWidth(float width);
    @Invoker("setHeight") void smashHeight(float height);
}
