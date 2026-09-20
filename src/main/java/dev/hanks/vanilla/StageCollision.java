package dev.hanks.vanilla;

import java.util.ArrayList;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Swept solid-island collision. Upper platforms retain their separate one-way landing rules. */
public final class StageCollision {
    public record Step(double x, double y, boolean wall, boolean ceiling, boolean landed) {}
    private StageCollision() {}
    public static Step move(AABB body, double dx, double dy, Iterable<VoxelShape> world) {
        var solids = new ArrayList<VoxelShape>();
        for (var shape : world) if (!shape.isEmpty() && shape.max(Direction.Axis.Y) <= ArenaRules.DECK_Y + 1e-7) solids.add(shape);
        double y = Shapes.collide(Direction.Axis.Y, body, solids, dy);
        double x = Shapes.collide(Direction.Axis.X, body.move(0, y, 0), solids, dx);
        return new Step(x, y, Math.abs(x-dx)>1e-7, dy>0 && y<dy-1e-7, dy<0 && y>dy+1e-7);
    }
}
