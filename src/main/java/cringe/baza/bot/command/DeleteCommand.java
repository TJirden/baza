package cringe.baza.bot.command;

import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.BaseRequest;
import com.pengrad.telegrambot.request.SendMessage;
import cringe.baza.processor.MemeProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class DeleteCommand implements Command {

  private final MemeProcessor memeProcessor;

  @Override
  public String command() {
    return "delete";
  }

  @Override
  public String description() {
    return "Удалить мем по ID. Пример: /delete 12345";
  }

  @Override
  public BaseRequest<?, ?> handle(Update update) {
    long chatId = update.message().chat().id();
    String memeId = extractText(update.message().text());

    if (memeId == null || memeId.isEmpty()) {
      return new SendMessage(chatId, "Нужно указать ID мема. Пример: /delete 12345");
    }

    boolean deleted = memeProcessor.delete(memeId);

    if (deleted) {
      return new SendMessage(chatId, "Мем успешно удален.");
    } else {
      return new SendMessage(chatId, "Мем с ID " + memeId + " не найден.");
    }
  }
}
