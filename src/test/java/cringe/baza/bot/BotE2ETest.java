package cringe.baza.bot;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.User;
import com.pengrad.telegrambot.request.BaseRequest;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.SendResponse;
import cringe.baza.bot.service.BotUpdateListener;
import cringe.baza.test.AbstractIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class BotE2ETest extends AbstractIntegrationTest {

    @Autowired
    private BotUpdateListener botUpdateListener;

    @MockitoBean
    private TelegramBot telegramBot;

    @Test
    void testStartCommandFlow() {
        SendResponse mockResponse = mock(SendResponse.class);
        when(mockResponse.isOk()).thenReturn(true);
        when(telegramBot.execute(any(BaseRequest.class))).thenReturn(mockResponse);

        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);
        User user = mock(User.class);

        when(update.message()).thenReturn(message);
        when(message.chat()).thenReturn(chat);
        when(message.from()).thenReturn(user);
        when(chat.id()).thenReturn(12345L);
        when(user.id()).thenReturn(12345L);
        when(user.username()).thenReturn("testuser");
        when(user.firstName()).thenReturn("Test");
        when(message.text()).thenReturn("/start");

        botUpdateListener.process(List.of(update));

        ArgumentCaptor<BaseRequest> requestCaptor = ArgumentCaptor.forClass(BaseRequest.class);
        verify(telegramBot, atLeastOnce()).execute(requestCaptor.capture());

        List<BaseRequest> capturedRequests = requestCaptor.getAllValues();
        SendMessage sendMessage = null;
        for (BaseRequest req : capturedRequests) {
            if (req instanceof SendMessage) {
                sendMessage = (SendMessage) req;
                break;
            }
        }

        assertNotNull(sendMessage);
        assertEquals(12345L, sendMessage.getParameters().get("chat_id"));
        assertTrue(sendMessage.getParameters().get("text").toString().contains("Привет"));
    }
}
