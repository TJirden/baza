package cringe.baza.meme;

import com.pengrad.telegrambot.model.PhotoSize;
import cringe.baza.bot.service.TelegramFileService;
import cringe.baza.bot.service.TelegramService;
import cringe.baza.domain.MemeModeration;
import cringe.baza.meme.phash.MemeImageHasher;
import cringe.baza.model.IdRepository;
import cringe.baza.model.Meme;
import cringe.baza.model.MemeVisibility;
import cringe.baza.model.ModerationStatus;
import cringe.baza.repository.jpa.MemeImageHashRepository;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncMemeService {

    private final TelegramService telegramService;
    private final MemeProcessor memeProcessor;
    private final TelegramFileService fileService;
    private final MemeAnalyzerService memeAnalyzerService;
    private final IdRepository memeRepository;
    private final MemeImageHasher memeImageHasher;
    private final MemeImageHashRepository memeImageHashRepository;

    @Value("${app.dedup.text-threshold}")
    private double textDedupThreshold;

    @Value("${app.dedup.image-phash-threshold}")
    private int imagePhashThreshold;

    @Async("memeAsyncExecutor")
    public void saveMemeAsync(
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
            MemeVisibility visibility = parseVisibilityAndGroups(visibilityContext, groupIds);
            String groupIdsStr = groupIds.stream().map(String::valueOf).collect(Collectors.joining(","));

            String memeId = UUID.randomUUID().toString();

            telegramService.editMessageText(chatId, messageIdToEdit, "Загружаю изображение...");
            byte[] imageBytes = fileService.downloadFileBytes(fileId);
            if (imageBytes == null || imageBytes.length == 0) {
                throw new IOException("Не удалось скачать изображение из Telegram");
            }

            telegramService.editMessageText(chatId, messageIdToEdit, "Вычисляю визуальный хеш...");
            OptionalLong imageHash = computeHashOrThrow(imageBytes);

            if (checkImageDuplicateAndQuarantine(
                    chatId,
                    messageIdToEdit,
                    memeId,
                    fileId,
                    description != null ? description : "",
                    "",
                    userId,
                    visibility,
                    groupIdsStr,
                    imageHash.getAsLong())) {
                return;
            }

            String ocrText = "";
            String aiDescription = "";

            try {
                telegramService.editMessageText(chatId, messageIdToEdit, "Анализирую изображение с помощью ИИ...");
                MemeAnalyzerService.MemeAnalysis analysis = memeAnalyzerService.analyzeMemeDetails(imageBytes);
                ocrText = analysis.ocrText();
                aiDescription = analysis.description();
            } catch (Exception e) {
                log.warn("Не удалось выполнить ИИ-анализ мема: {}", e.getMessage());
                if (description == null || description.isBlank()) {
                    throw new RuntimeException("ИИ-анализ обязателен для мемов без описания", e);
                }
            }

            String finalDescription;
            if (description == null || description.isBlank()) {
                finalDescription = aiDescription;
            } else {
                if (aiDescription != null && !aiDescription.isBlank()) {
                    finalDescription = description + "\n\n[ИИ-Теги]: " + aiDescription;
                } else {
                    finalDescription = description;
                }
            }

            if (checkCensorshipAndQuarantine(
                    chatId,
                    messageIdToEdit,
                    memeId,
                    fileId,
                    finalDescription,
                    ocrText,
                    userId,
                    visibility,
                    groupIdsStr,
                    imageBytes,
                    imageHash.getAsLong())) {
                return;
            }

            if (checkDuplicateAndQuarantine(
                    chatId,
                    messageIdToEdit,
                    memeId,
                    fileId,
                    finalDescription,
                    ocrText,
                    userId,
                    visibility,
                    groupIdsStr,
                    imageHash.getAsLong())) {
                return;
            }

            saveApprovedMeme(
                    chatId,
                    messageIdToEdit,
                    memeId,
                    fileId,
                    finalDescription,
                    ocrText,
                    userId,
                    visibility,
                    groupIds,
                    groupIdsStr,
                    imageHash);

        } catch (Exception e) {
            log.error("Критическая ошибка при асинхронном сохранении изображения: {}", e.getMessage(), e);
            try {
                telegramService.editMessageTextWithMarkdown(
                        chatId, messageIdToEdit, "*Ошибка сохранения мема:* " + e.getMessage());
            } catch (Exception ex) {
                log.error("Не удалось отправить сообщение об ошибке пользователю: {}", ex.getMessage());
            }
        }
    }

    private MemeVisibility parseVisibilityAndGroups(String visibilityContext, List<Long> groupIds) {
        if (visibilityContext != null) {
            if (visibilityContext.startsWith("GROUP:")) {
                String[] parts = visibilityContext.substring(6).split(",");
                for (String part : parts) {
                    try {
                        groupIds.add(Long.parseLong(part));
                    } catch (NumberFormatException ignored) {
                    }
                }
                return MemeVisibility.GROUP;
            }
            try {
                return MemeVisibility.valueOf(visibilityContext.toUpperCase());
            } catch (IllegalArgumentException e) {
                return MemeVisibility.PUBLIC;
            }
        }
        return MemeVisibility.PUBLIC;
    }

    private boolean checkCensorshipAndQuarantine(
            long chatId,
            int messageIdToEdit,
            String memeId,
            String fileId,
            String finalDescription,
            String ocrText,
            long userId,
            MemeVisibility visibility,
            String groupIdsStr,
            byte[] imageBytes,
            long imageHash) {

        telegramService.editMessageText(chatId, messageIdToEdit, "Проверяю мем на цензуру с помощью ИИ...");
        MemeAnalyzerService.CensorshipResult censorship = memeAnalyzerService.checkCensorship(imageBytes);

        if (!censorship.safe()) {
            log.warn("Мем {} не прошел цензуру ИИ. Причина: {}", memeId, censorship.reason());
            MemeModeration moderation = new MemeModeration(
                    memeId,
                    fileId,
                    finalDescription,
                    ocrText,
                    userId,
                    visibility,
                    groupIdsStr,
                    ModerationStatus.QUARANTINED,
                    "ИИ-цензура: " + censorship.reason());
            memeRepository.saveQuarantined(moderation, OptionalLong.of(imageHash));

            String text = "*Мем помещен в карантин по соображениям ИИ-цензуры!*\n\n"
                    + "*Причина:* " + censorship.reason() + "\n"
                    + "Мем будет доступен только после ручного одобрения модератором.\n\n"
                    + "*ID мема:* `" + memeId + "`";

            telegramService.editMessageTextWithMarkdown(chatId, messageIdToEdit, text);
            return true;
        }
        return false;
    }

    private boolean checkImageDuplicateAndQuarantine(
            long chatId,
            int messageIdToEdit,
            String memeId,
            String fileId,
            String finalDescription,
            String ocrText,
            long userId,
            MemeVisibility visibility,
            String groupIdsStr,
            long imageHash) {

        telegramService.editMessageText(chatId, messageIdToEdit, "Проверяю визуальные дубликаты...");
        try {
            Optional<String> duplicateIdOpt =
                    memeImageHashRepository.findNearestApproved(imageHash, imagePhashThreshold);
            if (duplicateIdOpt.isPresent()) {
                String duplicateId = duplicateIdOpt.get();
                log.warn("Обнаружен визуальный дубликат мема {}. Оригинал: {}", memeId, duplicateId);
                MemeModeration moderation = new MemeModeration(
                        memeId,
                        fileId,
                        finalDescription,
                        ocrText,
                        userId,
                        visibility,
                        groupIdsStr,
                        ModerationStatus.QUARANTINED,
                        "Визуальный дубликат мема: " + duplicateId);
                memeRepository.saveQuarantined(moderation, OptionalLong.of(imageHash));

                String text = "*Обнаружен визуальный дубликат оригинального мема!*\n\n"
                        + "Ваш мем визуально совпадает с существующим мемом (ID: `" + duplicateId + "`).\n"
                        + "Мем сохранен в карантин до подтверждения модератором.\n\n"
                        + "*ID вашего мема:* `" + memeId + "`";

                telegramService.editMessageTextWithMarkdown(chatId, messageIdToEdit, text);
                return true;
            }
        } catch (Exception e) {
            log.warn("Ошибка при визуальной проверке дубликатов мема {}: {}", memeId, e.getMessage());
            MemeModeration moderation = new MemeModeration(
                    memeId,
                    fileId,
                    finalDescription,
                    ocrText,
                    userId,
                    visibility,
                    groupIdsStr,
                    ModerationStatus.QUARANTINED,
                    "Ошибка визуальной проверки дубликатов: " + e.getMessage());
            memeRepository.saveQuarantined(moderation, OptionalLong.of(imageHash));

            String text = "*Не удалось проверить мем на визуальные дубликаты.*\n\n"
                    + "Мем сохранен в карантин до подтверждения модератором.\n\n"
                    + "*ID вашего мема:* `" + memeId + "`";

            telegramService.editMessageTextWithMarkdown(chatId, messageIdToEdit, text);
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
            String ocrText,
            long userId,
            MemeVisibility visibility,
            String groupIdsStr,
            long imageHash) {

        telegramService.editMessageText(chatId, messageIdToEdit, "Проверяю на наличие дубликатов в базе...");
        Optional<String> duplicateIdOpt;
        try {
            duplicateIdOpt = memeRepository.findDuplicateMemeId(finalDescription, textDedupThreshold);
        } catch (Exception e) {
            log.warn("Ошибка при текстовой проверке дубликатов мема {}: {}", memeId, e.getMessage());
            MemeModeration moderation = new MemeModeration(
                    memeId,
                    fileId,
                    finalDescription,
                    ocrText,
                    userId,
                    visibility,
                    groupIdsStr,
                    ModerationStatus.QUARANTINED,
                    "Ошибка текстовой проверки дубликатов: " + e.getMessage());
            memeRepository.saveQuarantined(moderation, OptionalLong.of(imageHash));

            String text = "*Не удалось проверить мем на текстовые дубликаты.*\n\n"
                    + "Мем сохранен в карантин до подтверждения модератором.\n\n"
                    + "*ID вашего мема:* `" + memeId + "`";

            telegramService.editMessageTextWithMarkdown(chatId, messageIdToEdit, text);
            return true;
        }

        if (duplicateIdOpt.isPresent()) {
            String duplicateId = duplicateIdOpt.get();
            log.warn("Обнаружен дубликат мема {}. Оригинал: {}", memeId, duplicateId);
            MemeModeration moderation = new MemeModeration(
                    memeId,
                    fileId,
                    finalDescription,
                    ocrText,
                    userId,
                    visibility,
                    groupIdsStr,
                    ModerationStatus.QUARANTINED,
                    "Дубликат мема: " + duplicateId);
            memeRepository.saveQuarantined(moderation, OptionalLong.of(imageHash));

            String text = "*Обнаружен дубликат оригинального мема!*\n\n"
                    + "Ваш мем совпадает с существующим мемом (ID: `" + duplicateId + "`).\n"
                    + "Мем сохранен в карантин до подтверждения модератором.\n\n"
                    + "*ID вашего мема:* `" + memeId + "`";

            telegramService.editMessageTextWithMarkdown(chatId, messageIdToEdit, text);
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
            String ocrText,
            long userId,
            MemeVisibility visibility,
            List<Long> groupIds,
            String groupIdsStr,
            OptionalLong imageHash) {

        String imageId = memeProcessor.save(
                new Meme(memeId, finalDescription, ocrText, fileId, userId, visibility, groupIds), imageHash);
        log.info("Мем успешно сохранен и проиндексирован. ID: {}", imageId);

        String text = "*Мем успешно сохранен!*\n\n"
                + "*ID*: `" + imageId + "`\n"
                + "*Описание*: " + finalDescription + "\n"
                + "*Доступ*: " + visibility;

        telegramService.editMessageTextWithMarkdown(chatId, messageIdToEdit, text);
    }

    private OptionalLong computeHashOrThrow(byte[] imageBytes) {
        OptionalLong hash = memeImageHasher.computeHash(imageBytes);
        if (hash.isEmpty()) {
            throw new RuntimeException("Не удалось вычислить perceptual hash изображения");
        }
        return hash;
    }
}
