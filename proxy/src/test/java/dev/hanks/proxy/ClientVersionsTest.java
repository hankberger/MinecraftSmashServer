package dev.hanks.proxy;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClientVersionsTest {
    @Test void supportedClientsGetTheirOwnPingProtocol() {
        for (int protocol : new int[] {776, 777}) {
            assertTrue(ClientVersions.supports(protocol));
            assertEquals(protocol, ClientVersions.advertisedProtocol(protocol));
        }
    }
    @Test void unsupportedAndSnapshotClientsNeverGetAMatchingPing() {
        for (int protocol : new int[] {-1, 47, 774, 775, 778, 0x40000000 | 777}) {
            assertFalse(ClientVersions.supports(protocol));
            assertNotEquals(protocol, ClientVersions.advertisedProtocol(protocol));
        }
    }
}
