package cringe.baza.bot.command;

import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.request.BaseRequest;
import com.pengrad.telegrambot.request.SendMessage;
import cringe.baza.domain.MemeBattle;
import cringe.baza.domain.MemeModeration;
import cringe.baza.domain.TelegramUser;
import cringe.baza.model.MemeVisibility;
import cringe.baza.model.ModerationStatus;
import cringe.baza.repository.jpa.MemeBattleRepository;
import cringe.baza.repository.jpa.MemeModerationRepository;
import cringe.baza.repository.jpa.TelegramUserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class DuelCommand implements Command {

    private final TelegramUserRepository telegramUserRepository;
    private final MemeModerationRepository memeModerationRepository;
    private final MemeBattleRepository memeBattleRepository;
    private final com.pengrad.telegrambot.TelegramBot bot;

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

        Optional<TelegramUser> opponentOpt = telegramUserRepository.findByUsernameIgnoreCase(targetUsername);
        if (opponentOpt.isEmpty()) {
            return new SendMessage(
                    chatId,
                    "⚠️ Пользователь @" + targetUsername
                            + " не найден в базе бота. Он должен сначала пообщаться с ботом.");
        }

        TelegramUser opponent = opponentOpt.get();
        if (opponent.getId().equals(userId)) {
            return new SendMessage(chatId, "⚠️ Вы не можете вызвать на дуэль самого себя!");
        }

        Optional<TelegramUser> challengerOpt = telegramUserRepository.findById(userId);
        if (challengerOpt.isEmpty()) {
            return new SendMessage(chatId, "⚠️ Произошла ошибка. Бот вас не распознал.");
        }

        TelegramUser challenger = challengerOpt.get();

        if (challenger.getPoints() == null || challenger.getPoints() < bet) {
            return new SendMessage(
                    chatId,
                    "⚠️ У вас недостаточно очков для этой ставки! Ваш баланс: "
                            + (challenger.getPoints() != null ? challenger.getPoints() : 0) + " очков.");
        }

        if (opponent.getPoints() == null || opponent.getPoints() < bet) {
            return new SendMessage(
                    chatId,
                    "⚠️ У оппонента @" + targetUsername + " недостаточно очков! Его баланс: "
                            + (opponent.getPoints() != null ? opponent.getPoints() : 0) + " очков.");
        }

        List<MemeModeration> challengerMemes = memeModerationRepository.findByOwnerIdAndStatusAndVisibility(
                challenger.getId(), ModerationStatus.APPROVED, MemeVisibility.PUBLIC);
        List<MemeModeration> opponentMemes = memeModerationRepository.findByOwnerIdAndStatusAndVisibility(
                opponent.getId(), ModerationStatus.APPROVED, MemeVisibility.PUBLIC);

        if (challengerMemes.isEmpty()) {
            return new SendMessage(chatId, "⚠️ У вас нет одобренных публичных мемов для участия в дуэли!");
        }

        if (opponentMemes.isEmpty()) {
            return new SendMessage(
                    chatId,
                    "⚠️ У оппонента @" + targetUsername + " нет одобренных публичных мемов для участия в дуэли!");
        }

        MemeBattle battle = new MemeBattle();
        battle.setBattleType("DUEL");
        battle.setChallengerId(challenger.getId());
        battle.setOpponentId(opponent.getId());
        battle.setBet(bet);
        battle.setStatus("PENDING");
        battle.setTelegramChatId(chatId);
        battle.setStartTime(LocalDateTime.now());
        battle = memeBattleRepository.save(battle);

        String challengerName =
                challenger.getUsername() != null ? "@" + challenger.getUsername() : challenger.getFirstName();
        String opponentName = "@" + opponent.getUsername();

        String msgText = String.format(
                "⚔️ *ВЫЗОВ НА ДУЭЛЬ!* ⚔️\n\n%s вызывает %s на дуэль мемов!\n"
                        + "💰 Ставка: *%d очков*\n\n"
                        + "%s, принимаешь ли ты вызов?\n"
                        + "_(Для выбора мемов перейдите в диалог с ботом: @cringe_baza_bot)_",
                challengerName, opponentName, bet, opponentName);

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup(
                new InlineKeyboardButton("Принять ⚔️").callbackData("duel_accept:" + battle.getId()),
                new InlineKeyboardButton("Отклонить 👎").callbackData("duel_decline:" + battle.getId()));

        com.pengrad.telegrambot.request.SendMessage sendMessage = new com.pengrad.telegrambot.request.SendMessage(
                        chatId, msgText)
                .parseMode(com.pengrad.telegrambot.model.request.ParseMode.Markdown)
                .replyMarkup(keyboard);

        com.pengrad.telegrambot.response.SendResponse response = bot.execute(sendMessage);
        if (response != null && response.isOk()) {
            battle.setTelegramMessageId(response.message().messageId());
            memeBattleRepository.save(battle);
        } else {
            log.error("Failed to send duel challenge message to chat {}", chatId);
            battle.setStatus("FAILED");
            memeBattleRepository.save(battle);
        }

        return null;
    }
}
