package cringe.baza.bot.service;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.BotCommand;
import com.pengrad.telegrambot.model.Update;
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
                if (update.message() == null || update.message().text() == null) {
                    continue;
                }

                SendMessage message = updateProcessor.processUpdate(update);

                if (message != null) {
                    bot.execute(message);
                    log.info(
                            "Отправлен ответ пользователю: chatId={}",
                            message.getParameters().get("chat_id"));
                }

            } catch (Exception e) {
                log.error("Ошибка обработки обновления: updateId={}", update.updateId(), e);
            }
        }
        return CONFIRMED_UPDATES_ALL;
    }
}