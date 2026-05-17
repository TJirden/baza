package cringe.baza.bot.service;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.PhotoSize;
import com.pengrad.telegrambot.request.GetFile;
import com.pengrad.telegrambot.response.GetFileResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

@Service
@RequiredArgsConstructor
public class TelegramFileService {
    private final TelegramBot bot;

    public String getImageFileId(PhotoSize[] photos) {
        if (photos == null || photos.length == 0) return null;
        return photos[photos.length - 1].fileId();
    }

    public String getFullFilePath(String fileId) {
        GetFileResponse response = bot.execute(new GetFile(fileId));
        if (response.isOk()) {
            return bot.getFullFilePath(response.file());
        }
        return null;
    }

    public InputStream downloadFile(String fileId) throws IOException {
        String path = getFullFilePath(fileId);
        if (path == null) return null;
        return new URL(path).openStream();
    }
}