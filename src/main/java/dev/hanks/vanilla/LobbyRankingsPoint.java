package dev.hanks.vanilla;

import com.mojang.math.Transformation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** The rear-right courtyard destination, opposite Party. */
public final class LobbyRankingsPoint extends LobbyLandmark {
    public static final BlockPos PODIUM=new BlockPos(-9,101,-66);
    public static final Vec3 POSITION=new Vec3(-8.5,102,-65.5);
    public LobbyRankingsPoint(VanillaSmash game){super(game,PODIUM);}
    public static void buildPodium(ServerLevel level){buildPodium(level,PODIUM,Blocks.GOLD_BLOCK);}
    @Override protected LivingEntity createFighter(ServerLevel level){return FighterModels.create(level,FighterClass.IRON_GOLEM);}
    @Override protected String title(){return "RANKINGS";}
    @Override protected String subtitle(){return "This week's champions";}
    @Override protected int color(){return 0xefc863;}
    @Override protected float labelHeight(){return 4.3f;}
    @Override protected void activate(ServerPlayer p){game.rankings.show(p);}
    @Override protected void addCompanions(ServerLevel level,LivingEntity fighter){
        // A small block-built cup: quartz plinth, gold foot/stem, hollow bowl and two handles.
        cube(level,-9.9,101.5,-65.5,.65,.35,.65,Blocks.CHISELED_QUARTZ_BLOCK);
        cube(level,-9.9,101.85,-65.5,.48,.12,.48,Blocks.GOLD_BLOCK);
        cube(level,-9.9,101.97,-65.5,.15,.29,.15,Blocks.GOLD_BLOCK);
        cube(level,-9.9,102.26,-65.5,.40,.12,.40,Blocks.GOLD_BLOCK);
        cube(level,-9.9,102.38,-65.72,.54,.30,.10,Blocks.GOLD_BLOCK);
        cube(level,-9.9,102.38,-65.28,.54,.30,.10,Blocks.GOLD_BLOCK);
        for(double side:new double[]{-1,1}){
            cube(level,-9.9+side*.22,102.38,-65.5,.10,.30,.34,Blocks.GOLD_BLOCK);
            cube(level,-9.9+side*.37,102.40,-65.5,.20,.08,.12,Blocks.GOLD_BLOCK);
            cube(level,-9.9+side*.37,102.62,-65.5,.20,.08,.12,Blocks.GOLD_BLOCK);
            cube(level,-9.9+side*.44,102.40,-65.5,.08,.30,.12,Blocks.GOLD_BLOCK);
        }
    }
    private void cube(ServerLevel level,double x,double y,double z,double w,double h,double d,Block block){
        var entity=new Display.BlockDisplay(EntityTypes.BLOCK_DISPLAY,level);
        entity.setBlockState(block.defaultBlockState());entity.setPos(x-w/2,y,z-d/2);
        entity.setTransformation(new Transformation(null,null,new Vector3f((float)w,(float)h,(float)d),null));add(entity);
    }
}
