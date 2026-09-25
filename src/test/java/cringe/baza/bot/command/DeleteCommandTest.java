package cringe.baza.bot.command;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.User;
import com.pengrad.telegrambot.request.BaseRequest;
import com.pengrad.telegrambot.request.SendMessage;
import cringe.baza.meme.MemeProcessor;
import cringe.baza.model.MemeMutationResult;
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
        assertEquals("delete", deleteCommand.command());
        assertEquals("Удалить мем по ID. Пример: /delete 12345", deleteCommand.description());
    }

    @Test
    void handle_NoId_ReturnsError() {
        BaseRequest<?, ?> result = deleteCommand.handle(mockUpdate("/delete"));

        assertNotNull(result);
        assertTrue(result instanceof SendMessage);
        assertEquals(
                "Нужно указать ID мема. Пример: /delete 12345",
                result.getParameters().get("text"));
        verifyNoInteractions(memeProcessor);
    }

    @Test
    void handle_Success() {
        Update update = mockUpdate("/delete meme-1");
        when(memeProcessor.deleteForOwner("meme-1", 123L)).thenReturn(MemeMutationResult.DONE);

        BaseRequest<?, ?> result = deleteCommand.handle(update);

        assertNotNull(result);
        assertTrue(result instanceof SendMessage);
        assertEquals("Мем успешно удален.", result.getParameters().get("text"));
    }

    @Test
    void handle_NotFound() {
        Update update = mockUpdate("/delete meme-1");
        when(memeProcessor.deleteForOwner("meme-1", 123L)).thenReturn(MemeMutationResult.NOT_FOUND);

        BaseRequest<?, ?> result = deleteCommand.handle(update);

        assertNotNull(result);
        assertTrue(result instanceof SendMessage);
        assertEquals("Мем с ID meme-1 не найден.", result.getParameters().get("text"));
    }

    @Test
    void handle_Forbidden() {
        Update update = mockUpdate("/delete meme-1");
        when(memeProcessor.deleteForOwner("meme-1", 123L)).thenReturn(MemeMutationResult.FORBIDDEN);

        BaseRequest<?, ?> result = deleteCommand.handle(update);

        assertNotNull(result);
        assertTrue(result instanceof SendMessage);
        assertEquals("Вы не можете удалить чужой мем.", result.getParameters().get("text"));
    }
}
