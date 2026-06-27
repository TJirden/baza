package cringe.baza.battle;

import cringe.baza.bot.service.TelegramService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import cringe.baza.bot.model.DuelActionResult;
import cringe.baza.domain.MemeBattle;
import cringe.baza.domain.TelegramUser;
import cringe.baza.repository.jpa.MemeBattleRepository;
import cringe.baza.repository.jpa.MemeModerationRepository;
import cringe.baza.repository.jpa.TelegramUserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MemeDuelServiceTest {

    @Mock
    private TelegramService telegramService;

    @Mock
    private MemeModerationRepository memeModerationRepository;

    @Mock
    private MemeBattleRepository memeBattleRepository;

    @Mock
    private TelegramUserRepository telegramUserRepository;

    @Mock
    private MemeDuelLifecycleService memeDuelLifecycleService;

    @InjectMocks
    private MemeDuelService memeDuelService;

    @Test
    void acceptDuel_Success() {
        MemeBattle battle = new MemeBattle();
        battle.setId(1L);
        battle.setStatus("PENDING");
        battle.setChallengerId(10L);
        battle.setOpponentId(20L);
        battle.setBet(10);
        battle.setTelegramChatId(100L);
        battle.setTelegramMessageId(200);

        TelegramUser challenger = new TelegramUser();
        challenger.setId(10L);
        challenger.setPoints(20);

        TelegramUser opponent = new TelegramUser();
        opponent.setId(20L);
        opponent.setPoints(20);

        when(memeBattleRepository.findById(1L)).thenReturn(Optional.of(battle));
        when(telegramUserRepository.findById(10L)).thenReturn(Optional.of(challenger));
        when(telegramUserRepository.findById(20L)).thenReturn(Optional.of(opponent));

        DuelActionResult result = memeDuelService.acceptDuel(1L, 20L);

        assertEquals(DuelActionResult.SUCCESS, result);
        assertEquals("MEME_SELECTION", battle.getStatus());
        assertEquals(10, challenger.getPoints());
        assertEquals(10, opponent.getPoints());
        verify(telegramUserRepository).save(challenger);
        verify(telegramUserRepository).save(opponent);
        verify(memeBattleRepository).save(battle);
    }

    @Test
    void declineDuel_Success() {
        MemeBattle battle = new MemeBattle();
        battle.setId(1L);
        battle.setStatus("PENDING");
        battle.setChallengerId(10L);
        battle.setOpponentId(20L);
        battle.setTelegramChatId(100L);
        battle.setTelegramMessageId(200);

        TelegramUser opponent = new TelegramUser();
        opponent.setId(20L);
        opponent.setFirstName("Opponent");

        when(memeBattleRepository.findById(1L)).thenReturn(Optional.of(battle));
        when(telegramUserRepository.findById(20L)).thenReturn(Optional.of(opponent));

        DuelActionResult result = memeDuelService.declineDuel(1L, 20L);

        assertEquals(DuelActionResult.SUCCESS, result);
        assertEquals("DECLINED", battle.getStatus());
        verify(memeBattleRepository).save(battle);
    }
}
