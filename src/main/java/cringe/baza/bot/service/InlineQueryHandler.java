package cringe.baza.bot.service;

import cringe.baza.user.TelegramUserService;

import com.pengrad.telegrambot.model.InlineQuery;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.model.request.InlineQueryResultCachedPhoto;
import com.pengrad.telegrambot.request.AnswerInlineQuery;
import cringe.baza.model.Meme;
import cringe.baza.meme.MemeProcessor;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class InlineQueryHandler {

    private final TelegramUserService userService;
    private final MemeProcessor memeProcessor;
    private final int searchLimit;

    public InlineQueryHandler(
            TelegramUserService userService,
            MemeProcessor memeProcessor,
            @Value("${app.bot.search-limit}") int searchLimit) {
        this.userService = userService;
        this.memeProcessor = memeProcessor;
        this.searchLimit = searchLimit;
    }

    public AnswerInlineQuery handle(InlineQuery inlineQuery) {
        String query = inlineQuery.query();
        if (query == null || query.isBlank()) {
            return new AnswerInlineQuery(inlineQuery.id());
        }

        try {
            long userId = inlineQuery.from().id();
            List<Long> userGroupIds = userService.getUserGroupIds(userId);
            List<Meme> memes = memeProcessor.getMemesByDescription(query, searchLimit, userId, userGroupIds);

            InlineQueryResultCachedPhoto[] results = memes.stream()
                    .map(meme -> {
                        String resultId = UUID.randomUUID().toString();
                        InlineQueryResultCachedPhoto photoResult =
                                new InlineQueryResultCachedPhoto(resultId, meme.fileId());
                        photoResult.replyMarkup(new InlineKeyboardMarkup(
                                new InlineKeyboardButton("Пожаловаться").callbackData("report:" + meme.id())));
                        return photoResult;
                    })
                    .toArray(InlineQueryResultCachedPhoto[]::new);

            return new AnswerInlineQuery(inlineQuery.id(), results).cacheTime(0).isPersonal(true);

        } catch (Exception e) {
            log.error("Inline search error: {}", e.getMessage());
            return new AnswerInlineQuery(inlineQuery.id());
        }
    }
}
