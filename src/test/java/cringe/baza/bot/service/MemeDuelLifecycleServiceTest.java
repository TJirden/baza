package cringe.baza.bot.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import cringe.baza.domain.MemeBattle;
import cringe.baza.domain.TelegramUser;
import cringe.baza.repository.jpa.MemeBattleRepository;
import cringe.baza.repository.jpa.MemeModerationRepository;
import cringe.baza.repository.jpa.TelegramUserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MemeDuelLifecycleServiceTest {

    @Mock
    private TelegramService telegramService;

    @Mock
    private MemeModerationRepository memeModerationRepository;

    @Mock
    private MemeBattleRepository memeBattleRepository;

    @Mock
    private TelegramUserRepository telegramUserRepository;

    @InjectMocks
    private MemeDuelLifecycleService memeDuelLifecycleService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(memeDuelLifecycleService, "battleDurationMinutes", 10);
    }

    @Test
    void completeDuel_ChallengerWins_EloAndStakesRewards() {
        MemeBattle battle = new MemeBattle();
        battle.setId(77L);
        battle.setStatus("ACTIVE");
        battle.setBattleType("DUEL");
        battle.setChallengerId(111L);
        battle.setOpponentId(222L);
        battle.setBet(50);
        battle.setMemeAId("meme-A");
        battle.setMemeBId("meme-B");
        battle.setVotesA(15);
        battle.setVotesB(5);
        battle.setTelegramChatId(12345L);
        battle.setTelegramMessageId(67890);

        TelegramUser challenger = new TelegramUser(111L, "challenger", "Challenger", 0, 100, new java.util.HashSet<>());
        TelegramUser opponent = new TelegramUser(222L, "opponent", "Opponent", 0, 100, new java.util.HashSet<>());

        when(telegramUserRepository.findById(111L)).thenReturn(Optional.of(challenger));
        when(telegramUserRepository.findById(222L)).thenReturn(Optional.of(opponent));

        memeDuelLifecycleService.completeDuel(battle, "meme-A", 50);

        assertEquals(210, challenger.getPoints());
        assertEquals(1, challenger.getBattleWins());
        assertEquals(100, opponent.getPoints());

        verify(telegramUserRepository).save(challenger);
        verify(telegramUserRepository).save(opponent);
    }

    @Test
    void completeDuel_Draw_StakesRefunded() {
        MemeBattle battle = new MemeBattle();
        battle.setId(78L);
        battle.setStatus("ACTIVE");
        battle.setBattleType("DUEL");
        battle.setChallengerId(111L);
        battle.setOpponentId(222L);
        battle.setBet(50);
        battle.setMemeAId("meme-A");
        battle.setMemeBId("meme-B");
        battle.setVotesA(5);
        battle.setVotesB(5);
        battle.setTelegramChatId(12345L);
        battle.setTelegramMessageId(67890);

        TelegramUser challenger = new TelegramUser(111L, "challenger", "Challenger", 0, 100, new java.util.HashSet<>());
        TelegramUser opponent = new TelegramUser(222L, "opponent", "Opponent", 0, 100, new java.util.HashSet<>());

        when(telegramUserRepository.findById(111L)).thenReturn(Optional.of(challenger));
        when(telegramUserRepository.findById(222L)).thenReturn(Optional.of(opponent));

        memeDuelLifecycleService.completeDuel(battle, null, 50);

        assertEquals(150, challenger.getPoints());
        assertEquals(150, opponent.getPoints());

        verify(telegramUserRepository).save(challenger);
        verify(telegramUserRepository).save(opponent);
    }
}
