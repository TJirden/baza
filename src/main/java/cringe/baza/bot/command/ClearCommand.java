package cringe.baza.bot.command;

import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.BaseRequest;
import com.pengrad.telegrambot.request.SendMessage;
import cringe.baza.processor.MemeProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class ClearCommand implements Command {

    private final MemeProcessor memeProcessor;

    @Value("${bot.admin.id}")
    private long adminId;

    @Override
    public String command() {
        return "clear";
    }

    @Override
    public String description() {
        return "Полная очистка базы (только для админа)";
    }

    @Override
    public BaseRequest<?, ?> handle(Update update) {
        long chatId = update.message().chat().id();

        if (chatId != adminId) {
            return new SendMessage(chatId, "Нуб, ты не можешь это сделать");
        }

        try {
            int deletedCount = memeProcessor.clearAllData();

            return new SendMessage(chatId, String.format("Удалено объектов: %d", deletedCount));
        } catch (Exception e) {
            return new SendMessage(chatId, "Ошибка при очистке базы: " + e.getMessage());
        }
    }
}