package dev.hanks.vanilla;

import java.util.Properties;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.*;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import dev.hanks.network.PointsStore;

/** Real resource-pack download, rendered Store cards and native external-link clicks. */
@SuppressWarnings("UnstableApiUsage")
public final class StoreScreenClientTest {
    private static VanillaSmash game() { return VanillaSmash.instance(); }
    private static AbstractWidget body(GuiEventListener node) {
        if(node instanceof AbstractWidget widget && widget.getClass().getSimpleName().equals("FocusableTextWidget"))return widget;
        if(node instanceof ContainerEventHandler container)for(var child:container.children()) {
            var found=body(child);if(found!=null)return found;
        }
        return null;
    }
    private static void cardClick(ClientGameTestContext c,boolean plus) {
        var point=c.computeOnClient(mc->{
            var widget=body(mc.gui.screen());if(widget==null)throw new AssertionError("Store art has a native text target");
            double scale=mc.getWindow().getGuiScale();
            return new double[]{(widget.getX()+4+(plus?220:63))*scale,(widget.getY()+4+121)*scale};
        });
        c.getInput().setCursorPos(point[0],point[1]);c.getInput().pressMouse(0);
        c.waitFor(mc->mc.gui.screen() instanceof ConfirmLinkScreen,100);
    }
    private static void checkCards(ClientGameTestContext c) {
        var visible=new java.util.concurrent.atomic.AtomicInteger(-1);
        c.runOnClient(mc->net.minecraft.client.Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(),image->{
            try(image) {
                int wallet=0,plus=0;
                for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++) {
                    int rgb=image.getPixel(x,y)&0xffffff;
                    if(rgb==0x1f4544)wallet++;
                    if(rgb==0x343a2d)plus++;
                }
                visible.set(Math.min(wallet,plus));
            }
        }));
        c.waitFor(mc->visible.get()>=0,100);
        if(visible.get()<2000)throw new AssertionError("Both Store cards must actually render, not missing font glyphs");
    }
    public static void run(ClientGameTestContext c) {
        var props=new Properties();props.setProperty("online-mode","false");props.setProperty("server-ip","127.0.0.1");
        props.setProperty("view-distance","6");props.setProperty("allow-flight","true");
        try(var server=c.worldBuilder().createServer(props);var connection=server.connect()) {
            c.getInput().resizeWindow(1280,720);
            c.runOnClient(mc->{mc.options.guiScale().set(2);mc.resizeGui();});
            MatchmakingClientTest.click(c,"Proceed");
            server.waitFor(s->game().uiPack.ready(connection.getServerPlayer()) && game().storePoint.fighter()!=null,1200);
            connection.waitForChunksRender();c.waitTicks(80);
            server.runOnServer(s->{
                var p=connection.getServerPlayer();
                game().points.deliver(new PointsStore.StoreDelivery("order:store-screen-test",p.getUUID(),2345,-1,"test")).join();
                p.teleportTo(-3.5,101,-74.5);
            });
            c.waitFor(mc->Math.abs(mc.player.getX()+3.5)<.1);c.waitTicks(10);
            c.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_2);c.getInput().lookAt(0,-15);
            c.getInput().pressMouse(1);
            c.waitFor(mc->mc.gui.screen()!=null && mc.gui.screen().getTitle().getString().equals("Store"),100);
            c.getInput().setCursorPos(20,400);c.waitTicks(3);checkCards(c);
            c.takeScreenshot("store-ui-01-desktop");
            cardClick(c,false);c.takeScreenshot("store-ui-02-credits-link");
            c.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE);c.waitTicks(5);
            cardClick(c,true);c.takeScreenshot("store-ui-03-plus-link");
            c.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE);c.waitTicks(5);
            MatchmakingClientTest.click(c,"Close");c.waitFor(mc->mc.gui.screen()==null);
            server.runOnServer(s->{
                var p=connection.getServerPlayer();
                game().points.deliver(new PointsStore.StoreDelivery("membership:store-screen-test",p.getUUID(),0,System.currentTimeMillis()+600_000,"test")).join();
            });
            c.getInput().resizeWindow(1920,1080);c.runOnClient(mc->{mc.options.guiScale().set(0);mc.resizeGui();});
            c.waitTicks(10);c.getInput().lookAt(0,-15);c.getInput().pressMouse(1);
            c.waitFor(mc->mc.gui.screen()!=null && mc.gui.screen().getTitle().getString().equals("Store"),100);
            c.getInput().setCursorPos(20,600);c.waitTicks(3);checkCards(c);
            c.takeScreenshot("store-ui-04-member-fullscreen");
            cardClick(c,true);c.takeScreenshot("store-ui-05-manage-link");
            c.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE);c.waitTicks(5);
            MatchmakingClientTest.click(c,"Close");c.waitFor(mc->mc.gui.screen()==null);
            server.runOnServer(s->{
                var p=connection.getServerPlayer();
                if(game().points.account(p.getUUID()).balance()!=2345 || !game().network.selections.tickets().isEmpty())
                    throw new AssertionError("Browsing preserves credits and queue state");
            });
        }
        VanillaSmash.LOG.info("STORE_SCREEN_NATIVE_CLIENT_TEST_PASSED");
    }
}
