package cringe.baza.bot.service;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.PhotoSize;
import com.pengrad.telegrambot.request.GetFile;
import com.pengrad.telegrambot.response.GetFileResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.net.URI;

@Service
@RequiredArgsConstructor
public class TelegramFileService {
    private final TelegramBot bot;

    public BufferedImage downloadImage(PhotoSize[] photos) {
        if (photos == null || photos.length == 0) return null;

        PhotoSize largestPhoto = photos[photos.length - 1];
        try {
            GetFileResponse response = bot.execute(new GetFile(largestPhoto.fileId()));
            if (!response.isOk()) throw new RuntimeException("Telegram API error: " + response.description());

            String fileUrl = bot.getFullFilePath(response.file());
            return ImageIO.read(URI.create(fileUrl).toURL());
        } catch (Exception e) {
            throw new RuntimeException("Failed to download image", e);
        }
    }
}