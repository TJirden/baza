package cringe.baza.bot.command;

import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.BaseRequest;
import com.pengrad.telegrambot.request.SendMessage;
import cringe.baza.meme.MemeProcessor;
import cringe.baza.model.MemeMutationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class EditCommand implements Command {

    private final MemeProcessor memeProcessor;

    @Override
    public String command() {
        return "edit";
    }

    @Override
    public String description() {
        return "Изменить описание мема. Пример: /edit 12345 новое описание";
    }

    @Override
    public BaseRequest<?, ?> handle(Update update) {
        long chatId = update.message().chat().id();
        long userId = update.message().from().id();
        String text = extractText(update.message().text());

        if (text == null || text.isBlank()) {
            return new SendMessage(chatId, "Использование: /edit {id} {новое описание}");
        }

        String[] parts = text.split("\\s+", 2);
        if (parts.length < 2) {
            return new SendMessage(chatId, "Необходимо указать новое описание. Пример: /edit 12345 смешной кот");
        }

        String memeId = parts[0];
        String newDescription = parts[1];

        MemeMutationResult result = memeProcessor.updateForOwner(memeId, newDescription, userId);

        return switch (result) {
            case DONE -> new SendMessage(chatId, "Описание мема успешно обновлено.");
            case NOT_FOUND -> new SendMessage(chatId, "Мем с ID " + memeId + " не найден.");
            case FORBIDDEN -> new SendMessage(chatId, "Вы не можете редактировать чужой мем.");
        };
    }
}
