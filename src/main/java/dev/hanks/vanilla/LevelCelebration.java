package dev.hanks.vanilla;

import com.mojang.math.Transformation;
import dev.hanks.network.*;
import java.util.*;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.sounds.*;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.block.Blocks;
import org.joml.Vector3f;

/** Private in-world reward card. Metadata animates without reopening the player's menu. */
final class LevelCelebration {
    private final VanillaSmash game;
    private final WinnerStage.Session session;
    private final boolean replay;
    private final Runnable acknowledge;
    private final List<Entity> entities=new ArrayList<>();
    private Display.TextDisplay heading,total,detail,breakdown;
    private Display.BlockDisplay bar,accent;
    private Levels.Receipt receipt;
    private XpAnimation animation;
    private int started=-1,lastSound=-100;
    private long lastShown=-1;
    private boolean finished,wasPulse;
    LevelCelebration(VanillaSmash game,WinnerStage.Session session,boolean replay,Runnable acknowledge){
        this.game=game;this.session=session;this.replay=replay;this.acknowledge=acknowledge;
    }
    private <T extends Entity>T add(T entity){
        entity.addTag(VanillaSmash.TEMP);entities.add(entity);session.entities.add(entity);entity.level().addFreshEntity(entity);return entity;
    }
    private Display.TextDisplay text(double y,float scale){
        var d=new Display.TextDisplay(EntityTypes.TEXT_DISPLAY,session.player.level());
        d.setTextOpacity((byte)255);d.setBackgroundColor(0);d.setLineWidth(400);
        d.setFlags(Display.TextDisplay.FLAG_SHADOW);d.setBrightnessOverride(new Brightness(15,15));d.setViewRange(3);
        d.setTransformation(new Transformation(null,null,new Vector3f(scale),null));d.setPos(session.origin()+4,y,4.58);return add(d);
    }
    private Display.BlockDisplay rectangle(double x,double y,double width,double height,net.minecraft.world.level.block.Block block,double z){
        var d=new Display.BlockDisplay(EntityTypes.BLOCK_DISPLAY,session.player.level());
        d.setBlockState(block.defaultBlockState());d.setBrightnessOverride(new Brightness(15,15));d.setViewRange(3);
        d.setPos(session.origin()+x,y,z);d.setTransformation(new Transformation(null,null,new Vector3f((float)width,(float)height,.02f),null));return add(d);
    }
    private void build(){
        if(game.uiPack.ready(session.player)){
            var panel=text(102.145,1);panel.setPos(session.origin()+4,102.145,4.54);
            panel.setFlags((byte)0);panel.setText(UiPack.strip("levels_reward"));
        }else{
            rectangle(1.45,99.92,5.1,2.45,Blocks.CONCRETE.black(),4.5);
            rectangle(1.50,99.97,5,2.35,Blocks.CONCRETE.gray(),4.52);
        }
        accent=rectangle(1.50,102.27,5,.05,Blocks.CONCRETE.lime(),4.55);
        heading=text(101.80,1.65f);total=text(101.41,1.25f);detail=text(100.69,.90f);breakdown=text(100.16,.82f);
        rectangle(1.80,101.13,4.4,.13,Blocks.CONCRETE.black(),4.55);
        bar=rectangle(1.80,101.13,.001,.13,Blocks.CONCRETE.lime(),4.58);bar.setTransformationInterpolationDuration(2);
        heading.setText(Component.literal("YOUR LEVEL").withColor(UiTheme.CREAM));
        total.setText(Component.literal("Saving XP...").withColor(UiTheme.MINT));
    }
    void tick(){
        if(!session.ready())return;
        if(started<0){started=game.ticks;build();}
        if(animation==null){
            receipt=game.points.levelReceipt(session.result.id(),session.player.getUUID());
            if(receipt==null)return;
            animation=new XpAnimation(receipt.before(),receipt.after(),replay);
            acknowledge.run();
            if(receipt.total()==0)breakdown.setText(Component.literal(LevelRules.exclusion(session.result,session.player.getUUID())).withColor(UiTheme.MUTED));
            else breakdown.setText(Component.literal("Match +"+receipt.finish()+ (receipt.win()>0?"   Win +"+receipt.win():"")
                    +(receipt.knockouts()>0?"   KOs +"+receipt.knockouts():"")).withColor(UiTheme.MINT));
        }
        if(finished)return;
        animation.tick();long shown=animation.shown();var v=LevelRules.progress(shown);
        boolean pop=animation.pulse()>0;
        heading.setText(Component.literal((pop?"LEVEL UP!  ":"LEVEL ")+v.level()).withColor(pop?0xffd66b:v.tier().color).withStyle(s->s.withBold(true)));
        heading.setTransformation(new Transformation(null,null,new Vector3f(pop?1.8f:1.65f),null));
        total.setText(Component.literal("+"+(shown-receipt.before())+" XP").withColor(UiTheme.CREAM));
        detail.setText(Component.literal(pop?v.tier().label+"  ·  Level "+v.level()+" reached":v.into()+" / "+v.required()+" XP  ·  "+v.tier().label).withColor(pop?0xffd66b:UiTheme.MINT));
        bar.setBlockState((pop?Blocks.CONCRETE.yellow():Blocks.CONCRETE.lime()).defaultBlockState());
        // Hold the completed bar gold, then start the next level's bar empty instead of shrinking it backwards.
        bar.setTransformationInterpolationDuration(wasPulse&&!pop?0:2);wasPulse=pop;
        bar.setTransformation(new Transformation(null,null,new Vector3f(Math.max(.001f,4.4f*(pop?1:v.fraction())),.13f,.02f),null));bar.setTransformationInterpolationDelay(0);
        accent.setBlockState((pop?Blocks.CONCRETE.yellow():Blocks.CONCRETE.lime()).defaultBlockState());
        if(animation.leveled()){
            sound(SoundEvents.PLAYER_LEVELUP,.60f,1.05f);
            sound(SoundEvents.AMETHYST_BLOCK_CHIME,.55f,1.35f);
            session.player.level().sendParticles(session.player,ParticleTypes.FIREWORK,true,false,session.origin()+4,102.5,4.6,18,2,.45,.08,.025);
            session.player.level().sendParticles(session.player,ParticleTypes.HAPPY_VILLAGER,true,false,session.origin()+4,103,1,12,1,1,.5,.02);
            if(v.tier()!=LevelRules.progress(shown-1).tier()){
                sound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,.5f,1.1f);
                breakdown.setText(Component.literal(v.tier().label+" unlocked!").withColor(0xffd66b));
            }
        }else if(!replay&&shown>lastShown&&lastShown>=0&&game.ticks-lastSound>=5){
            sound(SoundEvents.EXPERIENCE_ORB_PICKUP,.18f,.75f+1.05f*(shown-receipt.before())/Math.max(1,receipt.total()));lastSound=game.ticks;
        }
        lastShown=shown;
        if(animation.done()){
            finished=true;
            if(!replay&&receipt.total()>0)sound(SoundEvents.NOTE_BLOCK_PLING.value(),.3f,1.5f);
        }
    }
    private void sound(SoundEvent sound,float volume,float pitch){
        session.player.connection.send(new ClientboundSoundPacket(Holder.direct(sound),SoundSource.MASTER,session.camera.getX(),session.camera.getY(),session.camera.getZ(),volume,pitch,game.ticks));
    }
    boolean finished(){return finished;}
    long shown(){return animation==null?-1:animation.shown();}
    List<Entity> entities(){return List.copyOf(entities);}
}
