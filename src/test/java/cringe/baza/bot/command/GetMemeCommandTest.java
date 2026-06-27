package cringe.baza.bot.command;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.BaseRequest;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.request.SendPhoto;
import cringe.baza.meme.MemeProcessor;
import cringe.baza.model.Meme;
import cringe.baza.model.MemeVisibility;
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

    @InjectMocks
    private GetMemeCommand getMemeCommand;

    @Test
    void metadata() {
        assertEquals("getmeme", getMemeCommand.command());
        assertEquals("Получить мем по ID", getMemeCommand.description());
    }

    @Test
    void handle_NoId_ReturnsError() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);

        when(update.message()).thenReturn(message);
        when(message.chat()).thenReturn(chat);
        when(message.text()).thenReturn("/getmeme");
        when(chat.id()).thenReturn(123L);

        BaseRequest<?, ?> result = getMemeCommand.handle(update);

        assertNotNull(result);
        assertTrue(result instanceof SendMessage);
        assertEquals(
                "Нужно указать ID мема. Пример: /getmeme 123",
                result.getParameters().get("text"));
        verifyNoInteractions(memeProcessor);
    }

    @Test
    void handle_NotFound() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);

        when(update.message()).thenReturn(message);
        when(message.chat()).thenReturn(chat);
        when(message.text()).thenReturn("/getmeme 123");
        when(chat.id()).thenReturn(123L);
        when(memeProcessor.getMemeById("123")).thenReturn(Optional.empty());

        BaseRequest<?, ?> result = getMemeCommand.handle(update);

        assertNotNull(result);
        assertTrue(result instanceof SendMessage);
        assertEquals("Мем с ID 123 не найден", result.getParameters().get("text"));
    }

    @Test
    void handle_Success() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);
        Meme meme = new Meme("123", "cool description", "ocr", "file-123", 1L, MemeVisibility.PUBLIC, List.of());

        when(update.message()).thenReturn(message);
        when(message.chat()).thenReturn(chat);
        when(message.text()).thenReturn("/getmeme 123");
        when(chat.id()).thenReturn(123L);
        when(memeProcessor.getMemeById("123")).thenReturn(Optional.of(meme));

        BaseRequest<?, ?> result = getMemeCommand.handle(update);

        assertNotNull(result);
        assertTrue(result instanceof SendPhoto);
        assertEquals("file-123", result.getParameters().get("photo"));
        assertEquals("cool description", result.getParameters().get("caption"));
    }
}
