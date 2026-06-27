package cringe.baza.bot.command;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.BaseRequest;
import com.pengrad.telegrambot.request.SendMessage;
import cringe.baza.bot.service.MemeBattleService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BattleCommandTest {

    @Mock
    private TelegramBot bot;

    @Mock
    private MemeBattleService memeBattleService;

    @InjectMocks
    private BattleCommand battleCommand;

    @Test
    void metadata() {
        assertEquals("battle", battleCommand.command());
        assertEquals("Запустить баттл мемов в текущем чате", battleCommand.description());
    }

    @Test
    void handle_TriggersAsyncBattle() throws Exception {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);

        when(update.message()).thenReturn(message);
        when(message.chat()).thenReturn(chat);
        when(chat.id()).thenReturn(123L);

        BaseRequest<?, ?> result = battleCommand.handle(update);

        assertNull(result);
        verify(bot).execute(any(SendMessage.class));

        Thread.sleep(100);
        verify(memeBattleService).startBattle(123L);
    }
}
