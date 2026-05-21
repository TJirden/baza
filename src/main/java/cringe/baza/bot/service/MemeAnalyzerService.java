package cringe.baza.bot.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemeAnalyzerService {

    private final ChatModel chatModel;
    private final TelegramFileService fileService;

    /**
     * Скачивает изображение мема по telegram fileId, отправляет его в Ollama Vision модель
     * и возвращает текстовое описание мема на русском языке.
     */
    public String analyzeMeme(String fileId) {
        log.info("Начало анализа мема ИИ для fileId: {}", fileId);
        try (InputStream inputStream = fileService.downloadFile(fileId)) {
            if (inputStream == null) {
                throw new IOException("Не удалось скачать файл из Telegram");
            }
            byte[] imageBytes = inputStream.readAllBytes();
            Resource imageResource = new ByteArrayResource(imageBytes);

            UserMessage userMessage = UserMessage.builder()
                    .text(
                            "Ты — ИИ-помощник по анализу и тегированию мемов. Опиши это изображение: "
                                    + "1. Опиши происходящее на картинке (персонажи, эмоции, объекты). "
                                    + "2. Дословно распознай весь текст, написанный на изображении. "
                                    + "3. Напиши ключевые слова/теги для поиска. "
                                    + "Будь лаконичен (2-4 предложения). Ответ напиши СТРОГО на русском языке. Не добавляй лишних вводных фраз.")
                    .media(new Media(MimeTypeUtils.IMAGE_JPEG, imageResource))
                    .build();

            log.info("Отправка запроса в Ollama Vision...");
            var response = chatModel.call(new Prompt(List.of(userMessage)));
            String aiDescription = response.getResult().getOutput().getText();
            log.info("Анализ мема ИИ успешно завершен: {}", aiDescription);
            return aiDescription;
        } catch (Exception e) {
            log.error("Ошибка при анализе мема через ИИ: {}", e.getMessage(), e);
            throw new RuntimeException("Ошибка ИИ при анализе изображения: " + e.getMessage(), e);
        }
    }

    public CensorshipResult checkCensorship(String fileId) {
        log.info("Запуск ИИ-цензуры для fileId: {}", fileId);
        try (InputStream inputStream = fileService.downloadFile(fileId)) {
            if (inputStream == null) {
                throw new IOException("Не удалось скачать файл из Telegram для цензуры");
            }
            byte[] imageBytes = inputStream.readAllBytes();
            Resource imageResource = new ByteArrayResource(imageBytes);

            UserMessage userMessage = UserMessage.builder()
                    .text("Ты — ИИ-модератор мемов. Оцени, безопасен ли данный мем. "
                            + "Критерии блокировки: откровенная эротика/нагота (NSFW), сильная жестокость, тяжелые оскорбления, разжигание ненависти или пропаганда вредных веществ. "
                            + "Ответь СТРОГО в следующем формате:\n"
                            + "SAFE: <TRUE или FALSE>\n"
                            + "REASON: <краткая причина на русском языке в случае FALSE, иначе пусто>")
                    .media(new Media(MimeTypeUtils.IMAGE_JPEG, imageResource))
                    .build();

            log.info("Отправка запроса цензуры в Ollama...");
            var response = chatModel.call(new Prompt(List.of(userMessage)));
            String reply = response.getResult().getOutput().getText();
            log.info("Ответ ИИ-цензуры: {}", reply);

            boolean safe = true;
            String reason = "";

            if (reply != null) {
                String[] lines = reply.split("\n");
                for (String line : lines) {
                    String trimmed = line.trim();
                    if (trimmed.toUpperCase().startsWith("SAFE:")) {
                        String val = trimmed.substring(5).trim().toUpperCase();
                        if (val.contains("FALSE")) {
                            safe = false;
                        }
                    } else if (trimmed.toUpperCase().startsWith("REASON:")) {
                        reason = trimmed.substring(7).trim();
                    }
                }
            }

            return new CensorshipResult(safe, reason);
        } catch (Exception e) {
            log.error("Ошибка при проверке цензуры мема: {}", e.getMessage(), e);
            return new CensorshipResult(true, "");
        }
    }

    public record CensorshipResult(boolean safe, String reason) {}
}
