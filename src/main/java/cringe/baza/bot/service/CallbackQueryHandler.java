package cringe.baza.bot.service;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.CallbackQuery;
import com.pengrad.telegrambot.request.AnswerCallbackQuery;
import com.pengrad.telegrambot.request.BaseRequest;
import com.pengrad.telegrambot.request.EditMessageCaption;
import cringe.baza.bot.model.UserState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CallbackQueryHandler {

    private final MemeModerationService moderationService;
    private final MemeBattleService memeBattleService;
    private final SwipeService swipeService;
    private final UserSessionService sessionService;
    private final TelegramBot bot;

    public BaseRequest<?, ?> handle(CallbackQuery callbackQuery) {
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
                            .text("Режим оценки неактивен!")
                            .showAlert(true);
                }

                String[] parts = data.split(":");
                String memeId = parts[1];
                String voteType = parts[2];

                swipeService.registerSwipeVote(userId, memeId, voteType);

                String ratingText = "BASE".equalsIgnoreCase(voteType) ? "База" : "Кринж";
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
                String editedText = originalText + "\n\n*Оценка завершена.*";

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
