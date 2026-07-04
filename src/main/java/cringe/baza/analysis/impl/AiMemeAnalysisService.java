package cringe.baza.analysis.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import cringe.baza.analysis.MemeCensorshipService;
import cringe.baza.analysis.MemeDescriptionService;
import cringe.baza.analysis.MemeOcrService;
import cringe.baza.bot.service.TelegramFileService;
import cringe.baza.domain.CensorshipResult;
import cringe.baza.exception.CensorshipUnavailableException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;

@Service
@RequiredArgsConstructor
public class AiMemeAnalysisService implements MemeCensorshipService, MemeDescriptionService, MemeOcrService {

    private final ChatModel chatModel;
    private final TelegramFileService fileService;
    private final Cache<String, MemeAnalysisResult> cache =
            Caffeine.newBuilder().softValues().build();

    private record MemeAnalysisResult(CensorshipResult censorshipResult, String description, String ocrText) {}

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
        return cache.get(fileId, this::performAnalysis);
    }

    private MemeAnalysisResult performAnalysis(String fileId) {
        try {
            Resource imageResource = getImageResource(fileId, "для анализа");
            String reply = callModelForAnalysis(imageResource);
            return parseReply(reply);
        } catch (Exception e) {
            throw new CensorshipUnavailableException("Ошибка при анализе мема через ИИ: " + e.getMessage(), e);
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
        UserMessage userMessage = UserMessage.builder()
                .text(
                        "You are an AI meme analyzer. Perform censorship check, OCR text extraction, and visual description.\n"
                                + "Block criteria for censorship: explicit erotica/nudity (NSFW), violence, severe insults, hate speech, or drug/illegal substance promotion.\n"
                                + "For OCR: Extract any visible text exactly.\n"
                                + "For description: Describe the visual context in Russian.\n"
                                + "Respond STRICTLY in this format:\n"
                                + "SAFE: <TRUE or FALSE>\n"
                                + "REASON: <short reason for block in Russian if FALSE, otherwise empty>\n"
                                + "TEXT: <exact text on image or EMPTY if none>\n"
                                + "DESCRIPTION: <visual description of the image in Russian>")
                .media(new Media(MimeTypeUtils.IMAGE_JPEG, imageResource))
                .build();

        var response = chatModel.call(new Prompt(List.of(userMessage)));
        return response.getResult().getOutput().getText();
    }

    private MemeAnalysisResult parseReply(String reply) {
        boolean safe = true;
        String reason = "";
        String ocrText = "";
        String description = "";

        if (reply != null) {
            String[] lines = reply.split("\n");
            StringBuilder descBuilder = new StringBuilder();
            boolean buildingDesc = false;

            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.toUpperCase().startsWith("SAFE:")) {
                    String val = trimmed.substring(5).trim().toUpperCase();
                    if (val.contains("FALSE")) {
                        safe = false;
                    }
                    buildingDesc = false;
                } else if (trimmed.toUpperCase().startsWith("REASON:")) {
                    reason = trimmed.substring(7).trim();
                    buildingDesc = false;
                } else if (trimmed.toUpperCase().startsWith("TEXT:")) {
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

        if ("EMPTY".equalsIgnoreCase(ocrText) || "<EMPTY>".equalsIgnoreCase(ocrText)) {
            ocrText = "";
        }

        if (description.isBlank()) {
            description = "Без описания";
        }

        return new MemeAnalysisResult(new CensorshipResult(safe, reason), description, ocrText);
    }
}
