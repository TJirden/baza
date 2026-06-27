package cringe.baza.bot.service;

import cringe.baza.meme.AsyncMemeService;
import cringe.baza.user.UserSessionService;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.SendResponse;
import cringe.baza.bot.model.UserState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AwaitingSaveStateHandler {

    private final UserSessionService sessionService;
    private final TelegramBot bot;
    private final AsyncMemeService asyncMemeService;

    public SendMessage handle(Update update) {
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
}
