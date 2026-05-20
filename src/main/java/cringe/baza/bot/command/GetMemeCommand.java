package cringe.baza.bot.command;

import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.BaseRequest;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.request.SendPhoto;
import cringe.baza.model.Meme;
import cringe.baza.processor.MemeProcessor;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
@Slf4j
public class GetMemeCommand implements Command {

    private final MemeProcessor memeProcessor;

    @Override
    public String command() {
        return "getmeme";
    }

    @Override
    public String description() {
        return "Получить мем по ID";
    }

    @Override
    public BaseRequest<?, ?> handle(Update update) {
        long chatId = update.message().chat().id();
        String messageText = update.message().text();

        String memeId = extractText(messageText);

        if (memeId == null || memeId.isEmpty()) {
            return new SendMessage(chatId, "Нужно указать ID мема. Пример: /getmeme 123");
        }

        Optional<Meme> memeOptional = memeProcessor.getMemeById(memeId);

        if (memeOptional.isEmpty()) {
            return new SendMessage(chatId, "Мем с ID " + memeId + " не найден");
        }

        Meme meme = memeOptional.get();

        SendPhoto sendPhoto = new SendPhoto(chatId, meme.fileId());

        if (meme.description() != null && !meme.description().isBlank()) {
            sendPhoto.caption(meme.description());
        }

        return sendPhoto;
    }
}
