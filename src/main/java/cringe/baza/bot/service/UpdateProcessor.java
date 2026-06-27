package cringe.baza.bot.service;

import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.User;
import com.pengrad.telegrambot.request.BaseRequest;
import com.pengrad.telegrambot.request.SendMessage;
import cringe.baza.bot.model.UserState;
import cringe.baza.user.TelegramUserService;
import cringe.baza.user.UserSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateProcessor {

    private final UserSessionService sessionService;
    private final TelegramUserService userService;

    private final CallbackQueryHandler callbackQueryHandler;
    private final InlineQueryHandler inlineQueryHandler;
    private final CommandRouter commandRouter;
    private final AwaitingSaveStateHandler awaitingSaveStateHandler;
    private final SwipingStateHandler swipingStateHandler;

    public BaseRequest<?, ?> processUpdate(Update update) {
        try {
            return doProcessUpdate(update);
        } catch (Exception e) {
            log.error("Ошибка при обработке обновления: {}", e.getMessage(), e);
            return handleException(update, e);
        }
    }

    private BaseRequest<?, ?> doProcessUpdate(Update update) {
        if (update.callbackQuery() != null) {
            User tgUser = update.callbackQuery().from();
            if (tgUser != null) {
                userService.getOrCreateUser(tgUser.id(), tgUser.username(), tgUser.firstName());
            }
            return callbackQueryHandler.handle(update.callbackQuery());
        }

        if (update.inlineQuery() != null) {
            User tgUser = update.inlineQuery().from();
            if (tgUser != null) {
                userService.getOrCreateUser(tgUser.id(), tgUser.username(), tgUser.firstName());
            }
            return inlineQueryHandler.handle(update.inlineQuery());
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
            return awaitingSaveStateHandler.handle(update);
        }
        if (currentState == UserState.SWIPING) {
            return swipingStateHandler.handle(update, chatId);
        }
        return commandRouter.route(update);
    }

    private BaseRequest<?, ?> handleException(Update update, Exception e) {
        Long chatId = null;
        if (update.message() != null) {
            chatId = update.message().chat().id();
        } else if (update.callbackQuery() != null && update.callbackQuery().message() != null) {
            chatId = update.callbackQuery().message().chat().id();
        }

        if (chatId != null) {
            return new SendMessage(
                    chatId.longValue(), "Произошла ошибка при обработке команды. Пожалуйста, попробуйте позже.");
        }
        return null;
    }
}
