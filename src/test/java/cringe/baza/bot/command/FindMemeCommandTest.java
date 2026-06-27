package cringe.baza.bot.command;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.User;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.request.SendPhoto;
import cringe.baza.user.TelegramUserService;
import cringe.baza.model.Meme;
import cringe.baza.model.MemeVisibility;
import cringe.baza.meme.MemeProcessor;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FindMemeCommandTest {

    @Mock
    private MemeProcessor memeProcessor;

    @Mock
    private TelegramUserService userService;

    @InjectMocks
    private FindMemeCommand findMemeCommand;

    @Test
    void handle_EmptyQuery() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);
        User user = mock(User.class);
        when(update.message()).thenReturn(message);
        when(message.chat()).thenReturn(chat);
        when(message.from()).thenReturn(user);
        when(chat.id()).thenReturn(123L);
        when(user.id()).thenReturn(456L);
        when(message.text()).thenReturn("/find");

        SendMessage response = (SendMessage) findMemeCommand.handle(update);

        assertNotNull(response);
        assertEquals(
                "Введите поисковый запрос. Пример: /find пёс",
                response.getParameters().get("text"));
    }

    @Test
    void handle_NotFound() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);
        User user = mock(User.class);
        when(update.message()).thenReturn(message);
        when(message.chat()).thenReturn(chat);
        when(message.from()).thenReturn(user);
        when(chat.id()).thenReturn(123L);
        when(user.id()).thenReturn(456L);
        when(message.text()).thenReturn("/find cat");
        when(userService.getUserGroupIds(456L)).thenReturn(List.of(10L));
        when(memeProcessor.getSingleMemeByDescription("cat", 456L, List.of(10L)))
                .thenReturn(Optional.empty());

        SendMessage response = (SendMessage) findMemeCommand.handle(update);

        assertNotNull(response);
        assertEquals(
                "Ничего не нашлось по запросу: cat", response.getParameters().get("text"));
    }

    @Test
    void handle_Success() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);
        User user = mock(User.class);
        when(update.message()).thenReturn(message);
        when(message.chat()).thenReturn(chat);
        when(message.from()).thenReturn(user);
        when(chat.id()).thenReturn(123L);
        when(user.id()).thenReturn(456L);
        when(message.text()).thenReturn("/find cat");
        when(userService.getUserGroupIds(456L)).thenReturn(List.of(10L));

        Meme meme = new Meme("meme-1", "cute cat", "ocr text", "file-123", 456L, MemeVisibility.PUBLIC, List.of(10L));
        when(memeProcessor.getSingleMemeByDescription("cat", 456L, List.of(10L)))
                .thenReturn(Optional.of(meme));

        SendPhoto response = (SendPhoto) findMemeCommand.handle(update);

        assertNotNull(response);
        assertEquals("file-123", response.getParameters().get("photo"));
        assertEquals("cute cat", response.getParameters().get("caption"));
    }
}
