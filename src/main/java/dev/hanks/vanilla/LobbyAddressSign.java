package dev.hanks.vanilla;

import com.mojang.math.Transformation;
import java.util.*;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** The shared courtyard address, made entirely from native, non-interactive displays. */
public final class LobbyAddressSign {
    public static final String ADDRESS="mc.brawl.party";
    // The authored pink circle is centered on block (0,100,-62).
    public static final Vec3 POSITION=new Vec3(.5,106.1,-61.5);
    private final VanillaSmash game;
    private final List<Display.TextDisplay> entities=new ArrayList<>();
    public LobbyAddressSign(VanillaSmash game){this.game=game;}
    public boolean owns(Entity entity){return entities.contains(entity);}
    public boolean active(){return !entities.isEmpty();}

    public void tick(){
        if(game.ticks%20!=0||game.network.arena())return;
        var level=game.server.getLevel(MvpWorlds.LOBBY);
        if(level==null||level.players().stream().noneMatch(p->!p.isRemoved()&&p.position().distanceToSqr(POSITION)<64*64)){
            close();return;
        }
        if(entities.isEmpty()||entities.stream().anyMatch(Entity::isRemoved)){close();spawn(level);}
        // Two gentle gold flecks per second, alternating around the outside of the lettering.
        // Ordinary particle distance/settings apply; no global or forced particle broadcast.
        int phase=(game.ticks/20)%4;
        double[][] spots={{-4.15,.85},{3.95,.20},{-3.55,-.60},{3.35,-.85}};
        for(int i=0;i<2;i++){
            var spot=spots[(phase+i*2)%spots.length];
            level.sendParticles(new DustParticleOptions(0xffdf8c,.9f),false,false,
                    POSITION.x+spot[0],POSITION.y+spot[1],POSITION.z-.08,1,.06,.10,.02,0);
        }
    }
    private void spawn(ServerLevel level){
        var address=Component.empty().append(Component.literal("mc.").withColor(UiTheme.CREAM))
                .append(Component.literal("brawl.party").withColor(UiTheme.CORAL))
                .withStyle(s->s.withBold(false));
        text(level,address,0,0,5.4f);
        text(level,Component.literal("1v1  •  4 Player  •  Parties").withColor(UiTheme.CREAM),0,-.48,2.2f);
        accent(level,0,7f,.28f);
        accent(level,-.87,.40f,.28f);
        accent(level,.87,.40f,.28f);
    }
    private void accent(ServerLevel level,double x,float width,float height){
        var display=text(level,Component.literal(" "),x,-.77,1);
        display.setFlags((byte)0);display.setBackgroundColor(0xff98dfb9);
        display.setTransformation(new Transformation(null,null,new Vector3f(width,height,1),null));
    }
    private Display.TextDisplay text(ServerLevel level,Component text,double x,double y,float scale){
        var display=new Display.TextDisplay(EntityTypes.TEXT_DISPLAY,level);
        display.setText(text);display.setLineWidth(500);display.setBackgroundColor(0);
        display.setTextOpacity((byte)255);display.setFlags(Display.TextDisplay.FLAG_SHADOW);
        display.setBrightnessOverride(new Brightness(15,15));
        display.setBillboardConstraints(Display.BillboardConstraints.CENTER);display.setViewRange(1);
        display.setTransformation(new Transformation(null,null,new Vector3f(scale),null));
        display.setPos(POSITION.x+x,POSITION.y+y,POSITION.z);
        display.addTag(VanillaSmash.TEMP);entities.add(display);level.addFreshEntity(display);
        return display;
    }
    public void close(){entities.forEach(Entity::discard);entities.clear();}
}
