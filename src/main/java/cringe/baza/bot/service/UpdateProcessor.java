package cringe.baza.bot.service;

import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.SendMessage;
import cringe.baza.bot.command.Command;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateProcessor {
    private final List<Command> commands;

    public SendMessage processUpdate(Update update) {
        return processCommand(update);
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

