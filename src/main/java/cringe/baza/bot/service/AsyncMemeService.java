package cringe.baza.bot.service;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.PhotoSize;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.EditMessageText;
import cringe.baza.domain.MemeModeration;
import cringe.baza.model.Meme;
import cringe.baza.processor.MemeProcessor;
import cringe.baza.repository.MemeVectorRepository;
import cringe.baza.repository.jpa.MemeModerationRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncMemeService {

    private final TelegramBot bot;
    private final MemeProcessor memeProcessor;
    private final TelegramFileService fileService;
    private final MemeAnalyzerService memeAnalyzerService;
    private final MemeVectorRepository memeVectorRepository;
    private final MemeModerationRepository memeModerationRepository;

    @Async("memeAsyncExecutor")
    public void processAndSaveMemeAsync(
            long chatId,
            long userId,
            PhotoSize[] photo,
            String description,
            String visibilityContext,
            int messageIdToEdit) {
        try {
            log.info("Начало асинхронного сохранения мема для chatId={}, userId={}", chatId, userId);

            String fileId = fileService.getImageFileId(photo);
            if (fileId == null) {
                throw new IllegalArgumentException("Не удалось получить fileId для изображения");
            }

            List<Long> groupIds = new ArrayList<>();
            String visibility = parseVisibilityAndGroups(visibilityContext, groupIds);
            String groupIdsStr = groupIds.stream().map(String::valueOf).collect(Collectors.joining(","));

            String memeId = UUID.randomUUID().toString();
            String finalDescription = getFinalDescription(chatId, messageIdToEdit, fileId, description);

            if (checkCensorshipAndQuarantine(
                    chatId, messageIdToEdit, memeId, fileId, finalDescription, userId, visibility, groupIdsStr)) {
                return;
            }

            if (checkDuplicateAndQuarantine(
                    chatId, messageIdToEdit, memeId, fileId, finalDescription, userId, visibility, groupIdsStr)) {
                return;
            }

            saveApprovedMeme(
                    chatId,
                    messageIdToEdit,
                    memeId,
                    fileId,
                    finalDescription,
                    userId,
                    visibility,
                    groupIds,
                    groupIdsStr);

        } catch (Exception e) {
            log.error("Критическая ошибка при асинхронном сохранении изображения: {}", e.getMessage(), e);
            try {
                bot.execute(new EditMessageText(chatId, messageIdToEdit, "*Ошибка сохранения мема:* " + e.getMessage())
                        .parseMode(ParseMode.Markdown));
            } catch (Exception ex) {
                log.error("Не удалось отправить сообщение об ошибке пользователю: {}", ex.getMessage());
            }
        }
    }

    private String parseVisibilityAndGroups(String visibilityContext, List<Long> groupIds) {
        if (visibilityContext != null) {
            if (visibilityContext.startsWith("GROUP:")) {
                String[] parts = visibilityContext.substring(6).split(",");
                for (String part : parts) {
                    try {
                        groupIds.add(Long.parseLong(part));
                    } catch (NumberFormatException ignored) {
                    }
                }
                return "GROUP";
            }
            return visibilityContext;
        }
        return "PUBLIC";
    }

    private String getFinalDescription(long chatId, int messageIdToEdit, String fileId, String description) {
        if (description == null || description.isBlank()) {
            bot.execute(new EditMessageText(chatId, messageIdToEdit, "Анализирую изображение с помощью ИИ..."));
            String aiDesc = memeAnalyzerService.analyzeMeme(fileId);
            return (aiDesc != null && !aiDesc.isBlank()) ? aiDesc : "Без описания";
        } else {
            bot.execute(new EditMessageText(chatId, messageIdToEdit, "Сохраняю и обогащаю описание с помощью ИИ..."));
            try {
                String aiTags = memeAnalyzerService.analyzeMeme(fileId);
                if (aiTags != null && !aiTags.isBlank()) {
                    return description + "\n\n[ИИ-Теги]: " + aiTags;
                }
                return description;
            } catch (Exception e) {
                log.warn("Не удалось обогатить мем с помощью ИИ, сохраняю оригинальное описание: {}", e.getMessage());
                return description;
            }
        }
    }

    private boolean checkCensorshipAndQuarantine(
            long chatId,
            int messageIdToEdit,
            String memeId,
            String fileId,
            String finalDescription,
            long userId,
            String visibility,
            String groupIdsStr) {

        bot.execute(new EditMessageText(chatId, messageIdToEdit, "Проверяю мем на цензуру с помощью ИИ..."));
        MemeAnalyzerService.CensorshipResult censorship = memeAnalyzerService.checkCensorship(fileId);

        if (!censorship.safe()) {
            log.warn("Мем {} не прошел цензуру ИИ. Причина: {}", memeId, censorship.reason());
            MemeModeration moderation = new MemeModeration(
                    memeId,
                    fileId,
                    finalDescription,
                    userId,
                    visibility,
                    groupIdsStr,
                    "QUARANTINED",
                    "ИИ-цензура: " + censorship.reason());
            memeModerationRepository.save(moderation);

            String text = "*Мем помещен в карантин по соображениям ИИ-цензуры!*\n\n"
                    + "*Причина:* " + censorship.reason() + "\n"
                    + "Мем будет доступен только после ручного одобрения модератором.\n\n"
                    + "*ID мема:* `" + memeId + "`";

            bot.execute(new EditMessageText(chatId, messageIdToEdit, text).parseMode(ParseMode.Markdown));
            return true;
        }
        return false;
    }

    private boolean checkDuplicateAndQuarantine(
            long chatId,
            int messageIdToEdit,
            String memeId,
            String fileId,
            String finalDescription,
            long userId,
            String visibility,
            String groupIdsStr) {

        bot.execute(new EditMessageText(chatId, messageIdToEdit, "Проверяю на наличие дубликатов в базе..."));
        Optional<String> duplicateIdOpt = memeVectorRepository.findDuplicateMemeId(finalDescription, 0.95);

        if (duplicateIdOpt.isPresent()) {
            String duplicateId = duplicateIdOpt.get();
            log.warn("Обнаружен дубликат мема {}. Оригинал: {}", memeId, duplicateId);
            MemeModeration moderation = new MemeModeration(
                    memeId,
                    fileId,
                    finalDescription,
                    userId,
                    visibility,
                    groupIdsStr,
                    "QUARANTINED",
                    "Дубликат мема: " + duplicateId);
            memeModerationRepository.save(moderation);

            String text = "*Обнаружен дубликат оригинального мема!*\n\n"
                    + "Ваш мем совпадает с существующим мемом (ID: `" + duplicateId + "`).\n"
                    + "Мем сохранен в карантин до подтверждения модератором.\n\n"
                    + "*ID вашего мема:* `" + memeId + "`";

            bot.execute(new EditMessageText(chatId, messageIdToEdit, text).parseMode(ParseMode.Markdown));
            return true;
        }
        return false;
    }

    private void saveApprovedMeme(
            long chatId,
            int messageIdToEdit,
            String memeId,
            String fileId,
            String finalDescription,
            long userId,
            String visibility,
            List<Long> groupIds,
            String groupIdsStr) {

        MemeModeration moderation =
                new MemeModeration(memeId, fileId, finalDescription, userId, visibility, groupIdsStr, "APPROVED", null);
        memeModerationRepository.save(moderation);

        String imageId = memeProcessor.save(new Meme(memeId, finalDescription, fileId, userId, visibility, groupIds));
        log.info("Мем успешно сохранен и проиндексирован. ID: {}", imageId);

        String text = "*Мем успешно сохранен!*\n\n"
                + "*ID*: `" + imageId + "`\n"
                + "*Описание*: " + finalDescription + "\n"
                + "*Доступ*: " + visibility;

        bot.execute(new EditMessageText(chatId, messageIdToEdit, text).parseMode(ParseMode.Markdown));
    }
}
