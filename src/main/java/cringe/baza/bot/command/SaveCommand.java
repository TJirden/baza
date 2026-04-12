package cringe.baza.bot.command;

import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.SendMessage;
import cringe.baza.bot.model.UserState;
import cringe.baza.bot.service.UserSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class SaveCommand implements Command {

    private final UserSessionService sessionService;

    @Override
    public String command() {
        return "save";
    }

    @Override
    public String description() {
        return "Сохранить картинку";
    }

    @Override
    public SendMessage handle(Update update) {
        long chatId = update.message().chat().id();

        sessionService.setUserState(chatId, UserState.AWAITING_SAVE_IMAGE);

        return new SendMessage(chatId, "Скиньте картинку с подписью");
    }
}
