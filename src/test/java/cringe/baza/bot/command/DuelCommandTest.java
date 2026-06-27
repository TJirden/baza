package cringe.baza.bot.command;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.User;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.SendResponse;
import cringe.baza.domain.MemeBattle;
import cringe.baza.domain.MemeModeration;
import cringe.baza.domain.TelegramUser;
import cringe.baza.model.MemeVisibility;
import cringe.baza.model.ModerationStatus;
import cringe.baza.repository.jpa.MemeBattleRepository;
import cringe.baza.repository.jpa.MemeModerationRepository;
import cringe.baza.repository.jpa.TelegramUserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DuelCommandTest {

    @Mock
    private TelegramUserRepository telegramUserRepository;

    @Mock
    private MemeModerationRepository memeModerationRepository;

    @Mock
    private MemeBattleRepository memeBattleRepository;

    @Mock
    private TelegramBot bot;

    @InjectMocks
    private DuelCommand duelCommand;

    @Test
    void commandAndDescription() {
        assertEquals("duel", duelCommand.command());
        assertNotNull(duelCommand.description());
    }

    @Test
    void handle_NullText() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);
        User user = mock(User.class);

        when(update.message()).thenReturn(message);
        when(message.chat()).thenReturn(chat);
        when(chat.id()).thenReturn(100L);
        when(message.from()).thenReturn(user);
        when(user.id()).thenReturn(200L);
        when(message.text()).thenReturn(null);

        assertNull(duelCommand.handle(update));
    }

    @Test
    void handle_InvalidFormat() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);
        User user = mock(User.class);

        when(update.message()).thenReturn(message);
        when(message.chat()).thenReturn(chat);
        when(chat.id()).thenReturn(100L);
        when(message.from()).thenReturn(user);
        when(user.id()).thenReturn(200L);
        when(message.text()).thenReturn("/duel @opponent");

        SendMessage response = (SendMessage) duelCommand.handle(update);
        assertNotNull(response);
        assertTrue(response.getParameters().get("text").toString().contains("Неверный формат команды"));
    }

    @Test
    void handle_InvalidBetFormat() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);
        User user = mock(User.class);

        when(update.message()).thenReturn(message);
        when(message.chat()).thenReturn(chat);
        when(chat.id()).thenReturn(100L);
        when(message.from()).thenReturn(user);
        when(user.id()).thenReturn(200L);
        when(message.text()).thenReturn("/duel @opponent abc");

        SendMessage response = (SendMessage) duelCommand.handle(update);
        assertNotNull(response);
        assertTrue(response.getParameters().get("text").toString().contains("целым числом"));
    }

    @Test
    void handle_NegativeBet() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);
        User user = mock(User.class);

        when(update.message()).thenReturn(message);
        when(message.chat()).thenReturn(chat);
        when(chat.id()).thenReturn(100L);
        when(message.from()).thenReturn(user);
        when(user.id()).thenReturn(200L);
        when(message.text()).thenReturn("/duel @opponent -10");

        SendMessage response = (SendMessage) duelCommand.handle(update);
        assertNotNull(response);
        assertTrue(response.getParameters().get("text").toString().contains("больше 0 очков"));
    }

    @Test
    void handle_OpponentNotFound() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);
        User user = mock(User.class);

        when(update.message()).thenReturn(message);
        when(message.chat()).thenReturn(chat);
        when(chat.id()).thenReturn(100L);
        when(message.from()).thenReturn(user);
        when(user.id()).thenReturn(200L);
        when(message.text()).thenReturn("/duel @opponent 50");

        when(telegramUserRepository.findByUsernameIgnoreCase("opponent")).thenReturn(Optional.empty());

        SendMessage response = (SendMessage) duelCommand.handle(update);
        assertNotNull(response);
        assertTrue(response.getParameters().get("text").toString().contains("не найден в базе"));
    }

    @Test
    void handle_OpponentIsSelf() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);
        User user = mock(User.class);

        when(update.message()).thenReturn(message);
        when(message.chat()).thenReturn(chat);
        when(chat.id()).thenReturn(100L);
        when(message.from()).thenReturn(user);
        when(user.id()).thenReturn(200L);
        when(message.text()).thenReturn("/duel @opponent 50");

        TelegramUser opponent = new TelegramUser();
        opponent.setId(200L);
        when(telegramUserRepository.findByUsernameIgnoreCase("opponent")).thenReturn(Optional.of(opponent));

        SendMessage response = (SendMessage) duelCommand.handle(update);
        assertNotNull(response);
        assertTrue(response.getParameters().get("text").toString().contains("самого себя"));
    }

    @Test
    void handle_ChallengerNotFound() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);
        User user = mock(User.class);

        when(update.message()).thenReturn(message);
        when(message.chat()).thenReturn(chat);
        when(chat.id()).thenReturn(100L);
        when(message.from()).thenReturn(user);
        when(user.id()).thenReturn(200L);
        when(message.text()).thenReturn("/duel @opponent 50");

        TelegramUser opponent = new TelegramUser();
        opponent.setId(300L);
        when(telegramUserRepository.findByUsernameIgnoreCase("opponent")).thenReturn(Optional.of(opponent));
        when(telegramUserRepository.findById(200L)).thenReturn(Optional.empty());

        SendMessage response = (SendMessage) duelCommand.handle(update);
        assertNotNull(response);
        assertTrue(response.getParameters().get("text").toString().contains("ошибка"));
    }

    @Test
    void handle_ChallengerInsufficientPoints() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);
        User user = mock(User.class);

        when(update.message()).thenReturn(message);
        when(message.chat()).thenReturn(chat);
        when(chat.id()).thenReturn(100L);
        when(message.from()).thenReturn(user);
        when(user.id()).thenReturn(200L);
        when(message.text()).thenReturn("/duel @opponent 50");

        TelegramUser opponent = new TelegramUser();
        opponent.setId(300L);
        TelegramUser challenger = new TelegramUser();
        challenger.setId(200L);
        challenger.setPoints(10);

        when(telegramUserRepository.findByUsernameIgnoreCase("opponent")).thenReturn(Optional.of(opponent));
        when(telegramUserRepository.findById(200L)).thenReturn(Optional.of(challenger));

        SendMessage response = (SendMessage) duelCommand.handle(update);
        assertNotNull(response);
        assertTrue(response.getParameters().get("text").toString().contains("недостаточно очков"));
    }

    @Test
    void handle_OpponentInsufficientPoints() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);
        User user = mock(User.class);

        when(update.message()).thenReturn(message);
        when(message.chat()).thenReturn(chat);
        when(chat.id()).thenReturn(100L);
        when(message.from()).thenReturn(user);
        when(user.id()).thenReturn(200L);
        when(message.text()).thenReturn("/duel @opponent 50");

        TelegramUser opponent = new TelegramUser();
        opponent.setId(300L);
        opponent.setPoints(10);
        TelegramUser challenger = new TelegramUser();
        challenger.setId(200L);
        challenger.setPoints(100);

        when(telegramUserRepository.findByUsernameIgnoreCase("opponent")).thenReturn(Optional.of(opponent));
        when(telegramUserRepository.findById(200L)).thenReturn(Optional.of(challenger));

        SendMessage response = (SendMessage) duelCommand.handle(update);
        assertNotNull(response);
        assertTrue(response.getParameters().get("text").toString().contains("оппонента"));
    }

    @Test
    void handle_ChallengerNoMemes() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);
        User user = mock(User.class);

        when(update.message()).thenReturn(message);
        when(message.chat()).thenReturn(chat);
        when(chat.id()).thenReturn(100L);
        when(message.from()).thenReturn(user);
        when(user.id()).thenReturn(200L);
        when(message.text()).thenReturn("/duel @opponent 50");

        TelegramUser opponent = new TelegramUser();
        opponent.setId(300L);
        opponent.setPoints(100);
        TelegramUser challenger = new TelegramUser();
        challenger.setId(200L);
        challenger.setPoints(100);

        when(telegramUserRepository.findByUsernameIgnoreCase("opponent")).thenReturn(Optional.of(opponent));
        when(telegramUserRepository.findById(200L)).thenReturn(Optional.of(challenger));
        when(memeModerationRepository.findByOwnerIdAndStatusAndVisibility(
                        200L, ModerationStatus.APPROVED, MemeVisibility.PUBLIC))
                .thenReturn(List.of());

        SendMessage response = (SendMessage) duelCommand.handle(update);
        assertNotNull(response);
        assertTrue(response.getParameters().get("text").toString().contains("У вас нет одобренных"));
    }

    @Test
    void handle_OpponentNoMemes() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);
        User user = mock(User.class);

        when(update.message()).thenReturn(message);
        when(message.chat()).thenReturn(chat);
        when(chat.id()).thenReturn(100L);
        when(message.from()).thenReturn(user);
        when(user.id()).thenReturn(200L);
        when(message.text()).thenReturn("/duel @opponent 50");

        TelegramUser opponent = new TelegramUser();
        opponent.setId(300L);
        opponent.setPoints(100);
        TelegramUser challenger = new TelegramUser();
        challenger.setId(200L);
        challenger.setPoints(100);

        when(telegramUserRepository.findByUsernameIgnoreCase("opponent")).thenReturn(Optional.of(opponent));
        when(telegramUserRepository.findById(200L)).thenReturn(Optional.of(challenger));
        when(memeModerationRepository.findByOwnerIdAndStatusAndVisibility(
                        200L, ModerationStatus.APPROVED, MemeVisibility.PUBLIC))
                .thenReturn(List.of(new MemeModeration()));
        when(memeModerationRepository.findByOwnerIdAndStatusAndVisibility(
                        300L, ModerationStatus.APPROVED, MemeVisibility.PUBLIC))
                .thenReturn(List.of());

        SendMessage response = (SendMessage) duelCommand.handle(update);
        assertNotNull(response);
        assertTrue(
                response.getParameters().get("text").toString().contains("нет одобренных публичных мемов для участия"));
    }

    @Test
    void handle_Success_BotExecuteOk() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);
        User user = mock(User.class);

        when(update.message()).thenReturn(message);
        when(message.chat()).thenReturn(chat);
        when(chat.id()).thenReturn(100L);
        when(message.from()).thenReturn(user);
        when(user.id()).thenReturn(200L);
        when(message.text()).thenReturn("/duel @opponent 50");

        TelegramUser opponent = new TelegramUser();
        opponent.setId(300L);
        opponent.setUsername("opponent");
        opponent.setPoints(100);
        TelegramUser challenger = new TelegramUser();
        challenger.setId(200L);
        challenger.setUsername("challenger");
        challenger.setPoints(100);

        when(telegramUserRepository.findByUsernameIgnoreCase("opponent")).thenReturn(Optional.of(opponent));
        when(telegramUserRepository.findById(200L)).thenReturn(Optional.of(challenger));
        when(memeModerationRepository.findByOwnerIdAndStatusAndVisibility(
                        200L, ModerationStatus.APPROVED, MemeVisibility.PUBLIC))
                .thenReturn(List.of(new MemeModeration()));
        when(memeModerationRepository.findByOwnerIdAndStatusAndVisibility(
                        300L, ModerationStatus.APPROVED, MemeVisibility.PUBLIC))
                .thenReturn(List.of(new MemeModeration()));

        when(memeBattleRepository.save(any(MemeBattle.class))).thenAnswer(inv -> inv.getArgument(0));

        SendResponse sendResponse = mock(SendResponse.class);
        Message sentMessage = mock(Message.class);
        when(bot.execute(any(SendMessage.class))).thenReturn(sendResponse);
        when(sendResponse.isOk()).thenReturn(true);
        when(sendResponse.message()).thenReturn(sentMessage);
        when(sentMessage.messageId()).thenReturn(999);

        assertNull(duelCommand.handle(update));

        ArgumentCaptor<MemeBattle> battleCaptor = ArgumentCaptor.forClass(MemeBattle.class);
        verify(memeBattleRepository, times(2)).save(battleCaptor.capture());
        MemeBattle saved = battleCaptor.getValue();
        assertEquals(999, saved.getTelegramMessageId());
        assertEquals("PENDING", saved.getStatus());
    }

    @Test
    void handle_Success_BotExecuteFailed() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);
        User user = mock(User.class);

        when(update.message()).thenReturn(message);
        when(message.chat()).thenReturn(chat);
        when(chat.id()).thenReturn(100L);
        when(message.from()).thenReturn(user);
        when(user.id()).thenReturn(200L);
        when(message.text()).thenReturn("/duel @opponent 50");

        TelegramUser opponent = new TelegramUser();
        opponent.setId(300L);
        opponent.setUsername("opponent");
        opponent.setPoints(100);
        TelegramUser challenger = new TelegramUser();
        challenger.setId(200L);
        challenger.setUsername("challenger");
        challenger.setPoints(100);

        when(telegramUserRepository.findByUsernameIgnoreCase("opponent")).thenReturn(Optional.of(opponent));
        when(telegramUserRepository.findById(200L)).thenReturn(Optional.of(challenger));
        when(memeModerationRepository.findByOwnerIdAndStatusAndVisibility(
                        200L, ModerationStatus.APPROVED, MemeVisibility.PUBLIC))
                .thenReturn(List.of(new MemeModeration()));
        when(memeModerationRepository.findByOwnerIdAndStatusAndVisibility(
                        300L, ModerationStatus.APPROVED, MemeVisibility.PUBLIC))
                .thenReturn(List.of(new MemeModeration()));

        when(memeBattleRepository.save(any(MemeBattle.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bot.execute(any(SendMessage.class))).thenReturn(null);

        assertNull(duelCommand.handle(update));

        ArgumentCaptor<MemeBattle> battleCaptor = ArgumentCaptor.forClass(MemeBattle.class);
        verify(memeBattleRepository, times(2)).save(battleCaptor.capture());
        MemeBattle saved = battleCaptor.getValue();
        assertEquals("FAILED", saved.getStatus());
    }
}
