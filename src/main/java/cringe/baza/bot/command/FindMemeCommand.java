package cringe.baza.bot.command;

import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.InputMediaPhoto;
import com.pengrad.telegrambot.request.BaseRequest;
import com.pengrad.telegrambot.request.SendMediaGroup;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.request.SendPhoto;
import cringe.baza.model.Meme;
import cringe.baza.processor.MemeProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
@Component
public class FindMemeCommand implements Command {

    private final MemeProcessor memeProcessor;

    @Override
    public String command() {
        return "find";
    }

    @Override
    public String description() {
        return "Найти мемы по описанию. Пример: /find грустный кот";
    }

    @Override
    public BaseRequest<?, ?> handle(Update update) {
        long chatId = update.message().chat().id();
        String query = extractText(update.message().text());

        if (query == null || query.isBlank()) {
            return new SendMessage(chatId, "Введите поисковый запрос. Пример: /find пёс");
        }

        Optional<Meme> meme = memeProcessor.getSingleMemeByDescription(query);

        if (meme.isEmpty()) {
            return new SendMessage(chatId, "Ничего не нашлось по запросу: " + query);
        }


        try {
            byte[] imageBytes = toByteArray(meme.get().image());

            SendPhoto sendPhoto = new SendPhoto(chatId, imageBytes);

            if (meme.get().description() != null) {
                sendPhoto.caption(meme.get().description());
            }

            return sendPhoto;

        } catch (IOException e) {
            log.error("Ошибка при чтении изображения мема: {}", meme.get().description(), e);
            return new SendMessage(chatId, "Произошла ошибка при загрузке изображения.");
        }
    }

    private byte[] toByteArray(java.awt.image.BufferedImage image) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        return baos.toByteArray();
    }
}