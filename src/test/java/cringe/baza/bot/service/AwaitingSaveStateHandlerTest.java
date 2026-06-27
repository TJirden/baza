package cringe.baza.bot.service;

import cringe.baza.meme.AsyncMemeService;
import cringe.baza.user.UserSessionService;

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
import cringe.baza.bot.model.UserState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AwaitingSaveStateHandlerTest {

    @Mock
    private UserSessionService sessionService;

    @Mock
    private TelegramBot bot;

    @Mock
    private AsyncMemeService asyncMemeService;

    @InjectMocks
    private AwaitingSaveStateHandler handler;

    @Test
    void handle_NoPhoto_ReturnsError() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);
        User user = mock(User.class);

        when(update.message()).thenReturn(message);
        when(message.chat()).thenReturn(chat);
        when(chat.id()).thenReturn(100L);
        when(message.from()).thenReturn(user);
        when(user.id()).thenReturn(200L);
        when(message.photo()).thenReturn(null);

        SendMessage result = handler.handle(update);

        assertNotNull(result);
        verify(sessionService).setUserState(100L, UserState.DEFAULT);
        verifyNoInteractions(asyncMemeService);
    }

    @Test
    void handle_Success_TriggersAsyncSave() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);
        User user = mock(User.class);
        PhotoSize[] photo = new PhotoSize[] {mock(PhotoSize.class)};
        SendResponse sendResponse = mock(SendResponse.class);
        Message sentMessage = mock(Message.class);

        when(update.message()).thenReturn(message);
        when(message.chat()).thenReturn(chat);
        when(chat.id()).thenReturn(100L);
        when(message.from()).thenReturn(user);
        when(user.id()).thenReturn(200L);
        when(message.photo()).thenReturn(photo);
        when(message.caption()).thenReturn("Funny cat");

        when(bot.execute(any(SendMessage.class))).thenReturn(sendResponse);
        when(sendResponse.isOk()).thenReturn(true);
        when(sendResponse.message()).thenReturn(sentMessage);
        when(sentMessage.messageId()).thenReturn(123);
        when(sessionService.getTempData(100L)).thenReturn("PUBLIC");

        SendMessage result = handler.handle(update);

        assertNull(result);
        verify(sessionService).setUserState(100L, UserState.DEFAULT);
        verify(asyncMemeService).saveMemeAsync(100L, 200L, photo, "Funny cat", "PUBLIC", 123);
    }

    @Test
    void handle_ResponseNotOk_ReturnsError() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);
        User user = mock(User.class);
        PhotoSize[] photo = new PhotoSize[] {mock(PhotoSize.class)};
        SendResponse sendResponse = mock(SendResponse.class);

        when(update.message()).thenReturn(message);
        when(message.chat()).thenReturn(chat);
        when(chat.id()).thenReturn(100L);
        when(message.from()).thenReturn(user);
        when(user.id()).thenReturn(200L);
        when(message.photo()).thenReturn(photo);

        when(bot.execute(any(SendMessage.class))).thenReturn(sendResponse);
        when(sendResponse.isOk()).thenReturn(false);

        SendMessage result = handler.handle(update);

        assertNotNull(result);
        verify(sessionService).setUserState(100L, UserState.DEFAULT);
        verifyNoInteractions(asyncMemeService);
    }

    @Test
    void handle_Exception_ReturnsError() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);
        User user = mock(User.class);
        PhotoSize[] photo = new PhotoSize[] {mock(PhotoSize.class)};

        when(update.message()).thenReturn(message);
        when(message.chat()).thenReturn(chat);
        when(chat.id()).thenReturn(100L);
        when(message.from()).thenReturn(user);
        when(user.id()).thenReturn(200L);
        when(message.photo()).thenReturn(photo);

        when(bot.execute(any(SendMessage.class))).thenThrow(new RuntimeException("API error"));

        SendMessage result = handler.handle(update);

        assertNotNull(result);
        verify(sessionService).setUserState(100L, UserState.DEFAULT);
        verifyNoInteractions(asyncMemeService);
    }
}
