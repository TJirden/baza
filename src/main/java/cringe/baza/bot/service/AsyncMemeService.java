package cringe.baza.bot.service;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.PhotoSize;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.EditMessageText;
import cringe.baza.model.Meme;
import cringe.baza.processor.MemeProcessor;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncMemeService {

  private final TelegramBot bot;
  private final MemeProcessor memeProcessor;
  private final TelegramFileService fileService;

  @Async("memeAsyncExecutor")
  public void processAndSaveMemeAsync(
      long chatId,
      long userId,
      PhotoSize[] photo,
      String description,
      String visibilityContext,
      int messageIdToEdit) {
    try {
      log.info("Начало асинхронного сохранения мема для chatId={}, userId={}", chatId, userId);

      String fileId = fileService.getImageFileId(photo);
      if (fileId == null) {
        throw new IllegalArgumentException("Не удалось получить fileId для изображения");
      }

      String visibility = "PUBLIC";
      List<Long> groupIds = new ArrayList<>();

      if (visibilityContext != null) {
        if (visibilityContext.startsWith("GROUP:")) {
          visibility = "GROUP";
          String[] parts = visibilityContext.substring(6).split(",");
          for (String part : parts) {
            try {
              groupIds.add(Long.parseLong(part));
            } catch (NumberFormatException ignored) {
            }
          }
        } else {
          visibility = visibilityContext;
        }
      }

      String imageId =
          memeProcessor.save(new Meme(null, description, fileId, userId, visibility, groupIds));
      log.info("Мем успешно сохранен в фоне. ID: {}", imageId);

      String text =
          "*Мем успешно сохранен!*\n\n"
              + "*ID*: `"
              + imageId
              + "`\n"
              + "*Описание*: "
              + description
              + "\n"
              + "*Доступ*: "
              + visibility;

      bot.execute(new EditMessageText(chatId, messageIdToEdit, text).parseMode(ParseMode.Markdown));

    } catch (Exception e) {
      log.error("Критическая ошибка при асинхронном сохранении изображения: {}", e.getMessage(), e);
      try {
        bot.execute(
            new EditMessageText(
                    chatId, messageIdToEdit, "*Ошибка сохранения мема:* " + e.getMessage())
                .parseMode(ParseMode.Markdown));
      } catch (Exception ex) {
        log.error("Не удалось отправить сообщение об ошибке пользователю: {}", ex.getMessage());
      }
    }
  }
}
