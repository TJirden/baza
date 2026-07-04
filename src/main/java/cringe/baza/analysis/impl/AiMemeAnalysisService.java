package cringe.baza.analysis.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import cringe.baza.analysis.MemeCensorshipService;
import cringe.baza.analysis.MemeDescriptionService;
import cringe.baza.analysis.MemeOcrService;
import cringe.baza.bot.service.TelegramFileService;
import cringe.baza.domain.CensorshipResult;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ai.content.Media;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;

@Service
@RequiredArgsConstructor
public class AiMemeAnalysisService implements MemeCensorshipService, MemeDescriptionService, MemeOcrService {

    private final ChatModel chatModel;
    private final TelegramFileService fileService;

    @Value("classpath:/prompts/meme-analysis-system.txt")
    private Resource systemPromptResource;
    private final Cache<String, MemeAnalysisResult> cache =
            Caffeine.newBuilder()
                    .maximumSize(1000)
                    .expireAfterWrite(java.time.Duration.ofHours(24))
                    .softValues()
                    .build();

    private final BeanOutputConverter<MemeAnalysisResult> converter =
            new BeanOutputConverter<>(MemeAnalysisResult.class);

    public record MemeAnalysisResult(CensorshipResult censorshipResult, String description, String ocrText) {}

    @Override
    public CensorshipResult checkCensorship(String fileId) {
        return getAnalysisResult(fileId).censorshipResult();
    }

    @Override
    public String generateDescription(String fileId) {
        return getAnalysisResult(fileId).description();
    }

    @Override
    public String extractText(String fileId) {
        return getAnalysisResult(fileId).ocrText();
    }

    private MemeAnalysisResult getAnalysisResult(String fileId) {
        try {
            return cache.get(fileId, this::performAnalysis);
        } catch (Exception e) {
            return new MemeAnalysisResult(
                    new CensorshipResult(false, "Ошибка при анализе изображения"),
                    "Описание временно недоступно",
                    "");
        }
    }

    private MemeAnalysisResult performAnalysis(String fileId) {
        try {
            Resource imageResource = getImageResource(fileId, "для анализа");
            String reply = callModelForAnalysis(imageResource);
            return parseReply(reply);
        } catch (Exception e) {
            throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
        }
    }

    private Resource getImageResource(String fileId, String context) throws IOException {
        try (InputStream inputStream = fileService.downloadFile(fileId)) {
            if (inputStream == null) {
                throw new IOException("Не удалось скачать файл из Telegram " + context);
            }
            byte[] imageBytes = inputStream.readAllBytes();
            return new ByteArrayResource(imageBytes);
        }
    }

    private String callModelForAnalysis(Resource imageResource) {
        SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(systemPromptResource);
        Message systemMessage = systemPromptTemplate.createMessage(Map.of("format", converter.getFormat()));

        UserMessage userMessage = UserMessage.builder()
                .text("Analyze this meme.")
                .media(new Media(MimeTypeUtils.IMAGE_JPEG, imageResource))
                .build();

        var response = chatModel.call(new Prompt(List.of(systemMessage, userMessage)));
        return response.getResult().getOutput().getText();
    }

    private MemeAnalysisResult parseReply(String reply) {
        if (reply == null || reply.isBlank()) {
            return new MemeAnalysisResult(new CensorshipResult(false, "ИИ вернул пустой ответ"), "Без описания", "");
        }
        try {
            MemeAnalysisResult parsed = converter.convert(reply.trim());

            CensorshipResult parsedCens = parsed.censorshipResult();
            boolean safe = parsedCens != null && parsedCens.safe();
            String reason = parsedCens != null && parsedCens.reason() != null
                    ? parsedCens.reason().trim()
                    : "";

            if (!safe && reason.isBlank()) {
                reason = "Заблокировано ИИ-цензурой";
            }

            String description =
                    parsed.description() != null ? parsed.description().trim() : "Без описания";
            if (description.isBlank()) {
                description = "Без описания";
            }

            String ocrText = parsed.ocrText() != null ? parsed.ocrText().trim() : "";

            return new MemeAnalysisResult(new CensorshipResult(safe, reason), description, ocrText);
        } catch (Exception e) {
            return new MemeAnalysisResult(
                    new CensorshipResult(false, "Ошибка при анализе изображения"), "Без описания", "");
        }
    }
}
