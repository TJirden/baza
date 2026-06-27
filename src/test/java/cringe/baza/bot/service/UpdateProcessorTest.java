package cringe.baza.bot.service;

import cringe.baza.user.TelegramUserService;
import cringe.baza.user.UserSessionService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.pengrad.telegrambot.model.CallbackQuery;
import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.InlineQuery;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.User;
import com.pengrad.telegrambot.request.AnswerInlineQuery;
import com.pengrad.telegrambot.request.BaseRequest;
import com.pengrad.telegrambot.request.SendMessage;
import cringe.baza.bot.model.UserState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UpdateProcessorTest {

    @Mock
    private UserSessionService sessionService;

    @Mock
    private TelegramUserService userService;

    @Mock
    private CallbackQueryHandler callbackQueryHandler;

    @Mock
    private InlineQueryHandler inlineQueryHandler;

    @Mock
    private CommandRouter commandRouter;

    @Mock
    private AwaitingSaveStateHandler awaitingSaveStateHandler;

    @Mock
    private SwipingStateHandler swipingStateHandler;

    @InjectMocks
    private UpdateProcessor updateProcessor;

    @Test
    void processUpdate_CallbackQuery_DelegatesToCallbackHandler() {
        Update update = mock(Update.class);
        CallbackQuery callbackQuery = mock(CallbackQuery.class);
        User user = mock(User.class);
        BaseRequest<?, ?> expectedResponse = mock(BaseRequest.class);

        when(update.callbackQuery()).thenReturn(callbackQuery);
        when(callbackQuery.from()).thenReturn(user);
        when(user.id()).thenReturn(123L);
        when(user.username()).thenReturn("username");
        when(user.firstName()).thenReturn("first");
        when(callbackQueryHandler.handle(callbackQuery)).thenAnswer(inv -> expectedResponse);

        BaseRequest<?, ?> result = updateProcessor.processUpdate(update);

        assertEquals(expectedResponse, result);
        verify(userService).getOrCreateUser(123L, "username", "first");
    }

    @Test
    void processUpdate_InlineQuery_DelegatesToInlineHandler() {
        Update update = mock(Update.class);
        InlineQuery inlineQuery = mock(InlineQuery.class);
        User user = mock(User.class);
        AnswerInlineQuery expectedResponse = mock(AnswerInlineQuery.class);

        when(update.inlineQuery()).thenReturn(inlineQuery);
        when(inlineQuery.from()).thenReturn(user);
        when(user.id()).thenReturn(123L);
        when(user.username()).thenReturn("username");
        when(user.firstName()).thenReturn("first");
        when(inlineQueryHandler.handle(inlineQuery)).thenReturn(expectedResponse);

        BaseRequest<?, ?> result = updateProcessor.processUpdate(update);

        assertEquals(expectedResponse, result);
        verify(userService).getOrCreateUser(123L, "username", "first");
    }

    @Test
    void processUpdate_AwaitingSaveImageState_DelegatesToAwaitingSaveHandler() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);
        User user = mock(User.class);
        SendMessage expectedResponse = mock(SendMessage.class);

        when(update.message()).thenReturn(message);
        when(message.chat()).thenReturn(chat);
        when(chat.id()).thenReturn(456L);
        when(message.from()).thenReturn(user);
        when(user.id()).thenReturn(123L);
        when(user.username()).thenReturn("username");
        when(user.firstName()).thenReturn("first");
        when(sessionService.getUserState(456L)).thenReturn(UserState.AWAITING_SAVE_IMAGE);
        when(awaitingSaveStateHandler.handle(update)).thenReturn(expectedResponse);

        BaseRequest<?, ?> result = updateProcessor.processUpdate(update);

        assertEquals(expectedResponse, result);
    }

    @Test
    void processUpdate_SwipingState_DelegatesToSwipingHandler() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);
        User user = mock(User.class);
        BaseRequest<?, ?> expectedResponse = mock(BaseRequest.class);

        when(update.message()).thenReturn(message);
        when(message.chat()).thenReturn(chat);
        when(chat.id()).thenReturn(456L);
        when(message.from()).thenReturn(user);
        when(user.id()).thenReturn(123L);
        when(user.username()).thenReturn("username");
        when(user.firstName()).thenReturn("first");
        when(sessionService.getUserState(456L)).thenReturn(UserState.SWIPING);
        when(swipingStateHandler.handle(update, 456L)).thenAnswer(inv -> expectedResponse);

        BaseRequest<?, ?> result = updateProcessor.processUpdate(update);

        assertEquals(expectedResponse, result);
    }

    @Test
    void processUpdate_DefaultState_DelegatesToCommandRouter() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);
        User user = mock(User.class);
        BaseRequest<?, ?> expectedResponse = mock(BaseRequest.class);

        when(update.message()).thenReturn(message);
        when(message.chat()).thenReturn(chat);
        when(chat.id()).thenReturn(456L);
        when(message.from()).thenReturn(user);
        when(user.id()).thenReturn(123L);
        when(user.username()).thenReturn("username");
        when(user.firstName()).thenReturn("first");
        when(sessionService.getUserState(456L)).thenReturn(UserState.DEFAULT);
        when(commandRouter.route(update)).thenAnswer(inv -> expectedResponse);

        BaseRequest<?, ?> result = updateProcessor.processUpdate(update);

        assertEquals(expectedResponse, result);
    }

    @Test
    void processUpdate_ExceptionThrown_ReturnsErrorMessage() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);

        when(update.message()).thenReturn(message);
        when(message.chat()).thenReturn(chat);
        when(chat.id()).thenReturn(456L);
        when(sessionService.getUserState(456L)).thenThrow(new RuntimeException("Test exception"));

        SendMessage result = (SendMessage) updateProcessor.processUpdate(update);

        assertNotNull(result);
        assertEquals(
                "Произошла ошибка при обработке команды. Пожалуйста, попробуйте позже.",
                result.getParameters().get("text"));
        assertEquals(456L, result.getParameters().get("chat_id"));
    }
}
