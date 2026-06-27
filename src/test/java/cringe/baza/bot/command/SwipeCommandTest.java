package cringe.baza.bot.command;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.User;
import com.pengrad.telegrambot.request.BaseRequest;
import com.pengrad.telegrambot.request.SendMessage;
import cringe.baza.bot.model.UserState;
import cringe.baza.bot.service.SwipeService;
import cringe.baza.bot.service.UserSessionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SwipeCommandTest {

    @Mock
    private SwipeService swipeService;

    @Mock
    private UserSessionService sessionService;

    @InjectMocks
    private SwipeCommand swipeCommand;

    @Test
    void metadata() {
        assertEquals("swipe", swipeCommand.command());
        assertEquals("Оценить случайные мемы (База или Кринж) в ЛС", swipeCommand.description());
    }

    @Test
    void handle_InPrivateChat() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);
        User user = mock(User.class);

        when(update.message()).thenReturn(message);
        when(message.chat()).thenReturn(chat);
        when(message.from()).thenReturn(user);
        when(chat.id()).thenReturn(123L);
        when(user.id()).thenReturn(123L);

        BaseRequest<?, ?> result = swipeCommand.handle(update);

        assertNull(result);
        verify(sessionService).setUserState(123L, UserState.SWIPING);
        verify(swipeService).sendSwipeCard(123L, 123L);
    }

    @Test
    void handle_InGroupChat() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);
        User user = mock(User.class);

        when(update.message()).thenReturn(message);
        when(message.chat()).thenReturn(chat);
        when(message.from()).thenReturn(user);
        when(chat.id()).thenReturn(456L); // Group chat
        when(user.id()).thenReturn(123L);

        BaseRequest<?, ?> result = swipeCommand.handle(update);

        assertNotNull(result);
        assertTrue(result instanceof SendMessage);
        assertEquals(456L, result.getParameters().get("chat_id"));
        assertTrue(((String) result.getParameters().get("text")).contains("доступна только в личных сообщениях"));
        verifyNoInteractions(sessionService);
        verifyNoInteractions(swipeService);
    }
}
