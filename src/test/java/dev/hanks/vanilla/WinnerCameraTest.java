package dev.hanks.vanilla;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WinnerCameraTest {
    @Test void destinationFitsVanillaInterpolationAndControlsWaitForArrival() {
        assertTrue(WinnerCamera.DURATION>0 && WinnerCamera.DURATION<=59,"Vanilla teleport_duration range");
        assertTrue(WinnerCamera.MOVE_TICK>WinnerCamera.ATTACH_TICK,"Attach before moving");
        assertTrue(WinnerCamera.REVEAL_TICK>WinnerCamera.MOVE_TICK+WinnerCamera.DURATION,"Replay waits for client interpolation");
        assertEquals(WinnerCamera.START.yaw(),WinnerCamera.END.yaw());
        assertEquals(WinnerCamera.START.pitch(),WinnerCamera.END.pitch());
        assertTrue(Math.abs(WinnerCamera.END.x()-WinnerCamera.START.x())<8);
        assertTrue(Math.abs(WinnerCamera.END.z()-WinnerCamera.START.z())<8,"Single relative move fits the wire format");
    }
    @Test void winnerAndReplayLabelsFitTheSeventyDegreeView() {
        for(var frame:new WinnerCamera.Frame[]{WinnerCamera.START,WinnerCamera.END}) {
            for(double[] point:new double[][]{{110.1,1.3},{101.2,4},{108.5,0},{102,0}}) {
                double angle=Math.toDegrees(Math.atan2(point[0]-frame.y(),frame.z()-point[1]))+frame.pitch();
                assertTrue(Math.abs(angle)<35,"Winner title, model and name stay in frame");
            }
        }
        double bottom=Math.toDegrees(Math.atan2(99.15-WinnerCamera.END.y(),WinnerCamera.END.z()-1.6))+WinnerCamera.END.pitch();
        assertTrue(Math.abs(bottom)<35,"Final replay controls stay in frame");
    }
}
