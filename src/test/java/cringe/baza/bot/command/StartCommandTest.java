package cringe.baza.bot.command;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.SendMessage;
import cringe.baza.bot.model.UserState;
import cringe.baza.bot.service.UserSessionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StartCommandTest {

    @Mock
    private UserSessionService sessionService;

    @InjectMocks
    private StartCommand startCommand;

    @Test
    void metadata() {
        assertEquals("start", startCommand.command());
        assertEquals("Старт!", startCommand.description());
    }

    @Test
    void handle() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);

        when(update.message()).thenReturn(message);
        when(message.chat()).thenReturn(chat);
        when(chat.id()).thenReturn(123L);

        SendMessage response = startCommand.handle(update);

        assertNotNull(response);
        assertEquals(123L, response.getParameters().get("chat_id"));
        assertEquals("Привет базированным!", response.getParameters().get("text"));
        verify(sessionService).setUserState(123L, UserState.DEFAULT);
    }
}
