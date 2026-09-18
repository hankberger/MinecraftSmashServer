package dev.hanks.vanilla.mixin;

import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Sets vanilla teleport_duration metadata; stock clients perform the interpolation. */
@Mixin(Display.class)
public interface DisplayInterpolationMixin {
    @Invoker("setPosRotInterpolationDuration") void smashInterpolationDuration(int ticks);
}
