package cringe.baza.bot.command;

import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.BaseRequest;

public interface Command {

    String command();

    String description();

    BaseRequest<?, ?> handle(Update update);

    default boolean supports(String text) {
        if (text == null) {
            return false;
        }

        return text.equals("/" + command())
                || text.startsWith("/" + command() + " ")
                || text.startsWith("/" + command() + "@");
    }

    default String extractText(String text) {
        String withoutCommand =
                text.replaceFirst("(?i)/" + command() + "(?:@\\S+)?", "").trim();

        if (withoutCommand.isEmpty()) {
            return null;
        }

        return withoutCommand;
    }
}
