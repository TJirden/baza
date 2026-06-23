package cringe.baza.bot.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.pengrad.telegrambot.model.InlineQuery;
import com.pengrad.telegrambot.model.User;
import com.pengrad.telegrambot.request.AnswerInlineQuery;
import cringe.baza.model.Meme;
import cringe.baza.processor.MemeProcessor;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InlineQueryHandlerTest {

    @Mock
    private TelegramUserService userService;

    @Mock
    private MemeProcessor memeProcessor;

    @InjectMocks
    private InlineQueryHandler handler;

    @Test
    void handle_EmptyQuery_ReturnsEmptyAnswer() {
        InlineQuery inlineQuery = mock(InlineQuery.class);
        when(inlineQuery.query()).thenReturn("");
        when(inlineQuery.id()).thenReturn("query-id");

        AnswerInlineQuery result = handler.handle(inlineQuery);

        assertNotNull(result);
        assertEquals("query-id", result.getParameters().get("inline_query_id"));
    }

    @Test
    void handle_ValidQuery_ReturnsMemes() {
        InlineQuery inlineQuery = mock(InlineQuery.class);
        User user = mock(User.class);
        Meme meme = mock(Meme.class);

        when(inlineQuery.query()).thenReturn("cat");
        when(inlineQuery.id()).thenReturn("query-id");
        when(inlineQuery.from()).thenReturn(user);
        when(user.id()).thenReturn(100L);

        List<Long> groupIds = List.of(1L, 2L);
        when(userService.getUserGroupIds(100L)).thenReturn(groupIds);

        when(meme.id()).thenReturn("meme-1");
        when(meme.fileId()).thenReturn("file-1");
        when(memeProcessor.getMemesByDescription("cat", 50, 100L, groupIds)).thenReturn(List.of(meme));

        AnswerInlineQuery result = handler.handle(inlineQuery);

        assertNotNull(result);
        assertEquals("query-id", result.getParameters().get("inline_query_id"));
    }
}
