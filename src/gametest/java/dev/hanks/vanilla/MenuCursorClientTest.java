package dev.hanks.vanilla;

import java.util.*;
import dev.hanks.network.Wire;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/** Real cursor coordinates, across redraws and navigation; never restores the mouse after an action. */
@SuppressWarnings("UnstableApiUsage")
public final class MenuCursorClientTest {
    private static VanillaSmash game(){return VanillaSmash.instance();}
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static void stable(ClientGameTestContext c,double[] point,Runnable action,String label){
        c.getInput().setCursorPos(point[0],point[1]);action.run();c.waitTicks(12);
        c.runOnClient(mc->{
            check(mc.gui.screen()!=null && !mc.mouseHandler.isMouseGrabbed(),label+" keeps a menu open");
            check(Math.abs(mc.mouseHandler.xpos()-point[0])<1 && Math.abs(mc.mouseHandler.ypos()-point[1])<1,
                    label+" preserves cursor "+Arrays.toString(point)+", got ["+mc.mouseHandler.xpos()+", "+mc.mouseHandler.ypos()+"]");
        });
    }
    private static void picker(ClientGameTestContext c,int x,int y,String label){
        c.waitFor(mc->mc.gui.screen()!=null && mc.gui.screen().getTitle().getString().equals("Choose fighter"),200);
        var point=c.computeOnClient(mc->{var w=WidePickerClientTest.body(mc.gui.screen());double scale=mc.getWindow().getGuiScale();
            return new double[]{(w.getX()+4+x)*scale,(w.getY()+4+y)*scale};});
        stable(c,point,()->c.getInput().pressMouse(0),label);
    }
    private static void button(ClientGameTestContext c,String label){
        c.waitFor(mc->mc.gui.screen()!=null && MatchmakingClientTest.findButton(mc.gui.screen(),label)!=null,200);
        var point=c.computeOnClient(mc->{var b=MatchmakingClientTest.findButton(mc.gui.screen(),label);double scale=mc.getWindow().getGuiScale();
            return new double[]{(b.getX()+10)*scale,(b.getY()+6)*scale};});
        stable(c,point,()->c.getInput().pressMouse(0),label);
    }
    private static void escape(ClientGameTestContext c){
        // Stock DialogScreen.onClose forces CLOSE regardless of after_action; Escape is distinct from a button click.
        c.getInput().pressKey(InputConstants.KEY_ESCAPE);c.waitTicks(12);
        c.runOnClient(mc->check(mc.gui.screen()!=null && mc.gui.screen().getTitle().getString().equals("Choose fighter"),"Escape still returns to picker"));
    }
    public static void run(ClientGameTestContext c){
        var props=new Properties();props.setProperty("online-mode","false");props.setProperty("server-ip","127.0.0.1");
        props.setProperty("view-distance","6");props.setProperty("allow-flight","true");
        try(var server=c.worldBuilder().createServer(props);var connection=server.connect()){
            c.getInput().resizeWindow(1280,720);c.runOnClient(mc->{mc.options.fov().set(70);mc.options.guiScale().set(2);mc.resizeGui();});
            MatchmakingClientTest.click(c,"Proceed");server.waitFor(s->game().uiPack.ready(connection.getServerPlayer()),1200);
            connection.waitForChunksRender();c.waitTicks(30);
            c.runOnClient(mc->mc.player.connection.sendCommand("smash join"));c.waitTicks(60);
            picker(c,173,27,"Choose Alex");picker(c,202,139,"4 Player");picker(c,126,139,"1v1");
            picker(c,20,162,"Open Store");button(c,"Close");
            picker(c,20,162,"Reopen Store");escape(c);
            picker(c,40,49,"Invite");PartyMenuClientTest.click(c,260,56);PartyMenuClientTest.submit(c,"Cancel");PartyMenuClientTest.submit(c,"Back to fighters");
            picker(c,202,162,"Ready / queue");
            server.runOnServer(s->check(game().network.selected(connection.getServerPlayer().getUUID()),"Queued after ready"));
            stable(c,new double[]{211,307},()->c.waitTicks(40),"Queue animation");
            picker(c,202,162,"Cancel queue");
            picker(c,115,27,"Choose Steve");picker(c,205,117,"Next skin");picker(c,97,117,"Previous skin");picker(c,205,117,"Locked skin");
            var id=server.computeOnServer(s->connection.getServerPlayer().getUUID());
            server.runOnServer(s->{
                for(int i=0;i<10;i++){
                    var pair=List.of(id,UUID.randomUUID());
                    game().points.settle(new Wire.MatchResult(UUID.randomUUID(),"DUEL",id,
                            pair.stream().map(p->new Wire.Ticket(p,"STEVE","DUEL",UUID.randomUUID())).toList(),
                            pair.stream().map(p->new Wire.ResultRow(p,"Fixture","STEVE",pair.indexOf(p)+1,1,0,0,0)).toList())).join();
                }
            });c.waitTicks(12);
            picker(c,265,117,"Open purchase confirmation");button(c,"Cancel");
            picker(c,265,117,"Reopen purchase confirmation");button(c,"Unlock");
            server.waitFor(s->game().points.wardrobe(id).equipped("STEVE").equals("diamond"),100);
            server.runOnServer(s->check(game().points.account(id).balance()==0,"Purchase charged once"));
            c.getInput().pressKey(InputConstants.KEY_ESCAPE);c.waitTicks(12);
            server.runOnServer(s->check(!game().stage.active(connection.getServerPlayer()),"Leaving the picker still exits to gameplay"));
            c.runOnClient(mc->check(mc.gui.screen()==null,"Exit does not strand a menu"));
        }
        VanillaSmash.LOG.info("MENU_CURSOR_NATIVE_CLIENT_TEST_PASSED");
    }
}
