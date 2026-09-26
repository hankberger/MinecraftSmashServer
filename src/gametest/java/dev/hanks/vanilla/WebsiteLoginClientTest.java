package dev.hanks.vanilla;

import java.util.Properties;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.*;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;

@SuppressWarnings("UnstableApiUsage")
final class WebsiteLoginClientTest {
    private static AbstractWidget body(GuiEventListener node) {
        if(node instanceof AbstractWidget widget && widget.getClass().getSimpleName().equals("FocusableTextWidget"))return widget;
        if(node instanceof ContainerEventHandler container)for(var child:container.children()) { var found=body(child);if(found!=null)return found; }
        return null;
    }
    static void run(ClientGameTestContext c) {
        var props=new Properties();props.setProperty("online-mode","false");props.setProperty("server-ip","127.0.0.1");props.setProperty("view-distance","6");
        try(var server=c.worldBuilder().createServer(props);var connection=server.connect()) {
            connection.waitForChunksRender();c.getInput().resizeWindow(1280,720);
            c.runOnClient(mc->{mc.options.guiScale().set(2);mc.resizeGui();});c.waitTicks(40);
            c.runOnClient(mc->mc.player.connection.sendCommand("smash login"));
            c.waitFor(mc->mc.gui.screen()!=null && mc.gui.screen().getTitle().getString().equals("Website sign-in"));
            // An offline test server cannot issue real website credentials.
            c.takeScreenshot("website-login-01-offline-unavailable");
            MatchmakingClientTest.click(c,"Close");c.waitFor(mc->mc.gui.screen()==null);
            server.runOnServer(s->VanillaSmash.instance().websiteLogin.show(connection.getServerPlayer(),"AB6N96CQBLWB"));
            c.waitFor(mc->mc.gui.screen()!=null && mc.gui.screen().getTitle().getString().equals("Website sign-in"));
            c.takeScreenshot("website-login-02-code");
            var point=c.computeOnClient(mc->{var widget=body(mc.gui.screen());double scale=mc.getWindow().getGuiScale();return new double[]{(widget.getX()+widget.getWidth()/2.0)*scale,(widget.getY()+4+18+4)*scale};});
            c.getInput().setCursorPos(point[0],point[1]);c.getInput().pressMouse(0);c.waitTicks(3);
            if(!c.computeOnClient(mc->mc.keyboardHandler.getClipboard()).equals("AB6N-96CQ-BLWB"))throw new AssertionError("Code copies through native dialog");
            MatchmakingClientTest.click(c,"Close");c.waitFor(mc->mc.gui.screen()==null);
        }
        VanillaSmash.LOG.info("WEBSITE_LOGIN_NATIVE_CLIENT_TEST_PASSED");
    }
}
