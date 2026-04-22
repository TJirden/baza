package cringe.baza.bot.command;

import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.BaseRequest;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.request.SendPhoto;

public interface Command {

    /** Имя команды без "/" */
    String command();

    String description();

    /** Обработка входящего обновления */
    BaseRequest<?,?> handle(Update update);

    /** Проверяет, подходит ли данное обновление этой команде */
    default boolean supports(String text) {
        if (text == null) {
            return false;
        }

        return text.equals("/" + command())
                || text.startsWith("/" + command() + " ")
                || text.startsWith("/" + command() + "@");
    }

    default String extractText(String text) {
        String withoutCommand = text.replaceFirst("(?i)/" + command() + "(?:@\\S+)?", "").trim();

        if (withoutCommand.isEmpty()) {
            return null;
        }

        String[] parts = withoutCommand.split("\\s+");
        return parts[0].trim();
    }
}
