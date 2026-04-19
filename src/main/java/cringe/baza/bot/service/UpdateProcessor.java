package cringe.baza.bot.service;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.PhotoSize;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.BaseRequest;
import com.pengrad.telegrambot.request.GetFile;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.GetFileResponse;
import cringe.baza.bot.command.Command;
import cringe.baza.bot.model.UserState;
import cringe.baza.model.Meme;
import cringe.baza.model.MemeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateProcessor {
    private final List<Command> commands;
    private final UserSessionService sessionService;
    private final TelegramBot bot;
    private final MemeRepository memeRepository;

    public BaseRequest<?,?> processUpdate(Update update) {
        long chatId = update.message().chat().id();
        UserState currentState = sessionService.getUserState(chatId);

        if (currentState == UserState.AWAITING_SAVE_IMAGE) {
            return processImageSave(update);
        }
        return processCommand(update);
    }

    private SendMessage processImageSave(Update update) {
        long chatId = update.message().chat().id();
        log.info("Обработка изображения от чата с id: {}",chatId);

        if (update.message().photo() == null || update.message().photo().length == 0) {
            log.warn("Пользователь {} отправил не изображение в режиме сохранения", chatId);
            return new SendMessage(chatId, "Ошибка: необходимо отправить изображение");
        }

        String description = update.message().caption();
        PhotoSize[] photos = update.message().photo();
        PhotoSize largestPhoto = photos[photos.length - 1];

        try {
            GetFile getFile = new GetFile(largestPhoto.fileId());
            GetFileResponse response = bot.execute(getFile);

            if (!response.isOk()) {
                log.error("Ошибка получения файла из Telegram: {}", response.description());
                return new SendMessage(chatId, "Ошибка: не удалось загрузить файл");
            }

            String filePath = response.file().filePath();
            String fileUrl = String.format("https://api.telegram.org/file/bot%s/%s", bot.getToken(), filePath);

            BufferedImage image = ImageIO.read(new URL(fileUrl));

            if (image == null) {
                log.error("Не удалось прочитать изображение для пользователя {}", chatId);
                return new SendMessage(chatId, "Ошибка: не удалось прочитать изображение");
            }

            String imageId = UUID.randomUUID().toString();
            memeRepository.put(imageId,new Meme(image, description, chatId));

            sessionService.setUserState(chatId, UserState.DEFAULT);

            log.info("Пользователь {} сохранил изображение, ID: {}", chatId, imageId);

            String responseMessage = description != null && !description.isEmpty()
                    ? String.format("Изображение сохранено. ID: %s\nОписание: %s", imageId, description)
                    : String.format("Изображение сохранено. ID: %s", imageId);

            return new SendMessage(chatId, responseMessage);

        } catch (Exception e) {
            log.error("Критическая ошибка при сохранении изображения для пользователя {}: {}", chatId, e.getMessage());
            return new SendMessage(chatId, "Ошибка: не удалось сохранить изображение");
        }
    }

    private BaseRequest<?,?> processCommand(Update update) {
        long chatId = update.message().chat().id();
        String text = update.message().text();

        for (Command command : commands) {
            if (command.supports(text)) {
                return command.handle(update);
            }
        }

        log.warn("Получена неизвестная команда от пользователя {}: {}", chatId, text);
        return new SendMessage(chatId, "Неизвестная команда. Используй /help для списка команд.");
    }
}