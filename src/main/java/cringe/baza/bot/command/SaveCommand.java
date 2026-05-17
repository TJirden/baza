package cringe.baza.bot.command;

import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.SendMessage;
import cringe.baza.bot.model.UserState;
import cringe.baza.bot.service.UserSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class SaveCommand implements Command {

    private final UserSessionService sessionService;

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
        String text = extractText(update.message().text());

        String visibility = "PUBLIC";
        
        if (text != null && !text.isBlank()) {
            String[] parts = text.split("\\s+");
            String type = parts[0].toLowerCase();
            
            if (type.equals("private")) {
                visibility = "PRIVATE";
            } else if (type.equals("group") && parts.length > 1) {
                String groupIds = Arrays.stream(parts)
                        .skip(1)
                        .collect(Collectors.joining(","));
                visibility = "GROUP:" + groupIds;
            } else if (!type.equals("public")) {
                return new SendMessage(chatId, "Неверный формат. Используйте: /save, /save private, /save public или /save group 1 2");
            }
        }

        sessionService.setUserState(chatId, UserState.AWAITING_SAVE_IMAGE);
        sessionService.setTempData(chatId, visibility);

        return new SendMessage(chatId, "Скиньте картинку с подписью (сохранится с доступом: " + visibility + ")");
    }
}
