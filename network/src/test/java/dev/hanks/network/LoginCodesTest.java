package dev.hanks.network;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class LoginCodesTest {
    private static final String KEY="01".repeat(32);
    private static final UUID PLAYER=UUID.fromString("01234567-89ab-cdef-0123-456789abcdef");
    @Test void independentHmacVectorMatchesWebsite() {
        assertEquals("AB6N96CQBLWB",LoginCodes.code(KEY,PLAYER,30_000_000,"AB"));
        assertNotEquals(LoginCodes.code(KEY,PLAYER,30_000_000,"AB"),LoginCodes.code(KEY,PLAYER,30_000_001,"AB"));
        assertNotEquals(LoginCodes.code(KEY,PLAYER,30_000_000,"AB"),LoginCodes.code(KEY,UUID.randomUUID(),30_000_000,"AB"));
    }
    @Test void codesHaveTwelveReadableCharactersAndRequireConfiguredKey() {
        assertFalse(LoginCodes.configured(null));assertFalse(LoginCodes.configured("test"));
        for(int i=0;i<100;i++) {
            var code=LoginCodes.issue(KEY,PLAYER,1_800_000_000_000L);
            assertTrue(code.matches("[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{12}"));
            assertEquals(14,LoginCodes.display(code).length());
        }
        assertThrows(IllegalArgumentException.class,()->LoginCodes.code("invalid",PLAYER,0,"AB"));
    }
}
