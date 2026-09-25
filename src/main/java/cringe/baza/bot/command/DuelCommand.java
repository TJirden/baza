package cringe.baza.bot.command;

import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.BaseRequest;
import com.pengrad.telegrambot.request.SendMessage;
import cringe.baza.battle.MemeDuelService;
import cringe.baza.bot.model.DuelCreateResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class DuelCommand implements Command {

    private final MemeDuelService memeDuelService;

    @Override
    public String command() {
        return "duel";
    }

    @Override
    public String description() {
        return "Вызвать пользователя на дуэль мемов. Пример: /duel @username 50";
    }

    @Override
    public BaseRequest<?, ?> handle(Update update) {
        long chatId = update.message().chat().id();
        long userId = update.message().from().id();
        String text = update.message().text();

        if (text == null) {
            return null;
        }

        String[] parts = text.trim().split("\\s+");
        if (parts.length < 3) {
            return new SendMessage(chatId, "⚠️ Неверный формат команды. Используйте: /duel @username <ставка>");
        }

        String targetUsername = parts[1];
        if (targetUsername.startsWith("@")) {
            targetUsername = targetUsername.substring(1);
        }

        int bet;
        try {
            bet = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            return new SendMessage(chatId, "⚠️ Ставка должна быть целым числом. Пример: /duel @username 50");
        }

        if (bet <= 0) {
            return new SendMessage(chatId, "⚠️ Ставка должна быть больше 0 очков.");
        }

        DuelCreateResult result = memeDuelService.createDuel(userId, targetUsername, bet, chatId);

        return switch (result) {
            case SUCCESS -> null;
            case OPPONENT_NOT_FOUND ->
                new SendMessage(
                        chatId,
                        "⚠️ Пользователь @" + targetUsername
                                + " не найден в базе бота. Он должен сначала пообщаться с ботом.");
            case SELF_DUEL -> new SendMessage(chatId, "⚠️ Вы не можете вызвать на дуэль самого себя!");
            case CHALLENGER_INSUFFICIENT_POINTS ->
                new SendMessage(chatId, "⚠️ У вас недостаточно очков для этой ставки!");
            case OPPONENT_INSUFFICIENT_POINTS ->
                new SendMessage(chatId, "⚠️ У оппонента @" + targetUsername + " недостаточно очков!");
            case CHALLENGER_NO_MEMES ->
                new SendMessage(chatId, "⚠️ У вас нет одобренных публичных мемов для участия в дуэли!");
            case OPPONENT_NO_MEMES ->
                new SendMessage(
                        chatId,
                        "⚠️ У оппонента @" + targetUsername + " нет одобренных публичных мемов для участия в дуэли!");
            case ERROR -> new SendMessage(chatId, "⚠️ Произошла ошибка при создании дуэли. Попробуйте позже.");
        };
    }
}
