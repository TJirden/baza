package cringe.baza.bot.service;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.PhotoSize;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TelegramFileService {
    private final TelegramBot bot;

    public String getImageFileId(PhotoSize[] photos) {
        if (photos == null || photos.length == 0) return null;
        return photos[photos.length - 1].fileId();
    }
}