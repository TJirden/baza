package cringe.baza.bot.command;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.User;
import com.pengrad.telegrambot.request.BaseRequest;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.request.SendPhoto;
import cringe.baza.meme.MemeProcessor;
import cringe.baza.model.Meme;
import cringe.baza.model.MemeVisibility;
import cringe.baza.user.TelegramUserService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetMemeCommandTest {

    @Mock
    private MemeProcessor memeProcessor;

    @Mock
    private TelegramUserService userService;

    @InjectMocks
    private GetMemeCommand getMemeCommand;

    private Update mockUpdate(String text) {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);
        User user = mock(User.class);
        when(update.message()).thenReturn(message);
        when(message.chat()).thenReturn(chat);
        when(message.text()).thenReturn(text);
        when(chat.id()).thenReturn(123L);
        when(message.from()).thenReturn(user);
        when(user.id()).thenReturn(123L);
        return update;
    }

    @Test
    void metadata() {
        assertEquals("getmeme", getMemeCommand.command());
        assertEquals("Получить мем по ID", getMemeCommand.description());
    }

    @Test
    void handle_NoId_ReturnsError() {
        BaseRequest<?, ?> result = getMemeCommand.handle(mockUpdate("/getmeme"));

        assertNotNull(result);
        assertTrue(result instanceof SendMessage);
        assertEquals(
                "Нужно указать ID мема. Пример: /getmeme 123",
                result.getParameters().get("text"));
        verifyNoInteractions(memeProcessor);
    }

    @Test
    void handle_NotFound() {
        Update update = mockUpdate("/getmeme 123");
        when(userService.getUserGroupIds(123L)).thenReturn(List.of());
        when(memeProcessor.getMemeByIdForUser("123", 123L, List.of())).thenReturn(Optional.empty());

        BaseRequest<?, ?> result = getMemeCommand.handle(update);

        assertNotNull(result);
        assertTrue(result instanceof SendMessage);
        assertEquals("Мем с ID 123 не найден", result.getParameters().get("text"));
    }

    @Test
    void handle_Success() {
        Update update = mockUpdate("/getmeme 123");
        Meme meme = new Meme("123", "cool description", "ocr", "file-123", 1L, MemeVisibility.PUBLIC, List.of());

        when(userService.getUserGroupIds(123L)).thenReturn(List.of(7L));
        when(memeProcessor.getMemeByIdForUser("123", 123L, List.of(7L))).thenReturn(Optional.of(meme));

        BaseRequest<?, ?> result = getMemeCommand.handle(update);

        assertNotNull(result);
        assertTrue(result instanceof SendPhoto);
        assertEquals("file-123", result.getParameters().get("photo"));
        assertEquals("cool description", result.getParameters().get("caption"));
    }
}
