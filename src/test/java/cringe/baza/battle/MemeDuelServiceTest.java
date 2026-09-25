package cringe.baza.battle;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import cringe.baza.bot.config.TelegramProperties;
import cringe.baza.bot.model.DuelActionResult;
import cringe.baza.bot.model.DuelCreateResult;
import cringe.baza.bot.service.TelegramService;
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

    @Mock
    private TelegramProperties telegramProperties;

    @InjectMocks
    private MemeDuelService memeDuelService;

    @Test
    void acceptDuel_NotFoundOrInactiveOrUnauthorized() {
        when(memeBattleRepository.findById(1L)).thenReturn(Optional.empty());
        assertEquals(DuelActionResult.NOT_FOUND, memeDuelService.acceptDuel(1L, 20L));

        MemeBattle inactive = new MemeBattle();
        inactive.setStatus("ACTIVE");
        when(memeBattleRepository.findById(1L)).thenReturn(Optional.of(inactive));
        assertEquals(DuelActionResult.INACTIVE, memeDuelService.acceptDuel(1L, 20L));

        MemeBattle unauthorized = new MemeBattle();
        unauthorized.setStatus("PENDING");
        unauthorized.setOpponentId(30L);
        when(memeBattleRepository.findById(1L)).thenReturn(Optional.of(unauthorized));
        assertEquals(DuelActionResult.UNAUTHORIZED, memeDuelService.acceptDuel(1L, 20L));
    }

    @Test
    void acceptDuel_UserNotFound() {
        MemeBattle battle = new MemeBattle();
        battle.setStatus("PENDING");
        battle.setChallengerId(10L);
        battle.setOpponentId(20L);

        when(memeBattleRepository.findById(1L)).thenReturn(Optional.of(battle));
        when(telegramUserRepository.findById(10L)).thenReturn(Optional.empty());

        assertEquals(DuelActionResult.ERROR, memeDuelService.acceptDuel(1L, 20L));
    }

    @Test
    void acceptDuel_ChargesOnlyOpponent() {
        MemeBattle battle = new MemeBattle();
        battle.setId(1L);
        battle.setStatus("PENDING");
        battle.setChallengerId(10L);
        battle.setOpponentId(20L);
        battle.setBet(50);
        battle.setTelegramChatId(123L);
        battle.setTelegramMessageId(456);

        TelegramUser challenger = new TelegramUser();
        challenger.setId(10L);
        challenger.setFirstName("Challenger");
        challenger.setPoints(10);

        TelegramUser opponent = new TelegramUser();
        opponent.setId(20L);
        opponent.setFirstName("Opponent");
        opponent.setPoints(100);

        when(memeBattleRepository.findById(1L)).thenReturn(Optional.of(battle));
        when(telegramUserRepository.findById(10L)).thenReturn(Optional.of(challenger));
        when(telegramUserRepository.findById(20L)).thenReturn(Optional.of(opponent));

        MemeModeration meme = new MemeModeration(
                "meme-1",
                "file-1",
                "description",
                "ocr",
                10L,
                MemeVisibility.PUBLIC,
                "tags",
                ModerationStatus.APPROVED,
                null);
        when(memeModerationRepository.findByOwnerIdAndStatusAndVisibility(
                        anyLong(), eq(ModerationStatus.APPROVED), eq(MemeVisibility.PUBLIC)))
                .thenReturn(List.of(meme));

        assertEquals(DuelActionResult.SUCCESS, memeDuelService.acceptDuel(1L, 20L));
        assertEquals(10, challenger.getPoints());
        assertEquals(50, opponent.getPoints());
        verify(telegramUserRepository, never()).save(challenger);
        verify(telegramUserRepository).save(opponent);
    }

    @Test
    void acceptDuel_OpponentInsufficientPoints() {
        MemeBattle battle = new MemeBattle();
        battle.setStatus("PENDING");
        battle.setChallengerId(10L);
        battle.setOpponentId(20L);
        battle.setBet(50);

        TelegramUser challenger = new TelegramUser();
        challenger.setId(10L);
        challenger.setPoints(100);

        TelegramUser opponent = new TelegramUser();
        opponent.setId(20L);
        opponent.setPoints(10);

        when(memeBattleRepository.findById(1L)).thenReturn(Optional.of(battle));
        when(telegramUserRepository.findById(10L)).thenReturn(Optional.of(challenger));
        when(telegramUserRepository.findById(20L)).thenReturn(Optional.of(opponent));

        assertEquals(DuelActionResult.OPPONENT_INSUFFICIENT_POINTS, memeDuelService.acceptDuel(1L, 20L));
    }

    @Test
    void acceptDuel_SuccessAndMemeSelectionTriggered() {
        MemeBattle battle = new MemeBattle();
        battle.setId(1L);
        battle.setStatus("PENDING");
        battle.setChallengerId(10L);
        battle.setOpponentId(20L);
        battle.setBet(50);
        battle.setTelegramChatId(123L);
        battle.setTelegramMessageId(456);

        TelegramUser challenger = new TelegramUser();
        challenger.setId(10L);
        challenger.setFirstName("Challenger");
        challenger.setPoints(100);

        TelegramUser opponent = new TelegramUser();
        opponent.setId(20L);
        opponent.setFirstName("Opponent");
        opponent.setPoints(100);

        when(memeBattleRepository.findById(1L)).thenReturn(Optional.of(battle));
        when(telegramUserRepository.findById(10L)).thenReturn(Optional.of(challenger));
        when(telegramUserRepository.findById(20L)).thenReturn(Optional.of(opponent));

        MemeModeration meme = new MemeModeration(
                "meme-1",
                "file-1",
                "description",
                "ocr",
                10L,
                MemeVisibility.PUBLIC,
                "tags",
                ModerationStatus.APPROVED,
                null);
        when(memeModerationRepository.findByOwnerIdAndStatusAndVisibility(
                        anyLong(), eq(ModerationStatus.APPROVED), eq(MemeVisibility.PUBLIC)))
                .thenReturn(List.of(meme));

        assertEquals(DuelActionResult.SUCCESS, memeDuelService.acceptDuel(1L, 20L));
    }

    @Test
    void declineDuel_NotFoundOrInactiveOrUnauthorized() {
        when(memeBattleRepository.findById(1L)).thenReturn(Optional.empty());
        assertEquals(DuelActionResult.NOT_FOUND, memeDuelService.declineDuel(1L, 20L));

        MemeBattle inactive = new MemeBattle();
        inactive.setStatus("ACTIVE");
        when(memeBattleRepository.findById(1L)).thenReturn(Optional.of(inactive));
        assertEquals(DuelActionResult.INACTIVE, memeDuelService.declineDuel(1L, 20L));

        MemeBattle unauthorized = new MemeBattle();
        unauthorized.setStatus("PENDING");
        unauthorized.setChallengerId(10L);
        unauthorized.setOpponentId(20L);
        when(memeBattleRepository.findById(1L)).thenReturn(Optional.of(unauthorized));
        assertEquals(DuelActionResult.UNAUTHORIZED, memeDuelService.declineDuel(1L, 30L));
    }

    @Test
    void declineDuel_SuccessByOpponentAndChallenger() {
        MemeBattle battle1 = new MemeBattle();
        battle1.setStatus("PENDING");
        battle1.setChallengerId(10L);
        battle1.setOpponentId(20L);
        battle1.setTelegramChatId(123L);
        battle1.setTelegramMessageId(456);

        TelegramUser opponent = new TelegramUser();
        opponent.setId(20L);
        opponent.setFirstName("Opponent");

        when(memeBattleRepository.findById(1L)).thenReturn(Optional.of(battle1));
        when(telegramUserRepository.findById(20L)).thenReturn(Optional.of(opponent));

        assertEquals(DuelActionResult.SUCCESS, memeDuelService.declineDuel(1L, 20L));

        MemeBattle battle2 = new MemeBattle();
        battle2.setStatus("PENDING");
        battle2.setChallengerId(10L);
        battle2.setOpponentId(20L);
        battle2.setTelegramChatId(123L);
        battle2.setTelegramMessageId(456);

        TelegramUser challenger = new TelegramUser();
        challenger.setId(10L);
        challenger.setFirstName("Challenger");

        when(memeBattleRepository.findById(2L)).thenReturn(Optional.of(battle2));
        when(telegramUserRepository.findById(10L)).thenReturn(Optional.of(challenger));

        assertEquals(DuelActionResult.SUCCESS, memeDuelService.declineDuel(2L, 10L));
    }

    @Test
    void declineDuel_RefundsChallenger() {
        MemeBattle battle = new MemeBattle();
        battle.setStatus("PENDING");
        battle.setChallengerId(10L);
        battle.setOpponentId(20L);
        battle.setBet(50);
        battle.setTelegramChatId(123L);
        battle.setTelegramMessageId(456);

        TelegramUser challenger = new TelegramUser();
        challenger.setId(10L);
        challenger.setFirstName("Challenger");
        challenger.setPoints(50);

        TelegramUser opponent = new TelegramUser();
        opponent.setId(20L);
        opponent.setFirstName("Opponent");
        opponent.setPoints(100);

        when(memeBattleRepository.findById(1L)).thenReturn(Optional.of(battle));
        when(telegramUserRepository.findById(10L)).thenReturn(Optional.of(challenger));
        when(telegramUserRepository.findById(20L)).thenReturn(Optional.of(opponent));

        assertEquals(DuelActionResult.SUCCESS, memeDuelService.declineDuel(1L, 20L));
        assertEquals(100, challenger.getPoints());
        assertEquals(100, opponent.getPoints());
        verify(telegramUserRepository).save(challenger);
        verify(telegramUserRepository, never()).save(opponent);
    }

    @Test
    void createDuel_ChargesChallengerAndSendsChallenge() {
        TelegramUser challenger = new TelegramUser();
        challenger.setId(200L);
        challenger.setUsername("challenger");
        challenger.setPoints(100);

        TelegramUser opponent = new TelegramUser();
        opponent.setId(300L);
        opponent.setUsername("opponent");
        opponent.setPoints(100);

        when(telegramUserRepository.findByUsernameIgnoreCase("opponent")).thenReturn(Optional.of(opponent));
        when(telegramUserRepository.findById(200L)).thenReturn(Optional.of(challenger));
        when(memeModerationRepository.findByOwnerIdAndStatusAndVisibility(
                        anyLong(), eq(ModerationStatus.APPROVED), eq(MemeVisibility.PUBLIC)))
                .thenReturn(List.of(new MemeModeration()));
        when(memeBattleRepository.save(any(MemeBattle.class))).thenAnswer(inv -> {
            MemeBattle b = inv.getArgument(0);
            b.setId(1L);
            return b;
        });
        when(telegramProperties.getBotUsername()).thenReturn("test_bot");
        when(telegramService.sendDuelChallenge(eq(100L), anyString(), anyLong()))
                .thenReturn(999);

        assertEquals(DuelCreateResult.SUCCESS, memeDuelService.createDuel(200L, "opponent", 50, 100L));

        assertEquals(50, challenger.getPoints());
        verify(telegramUserRepository).save(challenger);
        ArgumentCaptor<MemeBattle> battleCaptor = ArgumentCaptor.forClass(MemeBattle.class);
        verify(memeBattleRepository, times(2)).save(battleCaptor.capture());
        MemeBattle saved = battleCaptor.getValue();
        assertEquals("PENDING", saved.getStatus());
        assertEquals(999, saved.getTelegramMessageId());
    }

    @Test
    void createDuel_SendFailure_FailsBattleAndRefundsChallenger() {
        TelegramUser challenger = new TelegramUser();
        challenger.setId(200L);
        challenger.setUsername("challenger");
        challenger.setPoints(100);

        TelegramUser opponent = new TelegramUser();
        opponent.setId(300L);
        opponent.setUsername("opponent");
        opponent.setPoints(100);

        when(telegramUserRepository.findByUsernameIgnoreCase("opponent")).thenReturn(Optional.of(opponent));
        when(telegramUserRepository.findById(200L)).thenReturn(Optional.of(challenger));
        when(memeModerationRepository.findByOwnerIdAndStatusAndVisibility(
                        anyLong(), eq(ModerationStatus.APPROVED), eq(MemeVisibility.PUBLIC)))
                .thenReturn(List.of(new MemeModeration()));
        when(memeBattleRepository.save(any(MemeBattle.class))).thenAnswer(inv -> {
            MemeBattle b = inv.getArgument(0);
            b.setId(1L);
            return b;
        });
        when(telegramProperties.getBotUsername()).thenReturn("test_bot");
        when(telegramService.sendDuelChallenge(eq(100L), anyString(), anyLong()))
                .thenReturn(null);

        assertEquals(DuelCreateResult.ERROR, memeDuelService.createDuel(200L, "opponent", 50, 100L));

        assertEquals(100, challenger.getPoints());
        ArgumentCaptor<MemeBattle> battleCaptor = ArgumentCaptor.forClass(MemeBattle.class);
        verify(memeBattleRepository, times(2)).save(battleCaptor.capture());
        assertEquals("FAILED", battleCaptor.getValue().getStatus());
    }

    @Test
    void createDuel_ValidationFailures() {
        when(telegramUserRepository.findByUsernameIgnoreCase("opponent")).thenReturn(Optional.empty());
        assertEquals(DuelCreateResult.OPPONENT_NOT_FOUND, memeDuelService.createDuel(200L, "opponent", 50, 100L));

        TelegramUser self = new TelegramUser();
        self.setId(200L);
        when(telegramUserRepository.findByUsernameIgnoreCase("self")).thenReturn(Optional.of(self));
        assertEquals(DuelCreateResult.SELF_DUEL, memeDuelService.createDuel(200L, "self", 50, 100L));

        TelegramUser opponent = new TelegramUser();
        opponent.setId(300L);
        opponent.setPoints(100);
        TelegramUser poorChallenger = new TelegramUser();
        poorChallenger.setId(200L);
        poorChallenger.setPoints(10);
        when(telegramUserRepository.findByUsernameIgnoreCase("opponent")).thenReturn(Optional.of(opponent));
        when(telegramUserRepository.findById(200L)).thenReturn(Optional.of(poorChallenger));
        assertEquals(
                DuelCreateResult.CHALLENGER_INSUFFICIENT_POINTS,
                memeDuelService.createDuel(200L, "opponent", 50, 100L));

        TelegramUser richChallenger = new TelegramUser();
        richChallenger.setId(200L);
        richChallenger.setPoints(100);
        TelegramUser poorOpponent = new TelegramUser();
        poorOpponent.setId(300L);
        poorOpponent.setPoints(10);
        when(telegramUserRepository.findByUsernameIgnoreCase("poor")).thenReturn(Optional.of(poorOpponent));
        when(telegramUserRepository.findById(200L)).thenReturn(Optional.of(richChallenger));
        assertEquals(DuelCreateResult.OPPONENT_INSUFFICIENT_POINTS, memeDuelService.createDuel(200L, "poor", 50, 100L));

        TelegramUser richOpponent = new TelegramUser();
        richOpponent.setId(300L);
        richOpponent.setPoints(100);
        when(telegramUserRepository.findByUsernameIgnoreCase("rich")).thenReturn(Optional.of(richOpponent));
        when(memeModerationRepository.findByOwnerIdAndStatusAndVisibility(
                        eq(200L), eq(ModerationStatus.APPROVED), eq(MemeVisibility.PUBLIC)))
                .thenReturn(List.of());
        assertEquals(DuelCreateResult.CHALLENGER_NO_MEMES, memeDuelService.createDuel(200L, "rich", 50, 100L));

        when(memeModerationRepository.findByOwnerIdAndStatusAndVisibility(
                        eq(200L), eq(ModerationStatus.APPROVED), eq(MemeVisibility.PUBLIC)))
                .thenReturn(List.of(new MemeModeration()));
        when(memeModerationRepository.findByOwnerIdAndStatusAndVisibility(
                        eq(300L), eq(ModerationStatus.APPROVED), eq(MemeVisibility.PUBLIC)))
                .thenReturn(List.of());
        assertEquals(DuelCreateResult.OPPONENT_NO_MEMES, memeDuelService.createDuel(200L, "rich", 50, 100L));

        verify(memeBattleRepository, never()).save(any());
    }

    @Test
    void selectDuelMeme_NotFoundOrInactiveOrUnauthorizedOrMemeNotFound() {
        when(memeBattleRepository.findById(1L)).thenReturn(Optional.empty());
        assertEquals(DuelActionResult.NOT_FOUND, memeDuelService.selectDuelMeme(1L, 10L, "meme-1"));

        MemeBattle inactive = new MemeBattle();
        inactive.setStatus("ACTIVE");
        when(memeBattleRepository.findById(1L)).thenReturn(Optional.of(inactive));
        assertEquals(DuelActionResult.INACTIVE, memeDuelService.selectDuelMeme(1L, 10L, "meme-1"));

        MemeBattle unauthorized = new MemeBattle();
        unauthorized.setStatus("MEME_SELECTION");
        unauthorized.setChallengerId(10L);
        unauthorized.setOpponentId(20L);
        when(memeBattleRepository.findById(1L)).thenReturn(Optional.of(unauthorized));
        assertEquals(DuelActionResult.UNAUTHORIZED, memeDuelService.selectDuelMeme(1L, 30L, "meme-1"));

        when(memeModerationRepository.findById("meme-1")).thenReturn(Optional.empty());
        assertEquals(DuelActionResult.MEME_NOT_FOUND, memeDuelService.selectDuelMeme(1L, 10L, "meme-1"));
    }

    @Test
    void selectDuelMeme_ChallengerAndOpponentSelection() {
        MemeBattle battle = new MemeBattle();
        battle.setId(1L);
        battle.setStatus("MEME_SELECTION");
        battle.setChallengerId(10L);
        battle.setOpponentId(20L);

        when(memeBattleRepository.findById(1L)).thenReturn(Optional.of(battle));
        when(memeModerationRepository.findById("meme-1")).thenReturn(Optional.of(new MemeModeration()));

        assertEquals(DuelActionResult.SUCCESS, memeDuelService.selectDuelMeme(1L, 10L, "meme-1"));
        assertEquals("meme-1", battle.getMemeAId());
        assertTrue(battle.getChallengerMemeSelected());

        assertEquals(DuelActionResult.ALREADY_SELECTED, memeDuelService.selectDuelMeme(1L, 10L, "meme-1"));

        assertEquals(DuelActionResult.SUCCESS, memeDuelService.selectDuelMeme(1L, 20L, "meme-1"));
        assertEquals("meme-1", battle.getMemeBId());
        assertTrue(battle.getOpponentMemeSelected());

        verify(memeDuelLifecycleService).startActiveDuel(battle);
    }
}
