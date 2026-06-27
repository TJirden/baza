package cringe.baza.bot.command;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.SendMessage;
import cringe.baza.meme.MemeProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EditCommandTest {

    @Mock
    private MemeProcessor memeProcessor;

    @InjectMocks
    private EditCommand editCommand;

    @Test
    void handle_MissingArguments() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);
        when(update.message()).thenReturn(message);
        when(message.chat()).thenReturn(chat);
        when(chat.id()).thenReturn(123L);
        when(message.text()).thenReturn("/edit");

        SendMessage response = (SendMessage) editCommand.handle(update);

        assertNotNull(response);
        assertEquals(
                "Использование: /edit {id} {новое описание}",
                response.getParameters().get("text"));
    }

    @Test
    void handle_MissingDescription() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);
        when(update.message()).thenReturn(message);
        when(message.chat()).thenReturn(chat);
        when(chat.id()).thenReturn(123L);
        when(message.text()).thenReturn("/edit meme-id");

        SendMessage response = (SendMessage) editCommand.handle(update);

        assertNotNull(response);
        assertEquals(
                "Необходимо указать новое описание. Пример: /edit 12345 смешной кот",
                response.getParameters().get("text"));
    }

    @Test
    void handle_Success() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);
        when(update.message()).thenReturn(message);
        when(message.chat()).thenReturn(chat);
        when(chat.id()).thenReturn(123L);
        when(message.text()).thenReturn("/edit meme-id cool new desc");
        when(memeProcessor.update("meme-id", "cool new desc")).thenReturn(true);

        SendMessage response = (SendMessage) editCommand.handle(update);

        assertNotNull(response);
        assertEquals(
                "Описание мема успешно обновлено.", response.getParameters().get("text"));
        verify(memeProcessor).update("meme-id", "cool new desc");
    }

    @Test
    void handle_NotFound() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);
        when(update.message()).thenReturn(message);
        when(message.chat()).thenReturn(chat);
        when(chat.id()).thenReturn(123L);
        when(message.text()).thenReturn("/edit meme-id cool new desc");
        when(memeProcessor.update("meme-id", "cool new desc")).thenReturn(false);

        SendMessage response = (SendMessage) editCommand.handle(update);

        assertNotNull(response);
        assertEquals("Мем с ID meme-id не найден.", response.getParameters().get("text"));
    }
}
