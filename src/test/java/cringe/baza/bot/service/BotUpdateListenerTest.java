package cringe.baza.bot.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.BaseRequest;
import com.pengrad.telegrambot.request.SetMyCommands;
import com.pengrad.telegrambot.response.BaseResponse;
import cringe.baza.bot.command.Command;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BotUpdateListenerTest {

    @Mock
    private TelegramBot bot;

    @Mock
    private UpdateProcessor updateProcessor;

    @Mock
    private Command command;

    private BotUpdateListener listener;

    @Test
    void init_RegistersCommandsAndUpdatesListener() {
        when(command.command()).thenReturn("help");
        when(command.description()).thenReturn("Help description");

        listener = new BotUpdateListener(bot, List.of(command), updateProcessor);
        listener.init();

        verify(bot).execute(any(SetMyCommands.class));
        verify(bot).setUpdatesListener(listener);
    }

    @Test
    void process_SuccessAndErrorCases() {
        listener = new BotUpdateListener(bot, List.of(), updateProcessor);

        Update update1 = mock(Update.class);
        lenient().when(update1.updateId()).thenReturn(1);
        Update update2 = mock(Update.class);
        lenient().when(update2.updateId()).thenReturn(2);
        Update update3 = mock(Update.class);
        lenient().when(update3.updateId()).thenReturn(3);

        BaseRequest request = mock(BaseRequest.class);
        BaseResponse responseOk = mock(BaseResponse.class);
        lenient().when(responseOk.isOk()).thenReturn(true);

        BaseRequest requestErr = mock(BaseRequest.class);
        BaseResponse responseErr = mock(BaseResponse.class);
        lenient().when(responseErr.isOk()).thenReturn(false);
        lenient().when(responseErr.errorCode()).thenReturn(400);
        lenient().when(responseErr.description()).thenReturn("Bad Request");

        lenient()
                .when(updateProcessor.processUpdate(update1))
                .thenThrow(new RuntimeException("Exception during process"));
        lenient().when(updateProcessor.processUpdate(update2)).thenReturn(request);
        lenient().when(bot.execute(request)).thenReturn(responseOk);
        lenient().when(updateProcessor.processUpdate(update3)).thenReturn(requestErr);
        lenient().when(bot.execute(requestErr)).thenReturn(responseErr);

        int result = listener.process(List.of(update1, update2, update3));

        assertEquals(-1, result);
        verify(updateProcessor).processUpdate(update1);
        verify(updateProcessor).processUpdate(update2);
        verify(updateProcessor).processUpdate(update3);
    }
}
