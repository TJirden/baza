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

    public record MemeAnalysis(String ocrText, String description) {}

    /**
     * Скачивает изображение мема по telegram fileId, отправляет его в Ollama Vision модель
     * и возвращает структурированный результат анализа (OCR-текст и описание).
     */
    public MemeAnalysis analyzeMemeDetails(String fileId) {
        log.info("Начало детального анализа мема ИИ для fileId: {}", fileId);
        try (InputStream inputStream = fileService.downloadFile(fileId)) {
            if (inputStream == null) {
                throw new IOException("Не удалось скачать файл из Telegram");
            }
            byte[] imageBytes = inputStream.readAllBytes();
            Resource imageResource = new ByteArrayResource(imageBytes);

            UserMessage userMessage = UserMessage.builder()
                    .text(
                            "Extract any visible text exactly as OCR. Describe the rest of the image contextually in Russian. "
                                    + "You MUST reply STRICTLY in this format:\n"
                                    + "TEXT: <exact text on image or EMPTY if none>\n"
                                    + "DESCRIPTION: <visual description of the image in Russian>")
                    .media(new Media(MimeTypeUtils.IMAGE_JPEG, imageResource))
                    .build();

            log.info("Отправка запроса детального анализа в Ollama Vision...");
            var response = chatModel.call(new Prompt(List.of(userMessage)));
            String reply = response.getResult().getOutput().getText();
            log.info("Ответ Ollama Vision:\n{}", reply);

            String ocrText = "";
            String description = "";

            if (reply != null) {
                String[] lines = reply.split("\n");
                StringBuilder descBuilder = new StringBuilder();
                boolean buildingDesc = false;

                for (String line : lines) {
                    String trimmed = line.trim();
                    if (trimmed.toUpperCase().startsWith("TEXT:")) {
                        ocrText = trimmed.substring(5).trim();
                        buildingDesc = false;
                    } else if (trimmed.toUpperCase().startsWith("DESCRIPTION:")) {
                        descBuilder.append(trimmed.substring(12).trim());
                        buildingDesc = true;
                    } else if (buildingDesc) {
                        if (!descBuilder.isEmpty()) {
                            descBuilder.append("\n");
                        }
                        descBuilder.append(trimmed);
                    }
                }
                description = descBuilder.toString().trim();
            }

            if (ocrText.equalsIgnoreCase("EMPTY") || ocrText.equalsIgnoreCase("<EMPTY>")) {
                ocrText = "";
            }

            if (description.isBlank()) {
                description = "Без описания";
            }

            return new MemeAnalysis(ocrText, description);
        } catch (Exception e) {
            log.error("Ошибка при детальном анализе мема через ИИ: {}", e.getMessage(), e);
            throw new RuntimeException("Ошибка ИИ при анализе изображения: " + e.getMessage(), e);
        }
    }

    /**
     * Сохраняет обратную совместимость, возвращая объединенное описание мема.
     */
    public String analyzeMeme(String fileId) {
        MemeAnalysis analysis = analyzeMemeDetails(fileId);
        if (analysis.ocrText().isBlank()) {
            return analysis.description();
        }
        return analysis.description() + " [Текст]: " + analysis.ocrText();
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
                    .text("You are an AI meme moderator. Assess if this meme is safe. "
                            + "Block criteria: explicit erotica/nudity (NSFW), violence, severe insults, hate speech, or drug/illegal substance promotion. "
                            + "Respond STRICTLY in this format:\n"
                            + "SAFE: <TRUE or FALSE>\n"
                            + "REASON: <short reason for block in English if FALSE, otherwise empty>")
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
