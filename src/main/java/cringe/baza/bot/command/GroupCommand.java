package cringe.baza.bot.command;

import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.BaseRequest;
import com.pengrad.telegrambot.request.SendMessage;
import cringe.baza.bot.model.GroupActionResult;
import cringe.baza.domain.MemeGroup;
import cringe.baza.user.GroupService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class GroupCommand implements Command {

    private final GroupService groupService;

    @Override
    public String command() {
        return "group";
    }

    @Override
    public String description() {
        return "Управление группами: /group [create|join|leave|list]";
    }

    @Override
    public BaseRequest<?, ?> handle(Update update) {
        long chatId = update.message().chat().id();
        long userId = update.message().from().id();
        String username = update.message().from().username();
        String firstName = update.message().from().firstName();
        String text = extractText(update.message().text());

        if (text == null || text.isBlank()) {
            return new SendMessage(
                    chatId, "Использование:\n/group create {имя}\n/group join {id}\n/group leave {id}\n/group list");
        }

        String[] parts = text.split("\\s+", 2);
        String action = parts[0].toLowerCase();

        return switch (action) {
            case "create" -> {
                if (parts.length < 2 || parts[1].isBlank()) {
                    yield new SendMessage(chatId, "Укажите имя группы: /group create {имя}");
                }
                MemeGroup group = groupService.createGroup(userId, username, firstName, parts[1].trim());
                yield new SendMessage(chatId, "Группа '" + group.getName() + "' создана! ID: " + group.getId());
            }
            case "join" -> {
                Long groupId = parseGroupId(parts);
                if (groupId == null) {
                    yield new SendMessage(chatId, "Укажите ID группы (число): /group join {id}");
                }
                GroupActionResult result = groupService.joinGroup(userId, username, firstName, groupId);
                yield switch (result.status()) {
                    case OK ->
                        new SendMessage(
                                chatId,
                                "Вы вступили в группу '" + result.group().getName() + "'");
                    case NOT_FOUND -> new SendMessage(chatId, "Группа не найдена");
                };
            }
            case "leave" -> {
                Long groupId = parseGroupId(parts);
                if (groupId == null) {
                    yield new SendMessage(chatId, "Укажите ID группы (число): /group leave {id}");
                }
                GroupActionResult result = groupService.leaveGroup(userId, groupId);
                yield switch (result.status()) {
                    case OK ->
                        new SendMessage(
                                chatId, "Вы покинули группу '" + result.group().getName() + "'");
                    case NOT_FOUND -> new SendMessage(chatId, "Группа не найдена");
                };
            }
            case "list" -> {
                List<MemeGroup> userGroups = groupService.listGroups(userId);
                if (userGroups.isEmpty()) {
                    yield new SendMessage(chatId, "Вы не состоите ни в одной группе.");
                }
                StringBuilder sb = new StringBuilder("Ваши группы:\n");
                for (MemeGroup g : userGroups) {
                    sb.append("- ")
                            .append(g.getName())
                            .append(" (ID: ")
                            .append(g.getId())
                            .append(")\n");
                }
                yield new SendMessage(chatId, sb.toString());
            }
            default -> new SendMessage(chatId, "Неизвестное действие. Доступно: create, join, leave, list");
        };
    }

    private Long parseGroupId(String[] parts) {
        if (parts.length < 2) {
            return null;
        }
        try {
            return Long.parseLong(parts[1].trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
