package cringe.baza.bot.command;

import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.BaseRequest;
import com.pengrad.telegrambot.request.SendMessage;
import cringe.baza.bot.service.TelegramUserService;
import cringe.baza.domain.MemeGroup;
import cringe.baza.domain.TelegramUser;
import cringe.baza.repository.jpa.MemeGroupRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class GroupCommand implements Command {

    private final MemeGroupRepository groupRepository;
    private final TelegramUserService userService;

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
        String text = extractText(update.message().text());

        if (text == null || text.isBlank()) {
            return new SendMessage(
                    chatId, "Использование:\n/group create {имя}\n/group join {id}\n/group leave {id}\n/group list");
        }

        String[] parts = text.split("\\s+", 2);
        String action = parts[0].toLowerCase();

        TelegramUser user = userService.getOrCreateUser(
                userId,
                update.message().from().username(),
                update.message().from().firstName());

        try {
            switch (action) {
                case "create":
                    if (parts.length < 2) return new SendMessage(chatId, "Укажите имя группы: /group create {имя}");
                    MemeGroup group = new MemeGroup();
                    group.setName(parts[1]);
                    group.setOwner(user);
                    group.getMembers().add(user);
                    groupRepository.save(group);
                    return new SendMessage(chatId, "Группа '" + parts[1] + "' создана! ID: " + group.getId());

                case "join":
                    if (parts.length < 2) return new SendMessage(chatId, "Укажите ID группы: /group join {id}");
                    Long joinId = Long.parseLong(parts[1]);
                    Optional<MemeGroup> joinGroupOpt = groupRepository.findById(joinId);
                    if (joinGroupOpt.isEmpty()) return new SendMessage(chatId, "Группа не найдена");
                    MemeGroup joinGroup = joinGroupOpt.get();
                    joinGroup.getMembers().add(user);
                    groupRepository.save(joinGroup);
                    return new SendMessage(chatId, "Вы вступили в группу '" + joinGroup.getName() + "'");

                case "leave":
                    if (parts.length < 2) return new SendMessage(chatId, "Укажите ID группы: /group leave {id}");
                    Long leaveId = Long.parseLong(parts[1]);
                    Optional<MemeGroup> leaveGroupOpt = groupRepository.findById(leaveId);
                    if (leaveGroupOpt.isEmpty()) return new SendMessage(chatId, "Группа не найдена");
                    MemeGroup leaveGroup = leaveGroupOpt.get();
                    leaveGroup.getMembers().remove(user);
                    groupRepository.save(leaveGroup);
                    return new SendMessage(chatId, "Вы покинули группу '" + leaveGroup.getName() + "'");

                case "list":
                    List<MemeGroup> userGroups = groupRepository.findByMembersContains(user);
                    if (userGroups.isEmpty()) {
                        return new SendMessage(chatId, "Вы не состоите ни в одной группе.");
                    }
                    StringBuilder sb = new StringBuilder("Ваши группы:\n");
                    for (MemeGroup g : userGroups) {
                        sb.append("- ")
                                .append(g.getName())
                                .append(" (ID: ")
                                .append(g.getId())
                                .append(")\n");
                    }
                    return new SendMessage(chatId, sb.toString());

                default:
                    return new SendMessage(chatId, "Неизвестное действие. Доступно: create, join, leave, list");
            }
        } catch (NumberFormatException e) {
            return new SendMessage(chatId, "ID группы должен быть числом.");
        } catch (Exception e) {
            return new SendMessage(chatId, "Ошибка: " + e.getMessage());
        }
    }
}
