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
import cringe.baza.processor.MemeProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateProcessor {
    private final List<Command> commands;
    private final UserSessionService sessionService;
    private final TelegramFileService fileService;
    private final MemeProcessor memeProcessor;

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
        String description = update.message().caption();

        if (description == null || description.isBlank()) {
            return new SendMessage(chatId, "Пожалуйста, добавь описание к фото (в подписи), чтобы я мог его найти!");
        }

        try {
            BufferedImage image = fileService.downloadImage(update.message().photo());
            if (image == null) {
                return new SendMessage(chatId, "Ошибка: отправьте фото");
            }

            String imageId = memeProcessor.save(new Meme(image, description, chatId));

            sessionService.setUserState(chatId, UserState.DEFAULT);

            String text = "Мем сохранен! ID: " + imageId + "\nОписание: " + description;
            return new SendMessage(chatId, text);

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