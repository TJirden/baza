package cringe.baza.bot.command;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.BaseRequest;
import com.pengrad.telegrambot.request.SendMessage;
import cringe.baza.battle.MemeBattleService;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class BattleCommand implements Command {

    private final TelegramBot bot;
    private final MemeBattleService memeBattleService;

    @Override
    public String command() {
        return "battle";
    }

    @Override
    public String description() {
        return "Запустить баттл мемов в текущем чате";
    }

    @Override
    public BaseRequest<?, ?> handle(Update update) {
        long chatId = update.message().chat().id();
        bot.execute(new SendMessage(chatId, "⏳ Подбираю достойных кандидатов для баттла мемов..."));

        CompletableFuture.runAsync(() -> {
            try {
                memeBattleService.startBattle(chatId);
            } catch (Exception e) {
                bot.execute(new SendMessage(chatId, "❌ Произошла ошибка при запуске баттла: " + e.getMessage()));
            }
        });

        return null;
    }
}
