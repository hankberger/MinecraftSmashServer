package dev.hanks.vanilla;

import java.util.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.*;
import com.mojang.blaze3d.platform.InputConstants;

/** Stock dialog sizing and real mouse input; no custom screen or input hooks. */
@SuppressWarnings("UnstableApiUsage")
public final class WidePickerClientTest {
    private static VanillaSmash game(){return VanillaSmash.instance();}
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static AbstractWidget body(GuiEventListener node){
        if(node instanceof AbstractWidget w && w.getClass().getSimpleName().equals("FocusableTextWidget"))return w;
        if(node instanceof ContainerEventHandler c)for(var child:c.children()){var found=body(child);if(found!=null)return found;}
        return null;
    }
    static void click(ClientGameTestContext c,int x,int y){
        c.waitFor(mc->mc.gui.screen()!=null && body(mc.gui.screen())!=null,200);
        var point=c.computeOnClient(mc->{var w=body(mc.gui.screen());double scale=mc.getWindow().getGuiScale();
            return new double[]{(w.getX()+4+x)*scale,(w.getY()+4+y)*scale};});
        c.getInput().setCursorPos(point[0],point[1]);c.getInput().pressMouse(0);c.waitTicks(8);
    }
    static void fighter(ClientGameTestContext c,int i){click(c,88+58*(i%4)+27,54*(i/4)+27);}
    static void action(ClientGameTestContext c,int action){
        if(action>=0 && action<8)fighter(c,action);
        else if(action>=20 && action<=22)click(c,126+(action-20)*76,139);
        else if(action==30)MatchmakingClientTest.click(c,"Back to lobby");
        else if(action==31)click(c,202,162);
        else if(action==32)click(c,60,162);
        else if(action==35)click(c,97,117);
        else if(action==36)click(c,205,117);
        else if(action==37)click(c,265,117);
        else if(action==38)click(c,20,162);
        else if(action>=40 && action<=43)click(c,40,22+(action-40)*27);
        else throw new IllegalArgumentException("Unknown picker action "+action);
    }
    public static void run(ClientGameTestContext c){
        var props=new Properties();props.setProperty("online-mode","false");props.setProperty("server-ip","127.0.0.1");
        props.setProperty("view-distance","6");props.setProperty("allow-flight","true");
        try(var server=c.worldBuilder().createServer(props);var connection=server.connect()){
            c.getInput().resizeWindow(1280,720);c.runOnClient(mc->{mc.options.fov().set(70);mc.options.guiScale().set(2);mc.resizeGui();});
            MatchmakingClientTest.click(c,"Proceed");server.waitFor(s->game().uiPack.ready(connection.getServerPlayer()),1200);
            connection.waitForChunksRender();c.waitTicks(30);
            c.runOnClient(mc->mc.player.connection.sendCommand("smash join"));c.waitTicks(60);
            c.runOnClient(mc->{mc.gui.toastManager().clear();var w=body(mc.gui.screen());
                check(w!=null,"Ordinary /smash join defaults to the wider picker");
                check(w.getWidth()==324 && w.getHeight()==179,"Canvas has nineteen rows without text wrapping: "+w.getWidth()+"x"+w.getHeight());
            });
            c.takeScreenshot("wide-01-desktop");
            for(int i=0;i<8;i++){fighter(c,i);var kind=FighterClass.values()[i];server.runOnServer(s->check(game().stage.session(connection.getServerPlayer().getUUID()).selected==kind,"Mouse selects "+kind));}
            for(int[] corner:new int[][]{{2,2},{51,2},{2,51},{51,51}}){
                fighter(c,0);click(c,146+corner[0],corner[1]);
                server.runOnServer(s->check(game().stage.session(connection.getServerPlayer().getUUID()).selected==FighterClass.ALEX,"Portrait corner is clickable: "+Arrays.toString(corner)));
            }
            // Every mode must respond at the top, middle and new bottom of the taller button.
            String[] modes={"DUEL","MATCH","PRACTICE"};
            for(int i=0;i<3;i++)for(int y:new int[]{128,139,151}){
                action(c,20+(i+1)%3);click(c,126+i*76,y);String mode=modes[i];
                server.runOnServer(s->check(game().hub.parties.view(connection.getServerPlayer().getUUID()).mode().equals(mode),"Full-height mode target: "+mode+" at "+y));
            }
            action(c,20);
            click(c,40,162);MatchmakingClientTest.click(c,"Close");
            c.waitFor(mc->body(mc.gui.screen())!=null && body(mc.gui.screen()).getWidth()==324,200);
            server.runOnServer(s->check(game().stage.active(connection.getServerPlayer()),"Store returns to the same character stage"));
            fighter(c,0);
            c.getInput().resizeWindow(2560,1440);c.waitTicks(15);c.takeScreenshot("wide-02-qhd-scale2");
            fighter(c,1);
            c.runOnClient(mc->{mc.options.guiScale().set(0);mc.resizeGui();});c.waitTicks(15);c.takeScreenshot("wide-03-qhd-auto");
            fighter(c,2);server.runOnServer(s->check(game().stage.session(connection.getServerPlayer().getUUID()).selected==FighterClass.ZOMBIE,"Mouse targets follow GUI resize"));
            c.getInput().resizeWindow(960,720);c.runOnClient(mc->{mc.options.guiScale().set(3);mc.resizeGui();});c.waitTicks(15);c.takeScreenshot("wide-04-small-auto");
            click(c,202,162);c.waitTicks(30);
            server.runOnServer(s->check(game().network.selected(connection.getServerPlayer().getUUID()),"Play queues from the wide dialog"));
            c.takeScreenshot("wide-05-queue");
            var stage=new java.util.concurrent.atomic.AtomicReference<CharacterStage.Session>();
            server.runOnServer(s->stage.set(game().stage.session(connection.getServerPlayer().getUUID())));
            c.runOnClient(mc->mc.player.connection.sendCommand("smash menu compact"));c.waitTicks(12);
            c.runOnClient(mc->check(mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>,"Compact layout is available as a fallback"));
            server.runOnServer(s->{
                check(game().network.selected(connection.getServerPlayer().getUUID()),"Switching layouts preserves the queue");
                check(game().stage.session(connection.getServerPlayer().getUUID())==stage.get(),"Switching layouts preserves the stage and selected fighter");
            });
            c.runOnClient(mc->mc.player.connection.sendCommand("smash menu wide"));c.waitTicks(12);
            click(c,202,162);
            server.runOnServer(s->check(!game().network.selected(connection.getServerPlayer().getUUID()),"Cancel remains clickable after queue animation"));
            c.getInput().pressKey(InputConstants.KEY_ESCAPE);c.waitTicks(10);
            server.runOnServer(s->check(!game().stage.active(connection.getServerPlayer()),"Escape exits the dialog and stage"));
            c.runOnClient(mc->mc.player.connection.sendCommand("smash join"));c.waitTicks(20);
            c.runOnClient(mc->check(body(mc.gui.screen())!=null,"The chosen layout persists when reopening Play"));
            c.getInput().pressKey(InputConstants.KEY_ESCAPE);c.waitTicks(10);
        }
        VanillaSmash.LOG.info("WIDE_PICKER_NATIVE_CLIENT_TEST_PASSED");
    }
}
