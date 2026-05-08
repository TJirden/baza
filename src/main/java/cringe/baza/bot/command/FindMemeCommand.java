package cringe.baza.bot.command;

import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.BaseRequest;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.request.SendPhoto;
import cringe.baza.model.Meme;
import cringe.baza.processor.MemeProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
@Component
public class FindMemeCommand implements Command {

    private final MemeProcessor memeProcessor;

    @Override
    public String command() {
        return "find";
    }

    @Override
    public String description() {
        return "Найти мемы по описанию. Пример: /find грустный кот";
    }

    @Override
    public BaseRequest<?, ?> handle(Update update) {
        long chatId = update.message().chat().id();
        String query = extractText(update.message().text());

        if (query == null || query.isBlank()) {
            return new SendMessage(chatId, "Введите поисковый запрос. Пример: /find пёс");
        }

        Optional<Meme> meme = memeProcessor.getSingleMemeByDescription(query);

        if (meme.isEmpty()) {
            return new SendMessage(chatId, "Ничего не нашлось по запросу: " + query);
        }

        SendPhoto sendPhoto = new SendPhoto(chatId, meme.get().fileId());

        if (meme.get().description() != null && !meme.get().description().isBlank()) {
            sendPhoto.caption(meme.get().description());
        }

        return sendPhoto;
    }
}