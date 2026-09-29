package dev.hanks.network;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DiscordRewardTest {
    @TempDir Path dir;
    @Test void restartAndLostAcknowledgmentCannotDoubleCredit() throws Exception {
        var player=UUID.randomUUID();var file=dir.resolve("points.db");
        var reward=new PointsStore.DiscordReward("discord:1554281966197538908:123456789012345678",player,750);
        try(var db=new PointsStore(file)) {assertEquals(750,db.rewardDiscord(reward).balance());}
        try(var db=new PointsStore(file)) {
            assertEquals(750,db.rewardDiscord(reward).balance());
            assertThrows(SQLException.class,()->db.rewardDiscord(new PointsStore.DiscordReward(reward.id(),UUID.randomUUID(),750)));
            assertThrows(SQLException.class,()->db.rewardDiscord(new PointsStore.DiscordReward("discord:1554281966197538908:223456789012345678",player,750)));
            assertEquals(750,db.account(player).earned());assertEquals(0,db.account(player).matches());assertEquals(0,db.xp(player));
            assertEquals(0,db.memberUntil(player));
        }
    }
    @Test void validatesGuildAmountAndReceipt() {
        var p=UUID.randomUUID();
        for(String id:new String[]{"order:123","discord:9999999999999999999:123456789012345678","discord:1554281966197538908:invalid"})
            assertThrows(IllegalArgumentException.class,()->new PointsStore.DiscordReward(id,p,750));
        assertThrows(IllegalArgumentException.class,()->new PointsStore.DiscordReward("discord:1554281966197538908:123456789012345678",p,751));
    }
}
