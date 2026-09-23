package dev.hanks.vanilla;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Authored vanilla-block stages. All ornament above the deck stays behind the fighting plane. */
final class AlternateArenaBuilder {
    private static final BlockPos MARKER = new BlockPos(1,40,0);
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_SKIP_ALL_SIDEEFFECTS;
    private final ServerLevel level;
    private final BattleStage stage;
    private final Map<String,BlockState> states = new HashMap<>();
    private AlternateArenaBuilder(ServerLevel level, BattleStage stage) { this.level=level; this.stage=stage; }
    static void ensureBuilt(ServerLevel level, BattleStage stage) {
        if (MvpWorlds.stage(level)!=stage || stage==BattleStage.SKYBOUND_GROVE) throw new IllegalArgumentException("Wrong stage dimension");
        if (level.getBlockState(MARKER).is(Blocks.REINFORCED_DEEPSLATE)) return;
        new AlternateArenaBuilder(level,stage).build();
    }
    private void build() {
        long started=System.nanoTime();
        fill(-44,55,-50,44,130,5,Blocks.AIR);
        if(stage==BattleStage.EMBERFORGE) forge(); else temple();
        for(var p:stage.platforms) platform(p);
        // Front corners are open so ledge hangers remain visible from the side camera.
        fill(stage.left,77,1,stage.left+1,80,3,Blocks.AIR);
        fill(stage.right-1,77,1,stage.right,80,3,Blocks.AIR);
        put(1,40,0,Blocks.REINFORCED_DEEPSLATE);
        VanillaSmash.LOG.info("Built {} arena revision 1 in {} ms",stage.label,(System.nanoTime()-started)/1_000_000);
    }
    private void forge() {
        // A suspended blast-furnace deck: brass seams, riveted steel, and a glowing exposed core.
        for(int y=70;y<=79;y++) {
            int half=Math.min(12,4+(y-70));
            for(int x=-half;x<=half;x++) for(int z=-3;z<=3;z++)
                put(x,y,z,Math.floorMod(x*13+y*7+z,11)<2?Blocks.POLISHED_BASALT:Blocks.DEEPSLATE_BRICKS);
        }
        fill(-12,80,-2,12,80,3,Blocks.POLISHED_DEEPSLATE);
        for(int x=-12;x<=12;x++) {
            if(Math.floorMod(x,4)==0) fill(x,80,-2,x,80,3,state("waxed_cut_copper"));
            put(x,79,3,Math.abs(x)%4==0?Blocks.IRON_BLOCK:Blocks.CHISELED_POLISHED_BLACKSTONE);
        }
        fill(-7,73,3,7,77,3,Blocks.POLISHED_BLACKSTONE_BRICKS);
        fill(-6,74,3,6,76,3,Blocks.MAGMA_BLOCK);
        fill(-5,74,2,5,76,2,Blocks.SHROOMLIGHT);
        for(int x=-6;x<=6;x+=3) fill(x,73,4,x,77,4,Blocks.POLISHED_BASALT);
        for(int x=-5;x<=5;x++) if(x%3!=0) put(x,75,3,state("orange_stained_glass"));
        fill(-8,72,3,8,72,3,state("waxed_cut_copper"));
        fill(-8,78,3,8,78,3,state("waxed_cut_copper"));
        for(int x:new int[]{-10,10}) {
            fill(x,73,2,x,79,3,state("waxed_cut_copper"));
            put(x,75,4,state("waxed_lightning_rod"));
            put(x,78,4,state("waxed_chiseled_copper"));
        }
        for(int x:new int[]{-9,9}) {
            line(x,72,-3,x*2,65,-9,Blocks.POLISHED_BASALT);
            fill(x,81,-4,x,82,-4,Blocks.POLISHED_BLACKSTONE_BRICKS);
            put(x,83,-4,state("lantern"));
        }
        // Two tall furnace houses frame the action, linked by an overhead copper gantry.
        forgeTower(-23,70,-21); forgeTower(23,70,-21);
        fill(-23,102,-23,23,102,-21,state("waxed_oxidized_cut_copper"));
        fill(-23,103,-22,23,103,-22,state("waxed_cut_copper"));
        for(int x=-21;x<=21;x+=7) {
            line(x,101,-21,x+3,98,-21,Blocks.POLISHED_BLACKSTONE_BRICKS);
            fill(x,94,-21,x,101,-21,state("iron_chain[axis=y]"));
            put(x,93,-21,Blocks.SHROOMLIGHT);
            put(x,92,-21,state("lantern[hanging=true]"));
        }
        // Great flywheel with an incandescent axle, built from individual blocks rather than a flat wall.
        for(int x=-9;x<=9;x++) for(int y=-9;y<=9;y++) {
            double r=Math.hypot(x,y);
            if(r>=7.5&&r<=9) put(x,88+y,-31,Math.floorMod(x+y,5)==0?state("waxed_oxidized_copper"):state("waxed_copper_block"));
        }
        for(int i=0;i<8;i++) {
            double a=i*Math.PI/4;
            line(0,88,-31,(int)Math.round(Math.cos(a)*8),88+(int)Math.round(Math.sin(a)*8),-31,state("waxed_cut_copper"));
        }
        fill(-1,87,-30,1,89,-30,Blocks.SHROOMLIGHT);
        put(0,88,-29,state("waxed_chiseled_copper"));
        for(int x:new int[]{-34,34}) {
            fill(x-2,66,-37,x+2,89,-33,Blocks.BASALT);
            fill(x-1,90,-36,x+1,105,-34,Blocks.NETHER_BRICKS);
            fill(x-2,105,-37,x+2,106,-33,Blocks.POLISHED_BLACKSTONE_BRICKS);
            for(int y=71;y<90;y+=5) put(x,y,-32,Blocks.SHROOMLIGHT);
        }
    }
    private void forgeTower(int x,int y,int z) {
        fill(x-4,y,z-3,x+4,y+28,z+3,Blocks.POLISHED_BLACKSTONE_BRICKS);
        for(int dx:new int[]{-4,4}) fill(x+dx,y,z+4,x+dx,y+27,z+4,Blocks.POLISHED_BASALT);
        for(int dy:new int[]{2,10,18,26}) fill(x-4,y+dy,z+4,x+4,y+dy,z+4,state("waxed_cut_copper"));
        for(int dy:new int[]{5,13,21}) {
            fill(x-2,y+dy,z+3,x+2,y+dy+2,z+3,Blocks.SHROOMLIGHT);
            fill(x-2,y+dy,z+4,x+2,y+dy+2,z+4,state("orange_stained_glass"));
            fill(x,y+dy,z+4,x,y+dy+2,z+4,Blocks.POLISHED_BLACKSTONE_BRICKS);
        }
        for(int tier=0;tier<4;tier++) fill(x-5+tier,y+29+tier,z-4+tier,x+5-tier,y+29+tier,z+4-tier,state("waxed_oxidized_cut_copper"));
        put(x,y+33,z,state("waxed_lightning_rod"));
    }
    private void temple() {
        // A pale, fractured sanctuary with turquoise inlay and hanging crystal foundations.
        for(int y=71;y<=79;y++) {
            int half=Math.min(16,4+(y-71)*2);
            for(int x=-half;x<=half;x++) for(int z=-3;z<=3;z++)
                put(x,y,z,Math.floorMod(x*7+y*3+z,13)<3?Blocks.CALCITE:Blocks.SMOOTH_SANDSTONE);
        }
        fill(-16,80,-2,16,80,3,Blocks.SMOOTH_QUARTZ);
        for(int x=-16;x<=16;x++) {
            put(x,80,2,Blocks.DARK_PRISMARINE);
            if(Math.floorMod(x,4)==0) fill(x,80,-2,x,80,1,Blocks.CHISELED_QUARTZ_BLOCK);
            put(x,79,3,Math.floorMod(x,4)==0?Blocks.GOLD_BLOCK:Blocks.CUT_SANDSTONE);
        }
        // Recessed colonnade beneath the play surface; the solid core remains behind its openings.
        for(int center:new int[]{-10,0,10}) {
            fill(center-3,74,3,center+3,78,3,Blocks.AIR);
            fill(center-2,74,2,center+2,77,2,Blocks.DARK_PRISMARINE);
            for(int dx:new int[]{-3,3}) fill(center+dx,73,3,center+dx,78,3,Blocks.QUARTZ_PILLAR);
            fill(center-2,78,3,center+2,78,3,Blocks.CHISELED_SANDSTONE);
            put(center-2,77,3,Blocks.SMOOTH_SANDSTONE); put(center+2,77,3,Blocks.SMOOTH_SANDSTONE);
            put(center,76,3,Blocks.SEA_LANTERN);
            put(center,75,3,state("soul_lantern[hanging=true]"));
        }
        for(int x:new int[]{-12,-6,0,6,12}) {
            int base=72+Math.abs(x)/3;
            line(x,base,1,x,base-5,1,Blocks.AMETHYST_BLOCK);
            put(x,base-2,2,Blocks.SEA_LANTERN);
            put(x,base-6,1,state("pointed_dripstone[vertical_direction=down,thickness=tip]"));
        }
        for(int x:new int[]{-12,12}) {
            fill(x-1,80,-4,x+1,80,-3,Blocks.MOSS_BLOCK);
            put(x,81,-4,Blocks.FLOWERING_AZALEA);
        }
        // Broken gates on separate floating isles: open arches keep the fighter silhouettes readable.
        templeIsle(-25,79,-23,8); templeIsle(25,81,-25,8);
        gate(-25,80,-23,12); gate(25,82,-25,10);
        for(int x:new int[]{-30,30}) { templeIsle(x,82,-29,5); cherry(x,83,-29); }
        // Distant beacon shrine above the main archway, with a stepped turquoise roof.
        templeIsle(0,105,-43,8);
        for(int x:new int[]{-5,5}) fill(x,106,-43,x,118,-41,Blocks.QUARTZ_PILLAR);
        fill(-6,119,-44,6,119,-40,Blocks.SMOOTH_SANDSTONE);
        for(int tier=0;tier<4;tier++) fill(-7+tier,120+tier,-45+tier,7-tier,120+tier,-39-tier,Blocks.DARK_PRISMARINE);
        fill(-1,106,-42,1,110,-40,Blocks.CHISELED_QUARTZ_BLOCK);
        put(0,111,-41,Blocks.SEA_LANTERN);
        for(int[] p:new int[][]{{-13,98,-36,4},{14,94,-34,4},{-37,69,-36,5},{35,103,-42,4}}) templeIsle(p[0],p[1],p[2],p[3]);
    }
    private void gate(int x,int y,int z,int height) {
        for(int dx:new int[]{-5,5}) {
            fill(x+dx-1,y,z-1,x+dx+1,y+2,z+1,Blocks.CHISELED_SANDSTONE);
            fill(x+dx,y+3,z,x+dx,y+height,z,Blocks.QUARTZ_PILLAR);
            fill(x+dx-1,y+height,z-1,x+dx+1,y+height+1,z+1,Blocks.DARK_PRISMARINE);
            put(x+dx,y+height+2,z,Blocks.GOLD_BLOCK);
        }
        fill(x-4,y+height-1,z,x+4,y+height,z,Blocks.SMOOTH_SANDSTONE);
        fill(x-3,y+height+1,z,x+2,y+height+1,z,Blocks.DARK_PRISMARINE);
        put(x+4,y+height+3,z,Blocks.SMOOTH_SANDSTONE); // Suspended broken keystone.
        for(int dx:new int[]{-4,4}) {
            fill(x+dx,y+height-4,z,x+dx,y+height-2,z,state("iron_chain[axis=y]"));
            put(x+dx,y+height-5,z,state("soul_lantern[hanging=true]"));
        }
    }
    private void templeIsle(int x,int top,int z,int radius) {
        for(int dy=-6;dy<=0;dy++) {
            int r=Math.max(1,radius+dy);
            for(int dx=-r;dx<=r;dx++) for(int dz=-r/2;dz<=r/2;dz++)
                if(dx*dx+dz*dz*3<=r*r+1) put(x+dx,top+dy,z+dz,dy==0?Blocks.MOSS_BLOCK:dy%3==0?Blocks.CALCITE:Blocks.SMOOTH_SANDSTONE);
        }
    }
    private void cherry(int x,int y,int z) {
        fill(x,y,z,x,y+5,z,Blocks.CHERRY_LOG);
        line(x,y+4,z,x-3,y+7,z,Blocks.CHERRY_LOG);
        line(x,y+4,z,x+3,y+6,z,Blocks.CHERRY_LOG);
        for(int dx=-5;dx<=5;dx++) for(int dy=0;dy<=3;dy++) for(int dz=-3;dz<=3;dz++)
            if(dx*dx/25.0+(dy-1)*(dy-1)/5.0+dz*dz/9.0<1) put(x+dx,y+6+dy,z+dz,state("cherry_leaves[persistent=true]"));
    }
    private void platform(BattleStage.Platform p) {
        boolean forge=stage==BattleStage.EMBERFORGE;
        fill(p.left(),p.blockY(),-1,p.right(),p.blockY(),2,forge?Blocks.POLISHED_DEEPSLATE:Blocks.SMOOTH_QUARTZ);
        for(int x=p.left();x<=p.right();x++) {
            put(x,p.blockY(),2,forge?state("waxed_cut_copper"):Blocks.DARK_PRISMARINE.defaultBlockState());
            put(x,p.blockY()-1,-2,state(forge?"waxed_cut_copper_slab[type=top]":"smooth_sandstone_slab[type=top]"));
        }
        for(int x:new int[]{p.left(),p.right()}) {
            fill(x,p.blockY(),-1,x,p.blockY(),2,forge?state("waxed_chiseled_copper"):Blocks.CHISELED_SANDSTONE.defaultBlockState());
            put(x,p.blockY()-1,-2,state(forge?"lantern[hanging=true]":"soul_lantern[hanging=true]"));
        }
    }
    private BlockState state(String name) {
        return states.computeIfAbsent(name,key->{
            try{return BlockStateParser.parseForBlock(level.registryAccess().lookupOrThrow(Registries.BLOCK),"minecraft:"+key,false).blockState();}
            catch(com.mojang.brigadier.exceptions.CommandSyntaxException e){throw new IllegalArgumentException("Unknown stage block: "+key,e);}
        });
    }
    private void line(int x,int y,int z,int tx,int ty,int tz,Block block) { line(x,y,z,tx,ty,tz,block.defaultBlockState()); }
    private void line(int x,int y,int z,int tx,int ty,int tz,BlockState block) {
        int n=Math.max(Math.max(Math.abs(tx-x),Math.abs(ty-y)),Math.abs(tz-z));
        for(int i=0;i<=n;i++){double t=n==0?0:(double)i/n;put((int)Math.round(x+(tx-x)*t),(int)Math.round(y+(ty-y)*t),(int)Math.round(z+(tz-z)*t),block);}
    }
    private void fill(int x,int y,int z,int tx,int ty,int tz,Block block){fill(x,y,z,tx,ty,tz,block.defaultBlockState());}
    private void fill(int x,int y,int z,int tx,int ty,int tz,BlockState state){
        var p=new BlockPos.MutableBlockPos();
        for(int a=x;a<=tx;a++)for(int b=y;b<=ty;b++)for(int c=z;c<=tz;c++)level.setBlock(p.set(a,b,c),state,FLAGS);
    }
    private void put(int x,int y,int z,Block block){put(x,y,z,block.defaultBlockState());}
    private void put(int x,int y,int z,BlockState state){level.setBlock(new BlockPos(x,y,z),state,FLAGS);}
}
