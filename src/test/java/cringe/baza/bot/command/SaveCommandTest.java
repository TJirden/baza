package cringe.baza.bot.command;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.User;
import com.pengrad.telegrambot.request.SendMessage;
import cringe.baza.bot.model.UserState;
import cringe.baza.bot.service.UserSessionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SaveCommandTest {

    @Mock
    private UserSessionService sessionService;

    @InjectMocks
    private SaveCommand saveCommand;

    @Test
    void handle_GroupChat_Rejected() {
        // Arrange
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);
        User user = mock(User.class);

        when(update.message()).thenReturn(message);
        when(message.chat()).thenReturn(chat);
        when(message.from()).thenReturn(user);

        when(chat.id()).thenReturn(100L); // group chatId
        when(user.id()).thenReturn(200L); // userId != chatId

        // Act
        SendMessage response = saveCommand.handle(update);

        // Assert
        assertNotNull(response);
        assertEquals("⚠️ Команда /save доступна только в личных сообщениях с ботом.", response.getParameters().get("text"));
        verifyNoInteractions(sessionService);
    }

    @Test
    void handle_PrivateChat_Success() {
        // Arrange
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);
        User user = mock(User.class);

        when(update.message()).thenReturn(message);
        when(message.chat()).thenReturn(chat);
        when(message.from()).thenReturn(user);

        when(chat.id()).thenReturn(200L); // private chatId
        when(user.id()).thenReturn(200L); // userId == chatId
        when(message.text()).thenReturn("/save private");

        // Act
        SendMessage response = saveCommand.handle(update);

        // Assert
        assertNotNull(response);
        assertTrue(((String) response.getParameters().get("text")).contains("PRIVATE"));
        verify(sessionService).setUserState(200L, UserState.AWAITING_SAVE_IMAGE);
        verify(sessionService).setTempData(200L, "PRIVATE");
    }
}
