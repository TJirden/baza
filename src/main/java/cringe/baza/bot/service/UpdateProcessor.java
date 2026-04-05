package cringe.baza.bot.service;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.PhotoSize;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.GetFile;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.GetFileResponse;
import cringe.baza.bot.command.Command;
import cringe.baza.bot.imaginator.ImageManager;
import cringe.baza.bot.model.UserState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateProcessor {
    private final List<Command> commands;
    private final UserSessionService sessionService;
    private final TelegramBot bot;
    private final ImageManager imageManager;

    public SendMessage processUpdate(Update update) {
        long chatId = update.message().chat().id();
        UserState currentState = sessionService.getUserState(chatId);
        if (currentState == UserState.AWAITING_SAVE_IMAGE) {
            return processImageSave(update);
        }
        return processCommand(update);
    }

    private SendMessage processImageSave(Update update) {
        long chatId = update.message().chat().id();

        if (update.message().photo() == null || update.message().photo().length == 0) {
            return new SendMessage(chatId, "Ошибка: необходимо отправить изображение");
        }

        String description = update.message().caption();
        PhotoSize[] photos = update.message().photo();
        PhotoSize largestPhoto = photos[photos.length - 1];

        try {
            GetFile getFile = new GetFile(largestPhoto.fileId());
            GetFileResponse response = bot.execute(getFile);

            if (!response.isOk()) {
                log.error("Failed to get file: {}", response.description());
                return new SendMessage(chatId, "Ошибка: не удалось загрузить файл");
            }

            String filePath = response.file().filePath();
            String fileUrl = String.format("https://api.telegram.org/file/bot%s/%s", bot.getToken(), filePath);

            BufferedImage image = ImageIO.read(new URL(fileUrl));

            if (image == null) {
                return new SendMessage(chatId, "Ошибка: не удалось прочитать изображение");
            }

            String imageId = imageManager.saveImage(image, description, chatId);

            sessionService.setUserState(chatId, UserState.DEFAULT);

            String responseMessage = description != null && !description.isEmpty()
                    ? String.format("Изображение сохранено. ID: %s\nОписание: %s", imageId, description)
                    : String.format("Изображение сохранено. ID: %s", imageId);

            return new SendMessage(chatId, responseMessage);

        } catch (Exception e) {
            log.error("Error saving image", e);
            return new SendMessage(chatId, "Ошибка: не удалось сохранить изображение");
        }
    }


    private SendMessage processCommand(Update update) {
        long chatId = update.message().chat().id();
        String text = update.message().text();

        for (Command command : commands) {
            if (command.supports(text)) {
                log.info("Обработана команда: command=/{}, chatId={}, text={}", command.command(), chatId, text);
                return command.handle(update);
            }
        }

        log.warn("Получена неизвестная команда: chatId={}, text={}", chatId, text);
        return new SendMessage(chatId, "Неизвестная команда. Используй /help для списка команд.");
    }
}

