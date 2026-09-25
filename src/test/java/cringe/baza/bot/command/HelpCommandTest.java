package cringe.baza.bot.command;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.SendMessage;
import java.util.List;
import org.junit.jupiter.api.Test;

class HelpCommandTest {

    @Test
    void metadata() {
        HelpCommand helpCommand = new HelpCommand(List.of());
        assertEquals("help", helpCommand.command());
        assertEquals("Список команд", helpCommand.description());
    }

    @Test
    void handle_ListsAllCommandsExceptItself() {
        Command start = mock(Command.class);
        when(start.command()).thenReturn("start");
        when(start.description()).thenReturn("Старт!");
        Command find = mock(Command.class);
        when(find.command()).thenReturn("find");
        when(find.description()).thenReturn("Найти мемы");

        HelpCommand helpCommand = new HelpCommand(List.of(start, find));

        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);
        when(update.message()).thenReturn(message);
        when(message.chat()).thenReturn(chat);
        when(chat.id()).thenReturn(123L);

        SendMessage response = helpCommand.handle(update);

        String text = response.getParameters().get("text").toString();
        assertEquals(123L, response.getParameters().get("chat_id"));
        assertTrue(text.contains("/start — Старт!"));
        assertTrue(text.contains("/find — Найти мемы"));
        assertFalse(text.contains("/help"));
    }
}
