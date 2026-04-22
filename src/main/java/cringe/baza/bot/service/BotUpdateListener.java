package cringe.baza.bot.service;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.BotCommand;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.BaseRequest;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.request.SetMyCommands;
import cringe.baza.bot.command.Command;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class BotUpdateListener implements UpdatesListener {

    private final TelegramBot bot;
    private final List<Command> commands;
    private final UpdateProcessor updateProcessor;

    private BotCommand[] botCommands;

    @PostConstruct
    public void init() {
        botCommands = commands.stream()
                .map(cmd -> new BotCommand("/" + cmd.command(), cmd.description()))
                .toArray(BotCommand[]::new);

        bot.execute(new SetMyCommands(botCommands));
        bot.setUpdatesListener(this);
    }

    @Override
    public int process(List<Update> updates) {
        for (Update update : updates) {
            try {
                BaseRequest<?,?> message = updateProcessor.processUpdate(update);

                if (message != null) {
                    var response = bot.execute(message);

                    if (!response.isOk()) {
                        log.error("Ошибка от Telegram API: {} - {}", response.errorCode(), response.description());
                    } else {
                        log.info("Успешно выполнен запрос: {}", message.getClass().getSimpleName());
                    }
                }

            } catch (Exception e) {
                log.error("Ошибка обработки обновления: updateId={}", update.updateId(), e);
            }
        }
        return CONFIRMED_UPDATES_ALL;
    }
}