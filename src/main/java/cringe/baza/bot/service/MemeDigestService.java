package cringe.baza.bot.service;

import cringe.baza.domain.MemeGroup;
import cringe.baza.domain.MemeModeration;
import cringe.baza.domain.MemeRating;
import cringe.baza.domain.TelegramUser;
import cringe.baza.model.ModerationStatus;
import cringe.baza.repository.jpa.MemeGroupRepository;
import cringe.baza.repository.jpa.MemeModerationRepository;
import cringe.baza.repository.jpa.MemeRatingRepository;
import cringe.baza.repository.jpa.TelegramUserRepository;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemeDigestService {

    private final MemeGroupRepository groupRepository;
    private final MemeModerationRepository moderationRepository;
    private final MemeRatingRepository ratingRepository;
    private final TelegramUserRepository userRepository;
    private final ChatModel chatModel;
    private final TelegramService telegramService;

    @Value("${app.digest.days:7}")
    private int digestDays;

    @Transactional(readOnly = true)
    public void runAllGroupDigests() {
        log.info("Starting automated meme digest generation for all groups...");
        List<MemeGroup> groups = groupRepository.findAll();
        for (MemeGroup group : groups) {
            try {
                generateAndSendDigestForGroup(group);
            } catch (Exception e) {
                log.error("Failed to generate digest for group {}: {}", group.getId(), e.getMessage(), e);
            }
        }
        log.info("Automated meme digest generation finished.");
    }

    @Transactional(readOnly = true)
    public void generateAndSendDigestForGroup(MemeGroup group) {
        log.info("Generating digest for group: {} (ID: {})", group.getName(), group.getId());
        List<MemeModeration> topMemes = getTopMemesForGroup(group.getId());

        if (topMemes.isEmpty()) {
            log.info("No approved memes found in the last {} days for group: {}", digestDays, group.getName());
            return;
        }

        String digestText = generateDigestTextWithAI(group.getName(), topMemes);

        for (TelegramUser member : group.getMembers()) {
            sendDigestToUser(member.getId(), digestText, topMemes);
        }
    }

    @Transactional(readOnly = true)
    public List<MemeModeration> getTopMemesForGroup(Long groupId) {
        LocalDateTime threshold = LocalDateTime.now().minusDays(digestDays);
        List<MemeModeration> recentMemes =
                moderationRepository.findByStatusAndCreatedAtAfter(ModerationStatus.APPROVED, threshold);

        List<MemeModeration> groupMemes = recentMemes.stream()
                .filter(m -> {
                    if (m.getGroupIds() == null || m.getGroupIds().isBlank()) {
                        return false;
                    }
                    return Arrays.stream(m.getGroupIds().split(","))
                            .map(String::trim)
                            .anyMatch(idStr -> idStr.equals(String.valueOf(groupId)));
                })
                .toList();

        if (groupMemes.isEmpty()) {
            return List.of();
        }

        List<String> memeIds = groupMemes.stream().map(MemeModeration::getId).toList();
        Map<String, Integer> eloMap = ratingRepository.findAllById(memeIds).stream()
                .collect(Collectors.toMap(MemeRating::getMemeId, MemeRating::getEloRating));

        return groupMemes.stream()
                .sorted((m1, m2) -> {
                    int elo1 = eloMap.getOrDefault(m1.getId(), 1000);
                    int elo2 = eloMap.getOrDefault(m2.getId(), 1000);
                    return Integer.compare(elo2, elo1); // Descending order
                })
                .limit(3)
                .toList();
    }

    public String generateDigestTextWithAI(String groupName, List<MemeModeration> topMemes) {
        StringBuilder promptText = new StringBuilder();
        promptText
                .append("Ты — ироничный, харизматичный и остроумный ИИ-редактор телеграм-канала о мемах.\n")
                .append("Твоя задача — составить еженедельный дайджест лучших мемов для мем-группы под названием \"")
                .append(groupName)
                .append("\".\n\n")
                .append("Вот список топ-мемов в порядке убывания их популярности:\n");

        for (int i = 0; i < topMemes.size(); i++) {
            MemeModeration meme = topMemes.get(i);
            promptText
                    .append(i + 1)
                    .append(". Мем от автора @")
                    .append(getOwnerUsername(meme.getOwnerId()))
                    .append(":\n")
                    .append("   - Текст на картинке (OCR): \"")
                    .append(meme.getOcrText())
                    .append("\"\n")
                    .append("   - Визуальное описание мема: \"")
                    .append(meme.getDescription())
                    .append("\"\n\n");
        }

        promptText
                .append("Требования к оформлению:\n")
                .append(
                        "1. Напиши смешную, ироничную и бодрую вступительную подводку (2-4 предложения) в стиле ведущего шоу.\n")
                .append(
                        "2. Для каждого мема напиши короткий (1-2 предложения) забавный комментарий, подстегивающий автора или объясняющий локальный юмор в шутливой манере.\n")
                .append(
                        "3. Используй стандартную разметку Markdown (например, *жирный* для выделения номеров мемов, авторов или важных моментов).\n")
                .append(
                        "4. Отвечай СТРОГО готовым текстом дайджеста на русском языке, без мета-комментариев вроде \"Вот ваш дайджест:\".");

        try {
            UserMessage userMessage =
                    UserMessage.builder().text(promptText.toString()).build();
            var response = chatModel.call(new Prompt(List.of(userMessage)));
            String text = response.getResult().getOutput().getText();
            if (text == null || text.isBlank()) {
                return getDefaultDigestText(groupName, topMemes);
            }
            return text;
        } catch (Exception e) {
            log.error("AI digest generation failed, using fallback: {}", e.getMessage());
            return getDefaultDigestText(groupName, topMemes);
        }
    }

    public void sendDigestToUser(Long userId, String digestText, List<MemeModeration> topMemes) {
        try {
            // 1. Отправляем текстовый дайджест
            telegramService.sendMessageWithMarkdown(userId, digestText);

            // 2. Отправляем фотографии лучших мемов
            for (int i = 0; i < topMemes.size(); i++) {
                MemeModeration meme = topMemes.get(i);
                String caption = (i + 1) + "-е место. Мем от автора @" + getOwnerUsername(meme.getOwnerId());
                telegramService.sendPhoto(userId, meme.getFileId(), caption);
            }
        } catch (Exception e) {
            log.error("Failed to send digest to user {}: {}", userId, e.getMessage(), e);
        }
    }

    private String getOwnerUsername(Long ownerId) {
        if (ownerId == null) {
            return "Аноним";
        }
        return userRepository
                .findById(ownerId)
                .map(u -> u.getUsername() != null && !u.getUsername().isBlank() ? u.getUsername() : u.getFirstName())
                .orElse("Аноним");
    }

    private String getDefaultDigestText(String groupName, List<MemeModeration> topMemes) {
        StringBuilder sb = new StringBuilder();
        sb.append("🔥 *Дайджест лучших мемов группы \"")
                .append(groupName)
                .append("\"!*\n\n")
                .append("Вот лучшие шедевры за последнее время:\n\n");

        for (int i = 0; i < topMemes.size(); i++) {
            MemeModeration meme = topMemes.get(i);
            sb.append("*")
                    .append(i + 1)
                    .append(" место* — автор: @")
                    .append(getOwnerUsername(meme.getOwnerId()))
                    .append("\n")
                    .append("📝 _Описание:_ ")
                    .append(meme.getDescription())
                    .append("\n\n");
        }
        return sb.toString();
    }
}
