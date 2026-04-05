package cringe.baza.bot.service;

import com.pengrad.telegrambot.model.PhotoSize;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.SendMessage;
import cringe.baza.bot.command.Command;
import cringe.baza.bot.model.UserState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateProcessor {
    private final List<Command> commands;
    private final UserSessionService sessionService;

    public SendMessage processUpdate(Update update) {
        long chatId = update.message().chat().id();
        UserState currentState = sessionService.getUserState(chatId);
        if (currentState == UserState.AWAITING_SAVE_IMAGE) {
            return processImageSave(update);
        }
        return processCommand(update);
    }

    private SendMessage processImageSave(Update update) {
        long chatId = update.message().chat().id();

        if (update.message().photo() == null || update.message().photo().length == 0) {
            return new SendMessage(chatId, "Пожалуйста, отправьте картинку с подписью");
        }

        String caption = update.message().caption();

        PhotoSize[] photos = update.message().photo();
        if (photos.length != 1){
            return  new SendMessage(chatId, "Одно фото за раз");
        }

        // TODO: Здесь будет логика сохранения картинки

        sessionService.setUserState(chatId, UserState.DEFAULT);

        String response = "Картинка сохранена, подпись:" + caption;

        return new SendMessage(chatId, response);
    }


    private SendMessage processCommand(Update update) {
        long chatId = update.message().chat().id();
        String text = update.message().text();

        for (Command command : commands) {
            if (command.supports(text)) {
                log.info("Обработана команда: command=/{}, chatId={}, text={}", command.command(), chatId, text);
                return command.handle(update);
            }
        }

        log.warn("Получена неизвестная команда: chatId={}, text={}", chatId, text);
        return new SendMessage(chatId, "Неизвестная команда. Используй /help для списка команд.");
    }
}

