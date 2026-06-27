package cringe.baza.bot.service;

import cringe.baza.meme.SwipeService;
import cringe.baza.meme.MemeModerationService;
import cringe.baza.battle.MemeDuelService;
import cringe.baza.battle.MemeBattleService;
import cringe.baza.user.UserSessionService;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.CallbackQuery;
import com.pengrad.telegrambot.request.AnswerCallbackQuery;
import com.pengrad.telegrambot.request.BaseRequest;
import com.pengrad.telegrambot.request.EditMessageCaption;
import cringe.baza.bot.model.DuelActionResult;
import cringe.baza.bot.model.UserState;
import cringe.baza.model.ReportStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CallbackQueryHandler {

    private final MemeModerationService moderationService;
    private final MemeBattleService memeBattleService;
    private final MemeDuelService memeDuelService;
    private final SwipeService swipeService;
    private final UserSessionService sessionService;
    private final TelegramBot bot;

    public BaseRequest<?, ?> handle(CallbackQuery callbackQuery) {
        String data = callbackQuery.data();
        if (data != null && data.startsWith("report:")) {
            String memeId = data.substring(7);
            Long userId = callbackQuery.from().id();

            MemeModerationService.ReportResult result = moderationService.reportMeme(memeId, userId);

            ReportStatus status = result.status();
            long count = result.currentReports();

            if (ReportStatus.NOT_FOUND == status) {
                return new AnswerCallbackQuery(callbackQuery.id())
                        .text("Мем не найден или уже был удален!")
                        .showAlert(true);
            } else if (ReportStatus.ALREADY_REPORTED == status) {
                return new AnswerCallbackQuery(callbackQuery.id())
                        .text("Вы уже жаловались на этот мем!")
                        .showAlert(true);
            } else if (ReportStatus.QUARANTINED == status) {
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
                DuelActionResult result = memeDuelService.acceptDuel(battleId, userId);
                if (result == DuelActionResult.SUCCESS) {
                    return new AnswerCallbackQuery(callbackQuery.id())
                            .text("Вы приняли вызов! Перейдите в ЛС с ботом для выбора мема.");
                }
                return mapDuelResult(callbackQuery.id(), result);
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
                DuelActionResult result = memeDuelService.declineDuel(battleId, userId);
                if (result == DuelActionResult.SUCCESS) {
                    return new AnswerCallbackQuery(callbackQuery.id()).text("Вызов отменен.");
                }
                return mapDuelResult(callbackQuery.id(), result);
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
                DuelActionResult result = memeDuelService.selectDuelMeme(battleId, userId, memeId);
                if (result == DuelActionResult.SUCCESS) {
                    return new AnswerCallbackQuery(callbackQuery.id()).text("Мем выбран!");
                }
                return mapDuelResult(callbackQuery.id(), result);
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

    private AnswerCallbackQuery mapDuelResult(String callbackQueryId, DuelActionResult result) {
        switch (result) {
            case SUCCESS:
                return new AnswerCallbackQuery(callbackQueryId).text("Действие успешно выполнено.");
            case NOT_FOUND:
                return new AnswerCallbackQuery(callbackQueryId)
                        .text("⚠️ Дуэль не найдена!")
                        .showAlert(true);
            case INACTIVE:
                return new AnswerCallbackQuery(callbackQueryId)
                        .text("⚠️ Этот вызов уже неактивен!")
                        .showAlert(true);
            case UNAUTHORIZED:
                return new AnswerCallbackQuery(callbackQueryId)
                        .text("⚠️ Вы не имеете права выполнять это действие!")
                        .showAlert(true);
            case CHALLENGER_INSUFFICIENT_POINTS:
                return new AnswerCallbackQuery(callbackQueryId)
                        .text("⚠️ У вызывающего недостаточно очков!")
                        .showAlert(true);
            case OPPONENT_INSUFFICIENT_POINTS:
                return new AnswerCallbackQuery(callbackQueryId)
                        .text("⚠️ У вас недостаточно очков!")
                        .showAlert(true);
            case MEME_NOT_FOUND:
                return new AnswerCallbackQuery(callbackQueryId)
                        .text("⚠️ Выбранный мем не найден!")
                        .showAlert(true);
            case ALREADY_SELECTED:
                return new AnswerCallbackQuery(callbackQueryId)
                        .text("⚠️ Вы уже выбрали мем!")
                        .showAlert(true);
            case ERROR:
            default:
                return new AnswerCallbackQuery(callbackQueryId)
                        .text("⚠️ Произошла ошибка при обработке дуэли!")
                        .showAlert(true);
        }
    }
}
