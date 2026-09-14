package dev.hanks.vanilla;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WinnerCameraTest {
    @Test void cameraWaitsForChunksThenEasesToAStableReplayView() {
        assertEquals(WinnerCamera.at(0),WinnerCamera.at(WinnerCamera.ATTACH_TICK));
        double previousX=8,previousZ=10;
        for(int age=0;age<=WinnerCamera.REVEAL_TICK;age++) {
            var f=WinnerCamera.at(age);
            assertTrue(Double.isFinite(f.x()) && Float.isFinite(f.yaw()) && Float.isFinite(f.pitch()));
            assertTrue(f.x()<=previousX && f.z()>=previousZ);
            assertTrue(Math.abs(f.x()-previousX)<.25 && Math.abs(f.z()-previousZ)<.13,"No large camera steps");
            previousX=f.x();previousZ=f.z();
        }
        assertEquals(0,WinnerCamera.at(WinnerCamera.REVEAL_TICK).x());
        assertEquals(180,WinnerCamera.at(WinnerCamera.REVEAL_TICK).yaw());
        assertEquals(WinnerCamera.at(WinnerCamera.REVEAL_TICK),WinnerCamera.at(2000));
    }
}
