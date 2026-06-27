package cringe.baza.bot.service;

import cringe.baza.meme.SwipeService;
import cringe.baza.meme.MemeModerationService;
import cringe.baza.battle.MemeBattleService;
import cringe.baza.user.UserSessionService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.CallbackQuery;
import com.pengrad.telegrambot.model.User;
import com.pengrad.telegrambot.request.AnswerCallbackQuery;
import cringe.baza.model.ReportStatus;
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
    private SwipeService swipeService;

    @Mock
    private UserSessionService sessionService;

    @Mock
    private TelegramBot bot;

    @InjectMocks
    private CallbackQueryHandler handler;

    @Test
    void handle_ReportCallback_ReturnsAnswer() {
        CallbackQuery callbackQuery = mock(CallbackQuery.class);
        User user = mock(User.class);

        when(callbackQuery.data()).thenReturn("report:meme-123");
        when(callbackQuery.from()).thenReturn(user);
        when(user.id()).thenReturn(555L);
        when(callbackQuery.id()).thenReturn("cb-1");

        when(moderationService.reportMeme("meme-123", 555L))
                .thenReturn(new MemeModerationService.ReportResult(ReportStatus.REPORT_ADDED, 1L));

        AnswerCallbackQuery result = (AnswerCallbackQuery) handler.handle(callbackQuery);

        assertNotNull(result);
        assertEquals("cb-1", result.getParameters().get("callback_query_id"));
        verify(moderationService).reportMeme("meme-123", 555L);
    }
}
