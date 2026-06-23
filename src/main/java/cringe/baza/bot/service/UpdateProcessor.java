package cringe.baza.bot.service;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.CallbackQuery;
import com.pengrad.telegrambot.model.InlineQuery;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.User;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.model.request.InlineQueryResultCachedPhoto;
import com.pengrad.telegrambot.request.AnswerCallbackQuery;
import com.pengrad.telegrambot.request.AnswerInlineQuery;
import com.pengrad.telegrambot.request.BaseRequest;
import com.pengrad.telegrambot.request.EditMessageCaption;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.SendResponse;
import cringe.baza.bot.command.Command;
import cringe.baza.bot.model.UserState;
import cringe.baza.model.Meme;
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
    private final MemeModerationService moderationService;
    private final MemeBattleService memeBattleService;
    private final SwipeService swipeService;

    public BaseRequest<?, ?> processUpdate(Update update) {
        if (update.callbackQuery() != null) {
            User tgUser = update.callbackQuery().from();
            if (tgUser != null) {
                userService.getOrCreateUser(tgUser.id(), tgUser.username(), tgUser.firstName());
            }
            return handleCallbackQuery(update.callbackQuery());
        }

        if (update.inlineQuery() != null) {
            User tgUser = update.inlineQuery().from();
            if (tgUser != null) {
                userService.getOrCreateUser(tgUser.id(), tgUser.username(), tgUser.firstName());
            }
            return handleInlineQuery(update.inlineQuery());
        }

        if (update.message() == null) {
            return null;
        }

        if (update.message().from() != null) {
            User tgUser = update.message().from();
            userService.getOrCreateUser(tgUser.id(), tgUser.username(), tgUser.firstName());
        }

        long chatId = update.message().chat().id();
        UserState currentState = sessionService.getUserState(chatId);

        if (currentState == UserState.AWAITING_SAVE_IMAGE) {
            return processImageSave(update);
        }
        if (currentState == UserState.SWIPING) {
            return handleSwipingState(update, chatId);
        }
        return processCommand(update);
    }

    private BaseRequest<?, ?> handleSwipingState(Update update, long chatId) {
        String text = update.message().text();
        if (text == null || !text.startsWith("/")) {
            return new SendMessage(
                    chatId,
                    "Вы находитесь в режиме оценки мемов. Нажимайте кнопки под картинкой или напишите /cancel для выхода.");
        }

        sessionService.setUserState(chatId, UserState.DEFAULT);
        if (text.equalsIgnoreCase("/cancel")) {
            return new SendMessage(chatId, "Режим оценки завершен.");
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

            asyncMemeService.saveMemeAsync(
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
            List<Meme> memes = memeProcessor.getMemesByDescription(query, 50, userId, userGroupIds);

            InlineQueryResultCachedPhoto[] results = memes.stream()
                    .map(meme -> {
                        String resultId = UUID.randomUUID().toString();
                        InlineQueryResultCachedPhoto photoResult =
                                new InlineQueryResultCachedPhoto(resultId, meme.fileId());
                        photoResult.replyMarkup(new InlineKeyboardMarkup(
                                new InlineKeyboardButton("Пожаловаться 🚨").callbackData("report:" + meme.id())));
                        return photoResult;
                    })
                    .toArray(InlineQueryResultCachedPhoto[]::new);

            return new AnswerInlineQuery(inlineQuery.id(), results).cacheTime(0).isPersonal(true);

        } catch (Exception e) {
            log.error("Inline search error: {}", e.getMessage());
            return new AnswerInlineQuery(inlineQuery.id());
        }
    }

    private BaseRequest<?, ?> handleCallbackQuery(CallbackQuery callbackQuery) {
        String data = callbackQuery.data();
        if (data != null && data.startsWith("report:")) {
            String memeId = data.substring(7);
            Long userId = callbackQuery.from().id();

            MemeModerationService.ReportResult result = moderationService.reportMeme(memeId, userId);

            String status = result.status();
            long count = result.currentReports();

            if ("ALREADY_REPORTED".equals(status)) {
                return new AnswerCallbackQuery(callbackQuery.id())
                        .text("Вы уже жаловались на этот мем!")
                        .showAlert(true);
            } else if ("QUARANTINED".equals(status)) {
                return new AnswerCallbackQuery(callbackQuery.id())
                        .text("Мем заблокирован и отправлен на модерацию из-за жалоб!")
                        .showAlert(true);
            } else {
                return new AnswerCallbackQuery(callbackQuery.id())
                        .text("Жалоба принята. Всего жалоб на мем: " + count + "/3")
                        .showAlert(true);
            }
        }

        if (data != null && data.startsWith("vote:")) {
            try {
                String[] parts = data.split(":");
                Long battleId = Long.parseLong(parts[1]);
                String option = parts[2];
                Long userId = callbackQuery.from().id();

                boolean success = memeBattleService.registerVote(battleId, userId, option);
                if (success) {
                    return new AnswerCallbackQuery(callbackQuery.id())
                            .text("Ваш голос за Вариант " + option + " принят!");
                } else {
                    return new AnswerCallbackQuery(callbackQuery.id())
                            .text("Вы уже голосовали в этом баттле!")
                            .showAlert(true);
                }
            } catch (Exception e) {
                log.error("Error processing battle vote callback: {}", e.getMessage(), e);
                return new AnswerCallbackQuery(callbackQuery.id())
                        .text("Ошибка при обработке голоса!")
                        .showAlert(true);
            }
        }

        if (data != null && data.startsWith("duel_accept:")) {
            try {
                long battleId = Long.parseLong(data.substring(12));
                long userId = callbackQuery.from().id();
                return memeBattleService.acceptDuel(battleId, userId, callbackQuery.id());
            } catch (Exception e) {
                log.error("Error processing duel accept callback: {}", e.getMessage(), e);
                return new AnswerCallbackQuery(callbackQuery.id())
                        .text("Ошибка при принятии вызова!")
                        .showAlert(true);
            }
        }

        if (data != null && data.startsWith("duel_decline:")) {
            try {
                long battleId = Long.parseLong(data.substring(13));
                long userId = callbackQuery.from().id();
                return memeBattleService.declineDuel(battleId, userId, callbackQuery.id());
            } catch (Exception e) {
                log.error("Error processing duel decline callback: {}", e.getMessage(), e);
                return new AnswerCallbackQuery(callbackQuery.id())
                        .text("Ошибка при отклонении вызова!")
                        .showAlert(true);
            }
        }

        if (data != null && data.startsWith("duel_select:")) {
            try {
                String[] parts = data.split(":");
                long battleId = Long.parseLong(parts[1]);
                String memeId = parts[2];
                long userId = callbackQuery.from().id();
                return memeBattleService.selectDuelMeme(battleId, userId, memeId, callbackQuery.id());
            } catch (Exception e) {
                log.error("Error processing duel meme selection callback: {}", e.getMessage(), e);
                return new AnswerCallbackQuery(callbackQuery.id())
                        .text("Ошибка при выборе мема!")
                        .showAlert(true);
            }
        }

        if (data != null && data.startsWith("swipe_vote:")) {
            try {
                long userId = callbackQuery.from().id();
                long chatId = callbackQuery.message().chat().id();
                UserState currentState = sessionService.getUserState(chatId);

                if (currentState != UserState.SWIPING) {
                    return new AnswerCallbackQuery(callbackQuery.id())
                            .text("⚠️ Режим оценки неактивен!")
                            .showAlert(true);
                }

                String[] parts = data.split(":");
                String memeId = parts[1];
                String voteType = parts[2];

                swipeService.registerSwipeVote(userId, memeId, voteType);

                String ratingText = "BASE".equalsIgnoreCase(voteType) ? "🔥 База" : "💩 Кринж";
                String originalText = callbackQuery.message().caption();
                if (originalText == null) {
                    originalText = "";
                }
                String editedText = originalText + "\n\nВаша оценка: *" + ratingText + "*";

                bot.execute(
                        new EditMessageCaption(chatId, callbackQuery.message().messageId())
                                .caption(editedText)
                                .parseMode(com.pengrad.telegrambot.model.request.ParseMode.Markdown));

                swipeService.sendSwipeCard(chatId, userId);

                return new AnswerCallbackQuery(callbackQuery.id()).text("Голос принят!");
            } catch (Exception e) {
                log.error("Error processing swipe vote callback: {}", e.getMessage(), e);
                return new AnswerCallbackQuery(callbackQuery.id())
                        .text("Ошибка при обработке оценки!")
                        .showAlert(true);
            }
        }

        if (data != null && "swipe_stop".equals(data)) {
            try {
                long userId = callbackQuery.from().id();
                long chatId = callbackQuery.message().chat().id();
                sessionService.setUserState(chatId, UserState.DEFAULT);

                String originalText = callbackQuery.message().caption();
                if (originalText == null) {
                    originalText = "";
                }
                String editedText = originalText + "\n\n🛑 *Оценка завершена.*";

                bot.execute(
                        new EditMessageCaption(chatId, callbackQuery.message().messageId())
                                .caption(editedText)
                                .parseMode(com.pengrad.telegrambot.model.request.ParseMode.Markdown));

                return new AnswerCallbackQuery(callbackQuery.id()).text("Вы вышли из режима оценки.");
            } catch (Exception e) {
                log.error("Error stopping swipe mode: {}", e.getMessage(), e);
                return new AnswerCallbackQuery(callbackQuery.id())
                        .text("Ошибка при выходе из режима оценки!")
                        .showAlert(true);
            }
        }

        return new AnswerCallbackQuery(callbackQuery.id());
    }
}
