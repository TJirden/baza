package cringe.baza.bot.service;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.InlineQuery;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.User;
import com.pengrad.telegrambot.model.request.InlineQueryResultCachedPhoto;
import com.pengrad.telegrambot.request.AnswerInlineQuery;
import com.pengrad.telegrambot.request.BaseRequest;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.SendResponse;
import cringe.baza.bot.command.Command;
import cringe.baza.bot.model.UserState;
import cringe.baza.processor.MemeProcessor;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateProcessor {
    private final List<Command> commands;
    private final UserSessionService sessionService;

    private final TelegramFileService fileService;
    private final MemeProcessor memeProcessor;
    private final TelegramUserService userService;
    private final TelegramBot bot;
    private final AsyncMemeService asyncMemeService;

    public BaseRequest<?, ?> processUpdate(Update update) {
        if (update.inlineQuery() != null) {
            User tgUser = update.inlineQuery().from();
            if (tgUser != null) {
                userService.getOrCreateUser(tgUser.id(), tgUser.username(), tgUser.firstName());
            }
            return handleInlineQuery(update.inlineQuery());
        }

        if (update.message() != null && update.message().from() != null) {
            User tgUser = update.message().from();
            userService.getOrCreateUser(tgUser.id(), tgUser.username(), tgUser.firstName());
        }

        long chatId = update.message().chat().id();
        UserState currentState = sessionService.getUserState(chatId);

        if (currentState == UserState.AWAITING_SAVE_IMAGE) {
            return processImageSave(update);
        }
        return processCommand(update);
    }

    private SendMessage processImageSave(Update update) {
        long chatId = update.message().chat().id();
        long userId = update.message().from().id();
        if (update.message().photo() == null || update.message().photo().length == 0) {
            sessionService.setUserState(chatId, UserState.DEFAULT);
            return new SendMessage(chatId, "Ошибка: я не вижу фото в твоем сообщении. Сбрасываю состояние");
        }
        String description = update.message().caption();

        try {
            SendResponse response = bot.execute(new SendMessage(chatId, "Получаю мем и индексирую..."));
            if (response == null || !response.isOk()) {
                log.error("Не удалось отправить промежуточное сообщение в Telegram для chatId={}", chatId);
                sessionService.setUserState(chatId, UserState.DEFAULT);
                return new SendMessage(chatId, "Ошибка: не удалось запустить процесс сохранения мема.");
            }

            String visibilityContext = sessionService.getTempData(chatId);
            sessionService.setUserState(chatId, UserState.DEFAULT);

            asyncMemeService.processAndSaveMemeAsync(
                    chatId,
                    userId,
                    update.message().photo(),
                    description,
                    visibilityContext,
                    response.message().messageId());

            return null;
        } catch (Exception e) {
            log.error(
                    "Критическая ошибка при запуске асинхронного сохранения для пользователя {}: {}",
                    chatId,
                    e.getMessage());
            sessionService.setUserState(chatId, UserState.DEFAULT);
            return new SendMessage(chatId, "Ошибка: произошел сбой при сохранении мема.");
        }
    }

    private BaseRequest<?, ?> processCommand(Update update) {
        long chatId = update.message().chat().id();
        String text = update.message().text();

        for (Command command : commands) {
            if (command.supports(text)) {
                return command.handle(update);
            }
        }

        log.warn("Получена неизвестная команда от пользователя {}: {}", chatId, text);
        return new SendMessage(chatId, "Неизвестная команда. Используй /help для списка команд.");
    }

    private AnswerInlineQuery handleInlineQuery(InlineQuery inlineQuery) {
        String query = inlineQuery.query();
        if (query == null || query.isBlank()) {
            return new AnswerInlineQuery(inlineQuery.id());
        }

        try {
            long userId = inlineQuery.from().id();
            List<Long> userGroupIds = userService.getUserGroupIds(userId);
            List<String> fileIds = memeProcessor.getFileIdsByDescription(query, 50, userId, userGroupIds);

            InlineQueryResultCachedPhoto[] results = fileIds.stream()
                    .map(fileId -> {
                        String resultId = UUID.randomUUID().toString();
                        return new InlineQueryResultCachedPhoto(resultId, fileId);
                    })
                    .toArray(InlineQueryResultCachedPhoto[]::new);

            return new AnswerInlineQuery(inlineQuery.id(), results).cacheTime(0).isPersonal(true);

        } catch (Exception e) {
            log.error("Inline search error: {}", e.getMessage());
            return new AnswerInlineQuery(inlineQuery.id());
        }
    }
}
