package cringe.baza.bot.command;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.SendResponse;
import cringe.baza.bot.model.UserState;
import cringe.baza.meme.AsyncMemeService;
import cringe.baza.user.UserSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class SaveCommand implements Command {

    private final UserSessionService sessionService;
    private final TelegramBot bot;
    private final AsyncMemeService asyncMemeService;
    private final SaveCommandParser parser;

    @Override
    public String command() {
        return "save";
    }

    @Override
    public String description() {
        return "Сохранить мем. Формат: /save [public|private|group 1 2]";
    }

    @Override
    public SendMessage handle(Update update) {
        long chatId = update.message().chat().id();
        long userId = update.message().from().id();
        Message replyTo = update.message().replyToMessage();
        boolean isReplyToPhoto = replyTo != null && replyTo.photo() != null && replyTo.photo().length > 0;

        if (isReplyToPhoto) {
            return handleReplySave(update, replyTo, chatId, userId);
        }

        if (chatId != userId) {
            return new SendMessage(chatId, "В групповом чате команда /save должна быть ответом на сообщение с фото.");
        }

        return handleStatefulSave(update, chatId);
    }

    private SendMessage handleReplySave(Update update, Message replyTo, long chatId, long userId) {
        String text = extractText(update.message().text());
        SaveParseResult parseResult = parser.parseReplySave(text);

        if (!parseResult.success()) {
            return new SendMessage(chatId, parseResult.errorMessage());
        }

        String visibility = parseResult.visibility();
        String description = parseResult.description();
        if (description == null || description.isBlank()) {
            description = replyTo.caption();
        }

        SendResponse response = bot.execute(new SendMessage(chatId, "Получаю мем из ответа и индексирую..."));
        if (response == null || !response.isOk()) {
            throw new RuntimeException("Не удалось запустить процесс сохранения мема");
        }

        asyncMemeService.saveMemeAsync(
                chatId,
                userId,
                replyTo.photo(),
                description,
                visibility,
                response.message().messageId());

        return null;
    }

    private SendMessage handleStatefulSave(Update update, long chatId) {
        String text = extractText(update.message().text());
        SaveParseResult parseResult = parser.parseStatefulSave(text);

        if (!parseResult.success()) {
            return new SendMessage(chatId, parseResult.errorMessage());
        }

        String visibility = parseResult.visibility();

        sessionService.setUserState(chatId, UserState.AWAITING_SAVE_IMAGE);
        sessionService.setTempData(chatId, visibility);

        return new SendMessage(chatId, "Скиньте картинку с подписью (сохранится с доступом: " + visibility + ")");
    }
}
