package cringe.baza.bot.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.BaseRequest;
import com.pengrad.telegrambot.request.SendMessage;
import cringe.baza.bot.model.UserState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SwipingStateHandlerTest {

    @Mock
    private UserSessionService sessionService;

    @Mock
    private CommandRouter commandRouter;

    @InjectMocks
    private SwipingStateHandler handler;

    @Test
    void handle_NonCommandMessage_ReturnsSwipingReminder() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);

        when(update.message()).thenReturn(message);
        when(message.text()).thenReturn("some text");

        SendMessage result = (SendMessage) handler.handle(update, 100L);

        assertNotNull(result);
        assertEquals(
                "Вы находитесь в режиме оценки мемов. Нажимайте кнопки под картинкой или напишите /cancel для выхода.",
                result.getParameters().get("text"));
        verifyNoInteractions(sessionService);
        verifyNoInteractions(commandRouter);
    }

    @Test
    void handle_CancelCommand_ResetsStateAndConfirms() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);

        when(update.message()).thenReturn(message);
        when(message.text()).thenReturn("/cancel");

        SendMessage result = (SendMessage) handler.handle(update, 100L);

        assertNotNull(result);
        assertEquals("Режим оценки завершен.", result.getParameters().get("text"));
        verify(sessionService).setUserState(100L, UserState.DEFAULT);
        verifyNoInteractions(commandRouter);
    }

    @Test
    void handle_OtherCommand_ResetsStateAndDelegatesToRouter() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        BaseRequest<?, ?> expected = mock(BaseRequest.class);

        when(update.message()).thenReturn(message);
        when(message.text()).thenReturn("/save");
        when(commandRouter.route(update)).thenAnswer(inv -> expected);

        BaseRequest<?, ?> result = handler.handle(update, 100L);

        assertEquals(expected, result);
        verify(sessionService).setUserState(100L, UserState.DEFAULT);
        verify(commandRouter).route(update);
    }
}
