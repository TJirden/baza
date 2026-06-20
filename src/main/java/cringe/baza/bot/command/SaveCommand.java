package cringe.baza.bot.command;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.SendResponse;
import cringe.baza.bot.model.UserState;
import cringe.baza.bot.service.AsyncMemeService;
import cringe.baza.bot.service.UserSessionService;
import java.util.Arrays;
import java.util.stream.Collectors;
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

        String text = extractText(update.message().text());
        String visibility = "PUBLIC";
        String description = null;

        if (text != null && !text.isBlank()) {
            String[] parts = text.split("\\s+");
            String type = parts[0].toLowerCase();

            if (type.equals("private")) {
                visibility = "PRIVATE";
                description = text.substring("private".length()).trim();
            } else if (type.equals("group")) {
                int i = 1;
                while (i < parts.length && isNumeric(parts[i])) {
                    i++;
                }
                if (i == 1) {
                    return new SendMessage(chatId, "⚠️ Укажите ID групп: /save group {id1} {id2} [описание]");
                }
                String groupIds = Arrays.stream(parts, 1, i).collect(Collectors.joining(","));
                visibility = "GROUP:" + groupIds;
                description = Arrays.stream(parts).skip(i).collect(Collectors.joining(" ")).trim();
            } else if (type.equals("public")) {
                visibility = "PUBLIC";
                description = text.substring("public".length()).trim();
            } else {
                Message replyTo = update.message().replyToMessage();
                if (replyTo != null && replyTo.photo() != null && replyTo.photo().length > 0) {
                    visibility = "PUBLIC";
                    description = text;
                } else {
                    return new SendMessage(
                            chatId, "Неверный формат. Используйте: /save, /save private, /save public или /save group 1 2");
                }
            }
        }

        Message replyTo = update.message().replyToMessage();
        if (replyTo != null && replyTo.photo() != null && replyTo.photo().length > 0) {
            if (description == null || description.isBlank()) {
                description = replyTo.caption();
            }

            try {
                SendResponse response = bot.execute(new SendMessage(chatId, "Получаю мем из ответа и индексирую..."));
                if (response == null || !response.isOk()) {
                    return new SendMessage(chatId, "Ошибка: не удалось запустить процесс сохранения мема.");
                }

                asyncMemeService.processAndSaveMemeAsync(
                        chatId,
                        userId,
                        replyTo.photo(),
                        description,
                        visibility,
                        response.message().messageId());

                return null;
            } catch (Exception e) {
                log.error("Failed to save meme by reply: {}", e.getMessage(), e);
                return new SendMessage(chatId, "Ошибка: произошел сбой при сохранении мема.");
            }
        }

        if (chatId != userId) {
            return new SendMessage(chatId, "⚠️ В групповом чате команда /save должна быть ответом на сообщение с фото.");
        }

        sessionService.setUserState(chatId, UserState.AWAITING_SAVE_IMAGE);
        sessionService.setTempData(chatId, visibility);

        return new SendMessage(chatId, "Скиньте картинку с подписью (сохранится с доступом: " + visibility + ")");
    }

    private boolean isNumeric(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        return str.chars().allMatch(Character::isDigit);
    }
}
