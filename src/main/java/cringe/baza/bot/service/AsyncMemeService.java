package cringe.baza.bot.service;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.PhotoSize;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.EditMessageText;
import cringe.baza.model.Meme;
import cringe.baza.processor.MemeProcessor;
import java.util.ArrayList;
import java.util.List;
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

            String visibility = "PUBLIC";
            List<Long> groupIds = new ArrayList<>();

            if (visibilityContext != null) {
                if (visibilityContext.startsWith("GROUP:")) {
                    visibility = "GROUP";
                    String[] parts = visibilityContext.substring(6).split(",");
                    for (String part : parts) {
                        try {
                            groupIds.add(Long.parseLong(part));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                } else {
                    visibility = visibilityContext;
                }
            }

            String finalDescription;

            if (description == null || description.isBlank()) {
                bot.execute(new EditMessageText(chatId, messageIdToEdit, "🤖 Анализирую изображение с помощью ИИ..."));
                finalDescription = memeAnalyzerService.analyzeMeme(fileId);
            } else {
                bot.execute(new EditMessageText(
                        chatId, messageIdToEdit, "💾 Сохраняю и обогащаю описание с помощью ИИ..."));
                try {
                    String aiTags = memeAnalyzerService.analyzeMeme(fileId);
                    finalDescription = description + "\n\n[ИИ-Теги]: " + aiTags;
                } catch (Exception e) {
                    log.warn(
                            "Не удалось обогатить мем с помощью ИИ, сохраняю оригинальное описание: {}",
                            e.getMessage());
                    finalDescription = description;
                }
            }

            String imageId = memeProcessor.save(new Meme(null, finalDescription, fileId, userId, visibility, groupIds));
            log.info("Мем успешно сохранен в фоне. ID: {}", imageId);

            String text = "*Мем успешно сохранен!*\n\n"
                    + "*ID*: `"
                    + imageId
                    + "`\n"
                    + "*Описание*: "
                    + finalDescription
                    + "\n"
                    + "*Доступ*: "
                    + visibility;

            bot.execute(new EditMessageText(chatId, messageIdToEdit, text).parseMode(ParseMode.Markdown));

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
}
