package cringe.baza.bot.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.PhotoSize;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.User;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.SendResponse;
import cringe.baza.bot.command.Command;
import cringe.baza.bot.model.UserState;
import cringe.baza.processor.MemeProcessor;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UpdateProcessorTest {

    @Mock
    private List<Command> commands;

    @Mock
    private UserSessionService sessionService;

    @Mock
    private TelegramFileService fileService;

    @Mock
    private MemeProcessor memeProcessor;

    @Mock
    private TelegramUserService userService;

    @Mock
    private TelegramBot bot;

    @Mock
    private AsyncMemeService asyncMemeService;

    @InjectMocks
    private UpdateProcessor updateProcessor;

    @Test
    void processImageSave_ValidationFailure_NoPhoto() {
        // Arrange
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);
        User user = mock(User.class);

        when(update.message()).thenReturn(message);
        when(message.chat()).thenReturn(chat);
        when(message.from()).thenReturn(user);
        when(chat.id()).thenReturn(100L);
        when(user.id()).thenReturn(200L);
        when(message.photo()).thenReturn(null);

        when(sessionService.getUserState(100L)).thenReturn(UserState.AWAITING_SAVE_IMAGE);

        // Act
        SendMessage result = (SendMessage) updateProcessor.processUpdate(update);

        // Assert
        assertNotNull(result);
        verify(sessionService).setUserState(100L, UserState.DEFAULT);
        verifyNoInteractions(asyncMemeService);
    }

    @Test
    void processImageSave_ValidationFailure_NoDescription() {
        // Arrange
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);
        User user = mock(User.class);
        PhotoSize[] photo = new PhotoSize[] {mock(PhotoSize.class)};

        when(update.message()).thenReturn(message);
        when(message.chat()).thenReturn(chat);
        when(message.from()).thenReturn(user);
        when(chat.id()).thenReturn(100L);
        when(user.id()).thenReturn(200L);
        when(message.photo()).thenReturn(photo);
        when(message.caption()).thenReturn(null); // Will trigger error

        when(sessionService.getUserState(100L)).thenReturn(UserState.AWAITING_SAVE_IMAGE);

        // Act
        SendMessage result = (SendMessage) updateProcessor.processUpdate(update);

        // Assert
        assertNotNull(result);
        verify(sessionService, never()).setUserState(100L, UserState.DEFAULT);
        verifyNoInteractions(asyncMemeService);
    }

    @Test
    void processImageSave_Success() {
        // Arrange
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);
        User user = mock(User.class);
        PhotoSize[] photo = new PhotoSize[] {mock(PhotoSize.class)};
        SendResponse sendResponse = mock(SendResponse.class);
        Message sentMessage = mock(Message.class);

        when(update.message()).thenReturn(message);
        when(message.chat()).thenReturn(chat);
        when(message.from()).thenReturn(user);
        when(chat.id()).thenReturn(100L);
        when(user.id()).thenReturn(200L);
        when(message.photo()).thenReturn(photo);
        when(message.caption()).thenReturn("Funny dog");

        when(sessionService.getUserState(100L)).thenReturn(UserState.AWAITING_SAVE_IMAGE);
        when(sessionService.getTempData(100L)).thenReturn("PUBLIC");

        when(bot.execute(any(SendMessage.class))).thenReturn(sendResponse);
        when(sendResponse.isOk()).thenReturn(true);
        when(sendResponse.message()).thenReturn(sentMessage);
        when(sentMessage.messageId()).thenReturn(777);

        // Act
        SendMessage result = (SendMessage) updateProcessor.processUpdate(update);

        // Assert
        assertNull(result); // Null return signifies successful delegation to async service
        verify(sessionService).setUserState(100L, UserState.DEFAULT);
        verify(asyncMemeService)
                .processAndSaveMemeAsync(eq(100L), eq(200L), eq(photo), eq("Funny dog"), eq("PUBLIC"), eq(777));
    }
}
