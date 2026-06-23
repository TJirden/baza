package cringe.baza.bot.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.BaseRequest;
import com.pengrad.telegrambot.request.SendMessage;
import cringe.baza.bot.command.Command;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CommandRouterTest {

    @Mock
    private Command command1;

    @Mock
    private Command command2;

    @Test
    void route_SupportedCommand_ExecutesAndReturns() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);
        BaseRequest<?, ?> expected = mock(BaseRequest.class);

        when(update.message()).thenReturn(message);
        when(message.chat()).thenReturn(chat);
        when(chat.id()).thenReturn(100L);
        when(message.text()).thenReturn("/start");

        when(command1.supports("/start")).thenReturn(false);
        when(command2.supports("/start")).thenReturn(true);
        when(command2.handle(update)).thenAnswer(inv -> expected);

        CommandRouter router = new CommandRouter(List.of(command1, command2));

        BaseRequest<?, ?> result = router.route(update);

        assertEquals(expected, result);
    }

    @Test
    void route_UnsupportedCommand_ReturnsUnknownCommandMessage() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);

        when(update.message()).thenReturn(message);
        when(message.chat()).thenReturn(chat);
        when(chat.id()).thenReturn(100L);
        when(message.text()).thenReturn("/unknown");

        when(command1.supports("/unknown")).thenReturn(false);
        when(command2.supports("/unknown")).thenReturn(false);

        CommandRouter router = new CommandRouter(List.of(command1, command2));

        SendMessage result = (SendMessage) router.route(update);

        assertNotNull(result);
        assertEquals(
                "Неизвестная команда. Используй /help для списка команд.",
                result.getParameters().get("text"));
    }
}
