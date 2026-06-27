package cringe.baza.bot.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.CallbackQuery;
import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.User;
import com.pengrad.telegrambot.request.AnswerCallbackQuery;
import cringe.baza.battle.MemeBattleService;
import cringe.baza.battle.MemeDuelService;
import cringe.baza.bot.model.DuelActionResult;
import cringe.baza.bot.model.UserState;
import cringe.baza.meme.MemeModerationService;
import cringe.baza.meme.SwipeService;
import cringe.baza.model.ReportStatus;
import cringe.baza.user.UserSessionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CallbackQueryHandlerTest {

    @Mock
    private MemeModerationService moderationService;

    @Mock
    private MemeBattleService memeBattleService;

    @Mock
    private MemeDuelService memeDuelService;

    @Mock
    private SwipeService swipeService;

    @Mock
    private UserSessionService sessionService;

    @Mock
    private TelegramBot bot;

    @InjectMocks
    private CallbackQueryHandler handler;

    @Test
    void handle_ReportCallback_VariousStatuses() {
        CallbackQuery query = mock(CallbackQuery.class);
        User user = mock(User.class);
        when(query.from()).thenReturn(user);
        when(user.id()).thenReturn(555L);
        when(query.id()).thenReturn("cb-id");

        when(query.data()).thenReturn("report:meme-1");
        when(moderationService.reportMeme("meme-1", 555L))
                .thenReturn(new MemeModerationService.ReportResult(ReportStatus.NOT_FOUND, 0));
        AnswerCallbackQuery r1 = (AnswerCallbackQuery) handler.handle(query);
        assertTrue((Boolean) r1.getParameters().get("show_alert"));

        when(moderationService.reportMeme("meme-1", 555L))
                .thenReturn(new MemeModerationService.ReportResult(ReportStatus.ALREADY_REPORTED, 0));
        AnswerCallbackQuery r2 = (AnswerCallbackQuery) handler.handle(query);
        assertTrue((Boolean) r2.getParameters().get("show_alert"));

        when(moderationService.reportMeme("meme-1", 555L))
                .thenReturn(new MemeModerationService.ReportResult(ReportStatus.QUARANTINED, 0));
        AnswerCallbackQuery r3 = (AnswerCallbackQuery) handler.handle(query);
        assertTrue((Boolean) r3.getParameters().get("show_alert"));

        when(moderationService.reportMeme("meme-1", 555L))
                .thenReturn(new MemeModerationService.ReportResult(ReportStatus.REPORT_ADDED, 2));
        AnswerCallbackQuery r4 = (AnswerCallbackQuery) handler.handle(query);
        assertTrue((Boolean) r4.getParameters().get("show_alert"));
    }

    @Test
    void handle_VoteCallback_SuccessAndFailureAndException() {
        CallbackQuery query = mock(CallbackQuery.class);
        User user = mock(User.class);
        when(query.from()).thenReturn(user);
        when(user.id()).thenReturn(555L);
        when(query.id()).thenReturn("cb-id");

        when(query.data()).thenReturn("vote:100:A");
        when(memeBattleService.registerVote(100L, 555L, "A")).thenReturn(true);
        AnswerCallbackQuery r1 = (AnswerCallbackQuery) handler.handle(query);
        assertFalse(r1.getParameters().containsKey("show_alert"));

        when(memeBattleService.registerVote(100L, 555L, "A")).thenReturn(false);
        AnswerCallbackQuery r2 = (AnswerCallbackQuery) handler.handle(query);
        assertTrue((Boolean) r2.getParameters().get("show_alert"));

        when(memeBattleService.registerVote(100L, 555L, "A")).thenThrow(new RuntimeException("Oops"));
        AnswerCallbackQuery r3 = (AnswerCallbackQuery) handler.handle(query);
        assertTrue((Boolean) r3.getParameters().get("show_alert"));
    }

    @Test
    void handle_DuelAcceptCallback() {
        CallbackQuery query = mock(CallbackQuery.class);
        User user = mock(User.class);
        when(query.from()).thenReturn(user);
        when(user.id()).thenReturn(555L);
        when(query.id()).thenReturn("cb-id");

        when(query.data()).thenReturn("duel_accept:100");
        when(memeDuelService.acceptDuel(100L, 555L)).thenReturn(DuelActionResult.SUCCESS);
        AnswerCallbackQuery r1 = (AnswerCallbackQuery) handler.handle(query);
        assertNull(r1.getParameters().get("show_alert"));

        when(memeDuelService.acceptDuel(100L, 555L)).thenReturn(DuelActionResult.OPPONENT_INSUFFICIENT_POINTS);
        AnswerCallbackQuery r2 = (AnswerCallbackQuery) handler.handle(query);
        assertTrue((Boolean) r2.getParameters().get("show_alert"));

        when(memeDuelService.acceptDuel(100L, 555L)).thenThrow(new RuntimeException("Oops"));
        AnswerCallbackQuery r3 = (AnswerCallbackQuery) handler.handle(query);
        assertTrue((Boolean) r3.getParameters().get("show_alert"));
    }

    @Test
    void handle_DuelDeclineCallback() {
        CallbackQuery query = mock(CallbackQuery.class);
        User user = mock(User.class);
        when(query.from()).thenReturn(user);
        when(user.id()).thenReturn(555L);
        when(query.id()).thenReturn("cb-id");

        when(query.data()).thenReturn("duel_decline:100");
        when(memeDuelService.declineDuel(100L, 555L)).thenReturn(DuelActionResult.SUCCESS);
        AnswerCallbackQuery r1 = (AnswerCallbackQuery) handler.handle(query);
        assertNull(r1.getParameters().get("show_alert"));

        when(memeDuelService.declineDuel(100L, 555L)).thenReturn(DuelActionResult.UNAUTHORIZED);
        AnswerCallbackQuery r2 = (AnswerCallbackQuery) handler.handle(query);
        assertTrue((Boolean) r2.getParameters().get("show_alert"));

        when(memeDuelService.declineDuel(100L, 555L)).thenThrow(new RuntimeException("Oops"));
        AnswerCallbackQuery r3 = (AnswerCallbackQuery) handler.handle(query);
        assertTrue((Boolean) r3.getParameters().get("show_alert"));
    }

    @Test
    void handle_DuelSelectCallback() {
        CallbackQuery query = mock(CallbackQuery.class);
        User user = mock(User.class);
        when(query.from()).thenReturn(user);
        when(user.id()).thenReturn(555L);
        when(query.id()).thenReturn("cb-id");

        when(query.data()).thenReturn("duel_select:100:meme-123");
        when(memeDuelService.selectDuelMeme(100L, 555L, "meme-123")).thenReturn(DuelActionResult.SUCCESS);
        AnswerCallbackQuery r1 = (AnswerCallbackQuery) handler.handle(query);
        assertNull(r1.getParameters().get("show_alert"));

        when(memeDuelService.selectDuelMeme(100L, 555L, "meme-123")).thenReturn(DuelActionResult.MEME_NOT_FOUND);
        AnswerCallbackQuery r2 = (AnswerCallbackQuery) handler.handle(query);
        assertTrue((Boolean) r2.getParameters().get("show_alert"));

        when(memeDuelService.selectDuelMeme(100L, 555L, "meme-123")).thenThrow(new RuntimeException("Oops"));
        AnswerCallbackQuery r3 = (AnswerCallbackQuery) handler.handle(query);
        assertTrue((Boolean) r3.getParameters().get("show_alert"));
    }

    @Test
    void handle_SwipeVoteCallback() {
        CallbackQuery query = mock(CallbackQuery.class);
        User user = mock(User.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);

        when(query.from()).thenReturn(user);
        when(user.id()).thenReturn(555L);
        when(query.id()).thenReturn("cb-id");
        when(query.message()).thenReturn(message);
        when(message.chat()).thenReturn(chat);
        when(chat.id()).thenReturn(999L);

        when(query.data()).thenReturn("swipe_vote:meme-123:BASE");
        when(sessionService.getUserState(999L)).thenReturn(UserState.DEFAULT);
        AnswerCallbackQuery r1 = (AnswerCallbackQuery) handler.handle(query);
        assertTrue((Boolean) r1.getParameters().get("show_alert"));

        when(sessionService.getUserState(999L)).thenReturn(UserState.SWIPING);
        when(message.caption()).thenReturn("Original caption");
        when(message.messageId()).thenReturn(111);

        AnswerCallbackQuery r2 = (AnswerCallbackQuery) handler.handle(query);
        assertNull(r2.getParameters().get("show_alert"));
        verify(swipeService).registerSwipeVote(555L, "meme-123", "BASE");
        verify(bot).execute(any());

        when(sessionService.getUserState(999L)).thenThrow(new RuntimeException("Oops"));
        AnswerCallbackQuery r3 = (AnswerCallbackQuery) handler.handle(query);
        assertTrue((Boolean) r3.getParameters().get("show_alert"));
    }

    @Test
    void handle_SwipeStopCallback() {
        CallbackQuery query = mock(CallbackQuery.class);
        User user = mock(User.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);

        when(query.from()).thenReturn(user);
        when(user.id()).thenReturn(555L);
        when(query.id()).thenReturn("cb-id");
        when(query.message()).thenReturn(message);
        when(message.chat()).thenReturn(chat);
        when(chat.id()).thenReturn(999L);

        when(query.data()).thenReturn("swipe_stop");
        when(message.caption()).thenReturn(null);
        when(message.messageId()).thenReturn(111);

        AnswerCallbackQuery r1 = (AnswerCallbackQuery) handler.handle(query);
        assertNull(r1.getParameters().get("show_alert"));
        verify(sessionService).setUserState(999L, UserState.DEFAULT);
        verify(bot).execute(any());

        doThrow(new RuntimeException("Oops")).when(sessionService).setUserState(999L, UserState.DEFAULT);
        AnswerCallbackQuery r2 = (AnswerCallbackQuery) handler.handle(query);
        assertTrue((Boolean) r2.getParameters().get("show_alert"));
    }

    @Test
    void handle_DefaultCallback() {
        CallbackQuery query = mock(CallbackQuery.class);
        when(query.data()).thenReturn("other_data");
        when(query.id()).thenReturn("cb-id");

        AnswerCallbackQuery result = (AnswerCallbackQuery) handler.handle(query);
        assertNotNull(result);
    }
}
