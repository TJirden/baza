package cringe.baza.bot.command;

import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.BaseRequest;
import com.pengrad.telegrambot.request.SendMessage;
import cringe.baza.bot.model.UserState;
import cringe.baza.bot.service.SwipeService;
import cringe.baza.bot.service.UserSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class SwipeCommand implements Command {

    private final SwipeService swipeService;
    private final UserSessionService sessionService;

    @Override
    public String command() {
        return "swipe";
    }

    @Override
    public String description() {
        return "Оценить случайные мемы (База или Кринж) в ЛС";
    }

    @Override
    public BaseRequest<?, ?> handle(Update update) {
        long chatId = update.message().chat().id();
        long userId = update.message().from().id();

        if (chatId != userId) {
            return new SendMessage(chatId, "⚠️ Команда /swipe доступна только в личных сообщениях с ботом.");
        }

        sessionService.setUserState(chatId, UserState.SWIPING);
        swipeService.sendSwipeCard(chatId, userId);

        return null;
    }
}
