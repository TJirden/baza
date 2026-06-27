package cringe.baza.user;

import static org.junit.jupiter.api.Assertions.*;

import cringe.baza.bot.model.UserState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UserSessionServiceTest {

    private UserSessionService sessionService;

    @BeforeEach
    void setUp() {
        sessionService = new UserSessionService();
    }

    @Test
    void getUserState_DefaultWhenNotSet() {
        assertEquals(UserState.DEFAULT, sessionService.getUserState(123L));
    }

    @Test
    void setUserState_SavesState() {
        sessionService.setUserState(123L, UserState.SWIPING);
        assertEquals(UserState.SWIPING, sessionService.getUserState(123L));
    }

    @Test
    void setUserState_DefaultClearsData() {
        sessionService.setUserState(123L, UserState.SWIPING);
        sessionService.setTempData(123L, "some-data");

        sessionService.setUserState(123L, UserState.DEFAULT);

        assertEquals(UserState.DEFAULT, sessionService.getUserState(123L));
        assertNull(sessionService.getTempData(123L));
    }

    @Test
    void tempOperations() {
        sessionService.setTempData(123L, "test-data");
        assertEquals("test-data", sessionService.getTempData(123L));

        sessionService.clearTempData(123L);
        assertNull(sessionService.getTempData(123L));
    }
}
