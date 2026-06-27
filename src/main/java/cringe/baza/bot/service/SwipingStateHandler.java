package cringe.baza.bot.service;

import cringe.baza.user.UserSessionService;

import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.BaseRequest;
import com.pengrad.telegrambot.request.SendMessage;
import cringe.baza.bot.model.UserState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SwipingStateHandler {

    private final UserSessionService sessionService;
    private final CommandRouter commandRouter;

    public BaseRequest<?, ?> handle(Update update, long chatId) {
        String text = update.message().text();
        if (text == null || !text.startsWith("/")) {
            return new SendMessage(
                    chatId,
                    "Вы находитесь в режиме оценки мемов. Нажимайте кнопки под картинкой или напишите /cancel для выхода.");
        }

        sessionService.setUserState(chatId, UserState.DEFAULT);
        if (text.equalsIgnoreCase("/cancel")) {
            return new SendMessage(chatId, "Режим оценки завершен.");
        }
        return commandRouter.route(update);
    }
}
