package cringe.baza.bot.command;

import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.BaseRequest;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.request.SendPhoto;
import cringe.baza.model.Meme;
import cringe.baza.model.MemeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class GetMemeCommand implements Command {

    private final MemeRepository memeRepository;

    @Override
    public String command() {
        return "getmeme";
    }

    @Override
    public String description() {
        return "Получить мем по ID";
    }

    @Override
    public BaseRequest<?,?> handle(Update update) {
        long chatId = update.message().chat().id();
        String messageText = update.message().text();

        String memeId = extractMemeId(messageText);

        if (memeId == null || memeId.isEmpty()) {
            return new SendMessage(chatId, "Нужно указать ID мема. Пример: /getmeme 123");
        }

        Optional<Meme> memeOptional = memeRepository.get(memeId);

        if (memeOptional.isEmpty()) {
            return new SendMessage(chatId, "Мем с ID " + memeId + " не найден");
        }

        Meme meme = memeOptional.get();

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(meme.image(), "png", baos);
            byte[] imageBytes = baos.toByteArray();

            SendPhoto sendPhoto = new SendPhoto(chatId, imageBytes);
            sendPhoto.caption(meme.description());

            return sendPhoto;

        } catch (IOException e) {
            return new SendMessage(chatId, "Ошибка при загрузке изображения");
        }
    }

    private String extractMemeId(String text) {
        String withoutCommand = text.replaceFirst("(?i)/" + command() + "(?:@\\S+)?", "").trim();

        if (withoutCommand.isEmpty()) {
            return null;
        }

        String[] parts = withoutCommand.split("\\s+");
        return parts[0].trim();
    }
}