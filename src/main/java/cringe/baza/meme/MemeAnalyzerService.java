package cringe.baza.meme;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemeAnalyzerService {

    private final ChatModel chatModel;
    private final RetryTemplate retryTemplate;
    private final CircuitBreaker aiCircuitBreaker;

    public record MemeAnalysis(String ocrText, String description, boolean safe, String censorshipReason) {}

    public MemeAnalysis analyze(byte[] imageBytes) {
        log.info("Начало комбинированного анализа мема ИИ ({} байт)", imageBytes.length);
        try {
            Resource imageResource = toResource(imageBytes);
            String reply = retryTemplate.execute(
                    () -> CircuitBreaker.decorateSupplier(aiCircuitBreaker, () -> callModel(imageResource))
                            .get());
            return parseReply(reply);
        } catch (CallNotPermittedException e) {
            log.warn("Circuit breaker открыт, AI-анализ недоступен: {}", e.getMessage());
            throw new AiUnavailableException("ИИ-анализ недоступен: circuit breaker открыт", e);
        } catch (AiUnavailableException e) {
            throw e;
        } catch (Exception e) {
            log.error("Ошибка при анализе мема через ИИ: {}", e.getMessage(), e);
            throw new AiUnavailableException("ИИ-анализ недоступен: " + e.getMessage(), e);
        }
    }

    private static Resource toResource(byte[] imageBytes) {
        return new ByteArrayResource(imageBytes);
    }

    private String callModel(Resource imageResource) {
        UserMessage userMessage = UserMessage.builder()
                .text("You are an AI meme analyzer. Analyze the image and reply STRICTLY in this format:\n"
                        + "TEXT: <exact text visible on the image, or EMPTY if none>\n"
                        + "SAFE: <TRUE or FALSE>\n"
                        + "REASON: <short reason in English if FALSE, otherwise empty>\n"
                        + "DESCRIPTION: <visual description of the image in Russian>\n\n"
                        + "Block criteria for SAFE=FALSE: explicit erotica/nudity (NSFW), violence, "
                        + "severe insults, hate speech, or drug/illegal substance promotion.")
                .media(new Media(MimeTypeUtils.IMAGE_JPEG, imageResource))
                .build();

        log.info("Отправка комбинированного запроса в ИИ...");
        var response = chatModel.call(new Prompt(List.of(userMessage)));
        String reply = response.getResult().getOutput().getText();
        log.info("Ответ ИИ:\n{}", reply);
        return reply;
    }

    private MemeAnalysis parseReply(String reply) {
        if (reply == null || reply.isBlank()) {
            throw new AiUnavailableException("ИИ вернул пустой ответ");
        }

        String ocrText = "";
        boolean safe = false;
        String reason = "";
        String description = "";
        boolean safeLineSeen = false;

        String[] lines = reply.split("\n");
        StringBuilder descBuilder = new StringBuilder();
        boolean buildingDesc = false;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.toUpperCase().startsWith("TEXT:")) {
                ocrText = trimmed.substring(5).trim();
                buildingDesc = false;
            } else if (trimmed.toUpperCase().startsWith("SAFE:")) {
                String val = trimmed.substring(5).trim().toUpperCase();
                if (val.contains("TRUE")) {
                    safe = true;
                } else if (val.contains("FALSE")) {
                    safe = false;
                }
                safeLineSeen = true;
                buildingDesc = false;
            } else if (trimmed.toUpperCase().startsWith("REASON:")) {
                reason = trimmed.substring(7).trim();
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

        if (!safeLineSeen) {
            throw new AiUnavailableException("ИИ вернул ответ без строки цензуры SAFE");
        }

        if ("EMPTY".equalsIgnoreCase(ocrText) || "<EMPTY>".equalsIgnoreCase(ocrText)) {
            ocrText = "";
        }

        if (description.isBlank()) {
            description = "Без описания";
        }

        return new MemeAnalysis(ocrText, description, safe, reason);
    }
}
