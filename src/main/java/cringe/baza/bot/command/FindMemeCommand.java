package cringe.baza.bot.command;

import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.request.BaseRequest;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.request.SendPhoto;
import cringe.baza.meme.MemeProcessor;
import cringe.baza.model.Meme;
import cringe.baza.user.TelegramUserService;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class FindMemeCommand implements Command {

    private final MemeProcessor memeProcessor;
    private final TelegramUserService userService;

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
        long userId = update.message().from().id();
        String query = extractText(update.message().text());

        if (query == null || query.isBlank()) {
            return new SendMessage(chatId, "Введите поисковый запрос. Пример: /find пёс");
        }

        List<Long> userGroupIds = userService.getUserGroupIds(userId);
        Optional<Meme> meme = memeProcessor.getSingleMemeByDescription(query, userId, userGroupIds);

        if (meme.isEmpty()) {
            return new SendMessage(chatId, "Ничего не нашлось по запросу: " + query);
        }

        SendPhoto sendPhoto = new SendPhoto(chatId, meme.get().fileId());

        if (meme.get().description() != null && !meme.get().description().isBlank()) {
            sendPhoto.caption(meme.get().description());
        }

        sendPhoto.replyMarkup(new InlineKeyboardMarkup(new InlineKeyboardButton("Пожаловаться 🚨")
                .callbackData("report:" + meme.get().id())));

        return sendPhoto;
    }
}
