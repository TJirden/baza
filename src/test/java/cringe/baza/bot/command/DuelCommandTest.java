package cringe.baza.bot.command;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.User;
import com.pengrad.telegrambot.request.SendMessage;
import cringe.baza.battle.MemeDuelService;
import cringe.baza.bot.model.DuelCreateResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DuelCommandTest {

    @Mock
    private MemeDuelService memeDuelService;

    @InjectMocks
    private DuelCommand duelCommand;

    private Update mockUpdate(String text) {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);
        User user = mock(User.class);

        when(update.message()).thenReturn(message);
        when(message.chat()).thenReturn(chat);
        when(chat.id()).thenReturn(100L);
        when(message.from()).thenReturn(user);
        when(user.id()).thenReturn(200L);
        when(message.text()).thenReturn(text);
        return update;
    }

    @Test
    void commandAndDescription() {
        assertEquals("duel", duelCommand.command());
        assertNotNull(duelCommand.description());
    }

    @Test
    void handle_NullText() {
        assertNull(duelCommand.handle(mockUpdate(null)));
    }

    @Test
    void handle_InvalidFormat() {
        SendMessage response = (SendMessage) duelCommand.handle(mockUpdate("/duel @opponent"));
        assertNotNull(response);
        assertTrue(response.getParameters().get("text").toString().contains("Неверный формат команды"));
    }

    @Test
    void handle_InvalidBetFormat() {
        SendMessage response = (SendMessage) duelCommand.handle(mockUpdate("/duel @opponent abc"));
        assertNotNull(response);
        assertTrue(response.getParameters().get("text").toString().contains("целым числом"));
    }

    @Test
    void handle_NegativeBet() {
        SendMessage response = (SendMessage) duelCommand.handle(mockUpdate("/duel @opponent -10"));
        assertNotNull(response);
        assertTrue(response.getParameters().get("text").toString().contains("больше 0 очков"));
    }

    @Test
    void handle_OpponentNotFound() {
        Update update = mockUpdate("/duel @opponent 50");
        when(memeDuelService.createDuel(200L, "opponent", 50, 100L)).thenReturn(DuelCreateResult.OPPONENT_NOT_FOUND);

        SendMessage response = (SendMessage) duelCommand.handle(update);
        assertNotNull(response);
        assertTrue(response.getParameters().get("text").toString().contains("не найден в базе"));
    }

    @Test
    void handle_OpponentIsSelf() {
        Update update = mockUpdate("/duel @opponent 50");
        when(memeDuelService.createDuel(200L, "opponent", 50, 100L)).thenReturn(DuelCreateResult.SELF_DUEL);

        SendMessage response = (SendMessage) duelCommand.handle(update);
        assertNotNull(response);
        assertTrue(response.getParameters().get("text").toString().contains("самого себя"));
    }

    @Test
    void handle_ChallengerInsufficientPoints() {
        Update update = mockUpdate("/duel @opponent 50");
        when(memeDuelService.createDuel(200L, "opponent", 50, 100L))
                .thenReturn(DuelCreateResult.CHALLENGER_INSUFFICIENT_POINTS);

        SendMessage response = (SendMessage) duelCommand.handle(update);
        assertNotNull(response);
        assertTrue(response.getParameters().get("text").toString().contains("недостаточно очков"));
    }

    @Test
    void handle_OpponentInsufficientPoints() {
        Update update = mockUpdate("/duel @opponent 50");
        when(memeDuelService.createDuel(200L, "opponent", 50, 100L))
                .thenReturn(DuelCreateResult.OPPONENT_INSUFFICIENT_POINTS);

        SendMessage response = (SendMessage) duelCommand.handle(update);
        assertNotNull(response);
        assertTrue(response.getParameters().get("text").toString().contains("оппонента"));
    }

    @Test
    void handle_ChallengerNoMemes() {
        Update update = mockUpdate("/duel @opponent 50");
        when(memeDuelService.createDuel(200L, "opponent", 50, 100L)).thenReturn(DuelCreateResult.CHALLENGER_NO_MEMES);

        SendMessage response = (SendMessage) duelCommand.handle(update);
        assertNotNull(response);
        assertTrue(response.getParameters().get("text").toString().contains("У вас нет одобренных"));
    }

    @Test
    void handle_OpponentNoMemes() {
        Update update = mockUpdate("/duel @opponent 50");
        when(memeDuelService.createDuel(200L, "opponent", 50, 100L)).thenReturn(DuelCreateResult.OPPONENT_NO_MEMES);

        SendMessage response = (SendMessage) duelCommand.handle(update);
        assertNotNull(response);
        assertTrue(
                response.getParameters().get("text").toString().contains("нет одобренных публичных мемов для участия"));
    }

    @Test
    void handle_Error() {
        Update update = mockUpdate("/duel @opponent 50");
        when(memeDuelService.createDuel(200L, "opponent", 50, 100L)).thenReturn(DuelCreateResult.ERROR);

        SendMessage response = (SendMessage) duelCommand.handle(update);
        assertNotNull(response);
        assertTrue(response.getParameters().get("text").toString().contains("Произошла ошибка"));
    }

    @Test
    void handle_Success_ReturnsNull() {
        Update update = mockUpdate("/duel @opponent 50");
        when(memeDuelService.createDuel(200L, "opponent", 50, 100L)).thenReturn(DuelCreateResult.SUCCESS);

        assertNull(duelCommand.handle(update));
        verify(memeDuelService).createDuel(200L, "opponent", 50, 100L);
    }
}
