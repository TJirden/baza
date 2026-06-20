package cringe.baza.bot.command;

import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.BaseRequest;
import com.pengrad.telegrambot.request.SendMessage;
import cringe.baza.bot.service.MemeDigestService;
import cringe.baza.domain.MemeGroup;
import cringe.baza.domain.MemeModeration;
import cringe.baza.domain.TelegramUser;
import cringe.baza.repository.jpa.MemeGroupRepository;
import cringe.baza.repository.jpa.TelegramUserRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class DigestCommand implements Command {

    private final MemeDigestService digestService;
    private final MemeGroupRepository groupRepository;
    private final TelegramUserRepository userRepository;

    @Override
    public String command() {
        return "digest";
    }

    @Override
    public String description() {
        return "Получить дайджест лучших мемов: /digest или /digest {id_группы}";
    }

    @Override
    public BaseRequest<?, ?> handle(Update update) {
        long chatId = update.message().chat().id();
        long userId = update.message().from().id();
        String param = extractText(update.message().text());

        Optional<TelegramUser> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return new SendMessage(chatId, "Вы не зарегистрированы в системе.");
        }
        TelegramUser user = userOpt.get();

        List<MemeGroup> userGroups = groupRepository.findByMembersContains(user);
        if (userGroups.isEmpty()) {
            return new SendMessage(
                    chatId,
                    "Вы не состоите ни в одной группе. Создайте группу через /group create или вступите через /group join.");
        }

        if (param != null && !param.isBlank()) {
            try {
                Long groupId = Long.parseLong(param);
                Optional<MemeGroup> groupOpt = userGroups.stream()
                        .filter(g -> g.getId().equals(groupId))
                        .findFirst();

                if (groupOpt.isEmpty()) {
                    return new SendMessage(chatId, "Вы не состоите в группе с ID " + groupId);
                }

                MemeGroup group = groupOpt.get();
                List<MemeModeration> topMemes = digestService.getTopMemesForGroup(group.getId());
                if (topMemes.isEmpty()) {
                    return new SendMessage(
                            chatId, "В группе \"" + group.getName() + "\" нет новых мемов за последнее время.");
                }

                String digestText = digestService.generateDigestTextWithAI(group.getName(), topMemes);
                digestService.sendDigestToUser(userId, digestText, topMemes);
                return null;

            } catch (NumberFormatException e) {
                return new SendMessage(chatId, "ID группы должен быть числом.");
            } catch (Exception e) {
                log.error("Failed to manually generate digest: {}", e.getMessage(), e);
                return new SendMessage(chatId, "Произошла ошибка при генерации дайджеста: " + e.getMessage());
            }
        }

        // Если ID не передан, но пользователь состоит ровно в одной группе
        if (userGroups.size() == 1) {
            MemeGroup group = userGroups.get(0);
            try {
                List<MemeModeration> topMemes = digestService.getTopMemesForGroup(group.getId());
                if (topMemes.isEmpty()) {
                    return new SendMessage(
                            chatId,
                            "В вашей единственной группе \"" + group.getName()
                                    + "\" нет новых мемов за последнее время.");
                }

                String digestText = digestService.generateDigestTextWithAI(group.getName(), topMemes);
                digestService.sendDigestToUser(userId, digestText, topMemes);
                return null;
            } catch (Exception e) {
                log.error("Failed to manually generate digest: {}", e.getMessage(), e);
                return new SendMessage(chatId, "Произошла ошибка при генерации дайджеста: " + e.getMessage());
            }
        }

        StringBuilder sb = new StringBuilder("Вы состоите в нескольких группах. Уточните ID группы:\n\n");
        for (MemeGroup g : userGroups) {
            sb.append("- ")
                    .append(g.getName())
                    .append(" (ID: `")
                    .append(g.getId())
                    .append("`)\n");
        }
        sb.append("\nИспользование: `/digest {ID}`");
        return new SendMessage(chatId, sb.toString()).parseMode(ParseMode.Markdown);
    }
}
