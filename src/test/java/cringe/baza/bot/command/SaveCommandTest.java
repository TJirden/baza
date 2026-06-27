package cringe.baza.bot.command;

import static org.junit.jupiter.api.Assertions.*;
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
import cringe.baza.meme.AsyncMemeService;
import cringe.baza.user.UserSessionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SaveCommandTest {

    @Mock
    private UserSessionService sessionService;

    @Mock
    private TelegramBot bot;

    @Mock
    private AsyncMemeService asyncMemeService;

    @Mock
    private SaveCommandParser parser;

    @InjectMocks
    private SaveCommand saveCommand;

    @Test
    void handle_GroupChat_NoReply_Rejected() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);
        User user = mock(User.class);

        when(update.message()).thenReturn(message);
        when(message.chat()).thenReturn(chat);
        when(message.from()).thenReturn(user);

        when(chat.id()).thenReturn(100L);
        when(user.id()).thenReturn(200L);
        when(message.replyToMessage()).thenReturn(null);

        SendMessage response = saveCommand.handle(update);

        assertNotNull(response);
        assertEquals(
                "В групповом чате команда /save должна быть ответом на сообщение с фото.",
                response.getParameters().get("text"));
        verifyNoInteractions(sessionService);
        verifyNoInteractions(asyncMemeService);
    }

    @Test
    void handle_GroupChat_ReplyWithPhoto_Success() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Message replyTo = mock(Message.class);
        Chat chat = mock(Chat.class);
        User user = mock(User.class);
        PhotoSize[] photos = new PhotoSize[] {mock(PhotoSize.class)};
        SendResponse sendResponse = mock(SendResponse.class);
        Message sentMsg = mock(Message.class);

        when(update.message()).thenReturn(message);
        when(message.chat()).thenReturn(chat);
        when(message.from()).thenReturn(user);
        when(message.replyToMessage()).thenReturn(replyTo);

        when(chat.id()).thenReturn(100L);
        when(user.id()).thenReturn(200L);
        when(message.text()).thenReturn("/save private cool cat");
        when(parser.parseReplySave("private cool cat")).thenReturn(SaveParseResult.success("PRIVATE", "cool cat"));

        when(replyTo.photo()).thenReturn(photos);
        when(bot.execute(any(SendMessage.class))).thenReturn(sendResponse);
        when(sendResponse.isOk()).thenReturn(true);
        when(sendResponse.message()).thenReturn(sentMsg);
        when(sentMsg.messageId()).thenReturn(999);

        SendMessage response = saveCommand.handle(update);

        assertNull(response);
        verify(asyncMemeService).saveMemeAsync(eq(100L), eq(200L), eq(photos), eq("cool cat"), eq("PRIVATE"), eq(999));
        verifyNoInteractions(sessionService);
    }

    @Test
    void handle_PrivateChat_NoReply_Success() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);
        User user = mock(User.class);

        when(update.message()).thenReturn(message);
        when(message.chat()).thenReturn(chat);
        when(message.from()).thenReturn(user);

        when(chat.id()).thenReturn(200L);
        when(user.id()).thenReturn(200L);
        when(message.text()).thenReturn("/save private");
        when(parser.parseStatefulSave("private")).thenReturn(SaveParseResult.success("PRIVATE", null));

        SendMessage response = saveCommand.handle(update);

        assertNotNull(response);
        assertTrue(((String) response.getParameters().get("text")).contains("PRIVATE"));
        verify(sessionService).setUserState(200L, UserState.AWAITING_SAVE_IMAGE);
        verify(sessionService).setTempData(200L, "PRIVATE");
        verifyNoInteractions(asyncMemeService);
    }

    @Test
    void handle_PrivateChat_ReplyWithPhoto_Success() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Message replyTo = mock(Message.class);
        Chat chat = mock(Chat.class);
        User user = mock(User.class);
        PhotoSize[] photos = new PhotoSize[] {mock(PhotoSize.class)};
        SendResponse sendResponse = mock(SendResponse.class);
        Message sentMsg = mock(Message.class);

        when(update.message()).thenReturn(message);
        when(message.chat()).thenReturn(chat);
        when(message.from()).thenReturn(user);
        when(message.replyToMessage()).thenReturn(replyTo);

        when(chat.id()).thenReturn(200L);
        when(user.id()).thenReturn(200L);
        when(message.text()).thenReturn("/save public custom description");
        when(parser.parseReplySave("public custom description"))
                .thenReturn(SaveParseResult.success("PUBLIC", "custom description"));

        when(replyTo.photo()).thenReturn(photos);
        when(bot.execute(any(SendMessage.class))).thenReturn(sendResponse);
        when(sendResponse.isOk()).thenReturn(true);
        when(sendResponse.message()).thenReturn(sentMsg);
        when(sentMsg.messageId()).thenReturn(888);

        SendMessage response = saveCommand.handle(update);

        assertNull(response);
        verify(asyncMemeService)
                .saveMemeAsync(eq(200L), eq(200L), eq(photos), eq("custom description"), eq("PUBLIC"), eq(888));
        verifyNoInteractions(sessionService);
    }
}
