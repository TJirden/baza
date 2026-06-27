package cringe.baza.bot.command;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.BaseRequest;
import com.pengrad.telegrambot.request.SendMessage;
import cringe.baza.meme.MemeProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeleteCommandTest {

    @Mock
    private MemeProcessor memeProcessor;

    @InjectMocks
    private DeleteCommand deleteCommand;

    @Test
    void metadata() {
        assertEquals("delete", deleteCommand.command());
        assertEquals("Удалить мем по ID. Пример: /delete 12345", deleteCommand.description());
    }

    @Test
    void handle_NoId_ReturnsError() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);

        when(update.message()).thenReturn(message);
        when(message.chat()).thenReturn(chat);
        when(message.text()).thenReturn("/delete");
        when(chat.id()).thenReturn(123L);

        BaseRequest<?, ?> result = deleteCommand.handle(update);

        assertNotNull(result);
        assertTrue(result instanceof SendMessage);
        assertEquals(
                "Нужно указать ID мема. Пример: /delete 12345",
                result.getParameters().get("text"));
        verifyNoInteractions(memeProcessor);
    }

    @Test
    void handle_Success() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);

        when(update.message()).thenReturn(message);
        when(message.chat()).thenReturn(chat);
        when(message.text()).thenReturn("/delete meme-1");
        when(chat.id()).thenReturn(123L);
        when(memeProcessor.delete("meme-1")).thenReturn(true);

        BaseRequest<?, ?> result = deleteCommand.handle(update);

        assertNotNull(result);
        assertTrue(result instanceof SendMessage);
        assertEquals("Мем успешно удален.", result.getParameters().get("text"));
    }

    @Test
    void handle_NotFound() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);

        when(update.message()).thenReturn(message);
        when(message.chat()).thenReturn(chat);
        when(message.text()).thenReturn("/delete meme-1");
        when(chat.id()).thenReturn(123L);
        when(memeProcessor.delete("meme-1")).thenReturn(false);

        BaseRequest<?, ?> result = deleteCommand.handle(update);

        assertNotNull(result);
        assertTrue(result instanceof SendMessage);
        assertEquals("Мем с ID meme-1 не найден.", result.getParameters().get("text"));
    }
}
