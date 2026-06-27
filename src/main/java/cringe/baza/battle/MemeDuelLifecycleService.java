package cringe.baza.battle;

import cringe.baza.bot.service.TelegramService;
import cringe.baza.domain.MemeBattle;
import cringe.baza.domain.MemeModeration;
import cringe.baza.domain.TelegramUser;
import cringe.baza.repository.jpa.MemeBattleRepository;
import cringe.baza.repository.jpa.MemeModerationRepository;
import cringe.baza.repository.jpa.TelegramUserRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemeDuelLifecycleService {

    private final TelegramService telegramService;
    private final MemeModerationRepository memeModerationRepository;
    private final MemeBattleRepository memeBattleRepository;
    private final TelegramUserRepository telegramUserRepository;

    @Value("${app.battle.duration-minutes:10}")
    private int battleDurationMinutes;

    @Transactional
    public void startActiveDuel(MemeBattle battle) {
        Optional<MemeModeration> memeAOpt = memeModerationRepository.findById(battle.getMemeAId());
        Optional<MemeModeration> memeBOpt = memeModerationRepository.findById(battle.getMemeBId());

        if (memeAOpt.isEmpty() || memeBOpt.isEmpty()) {
            log.error("Memes not found for active duel {}", battle.getId());
            battle.setStatus("FAILED");
            memeBattleRepository.save(battle);
            return;
        }

        MemeModeration memeA = memeAOpt.get();
        MemeModeration memeB = memeBOpt.get();

        battle.setStartTime(LocalDateTime.now());
        battle.setEndTime(LocalDateTime.now().plusMinutes(battleDurationMinutes));
        battle.setStatus("ACTIVE");
        memeBattleRepository.save(battle);

        TelegramUser challenger =
                telegramUserRepository.findById(battle.getChallengerId()).orElseThrow();
        TelegramUser opponent =
                telegramUserRepository.findById(battle.getOpponentId()).orElseThrow();

        String chName = challenger.getUsername() != null ? "@" + challenger.getUsername() : challenger.getFirstName();
        String opName = opponent.getUsername() != null ? "@" + opponent.getUsername() : opponent.getFirstName();

        long chatId = battle.getTelegramChatId();

        telegramService.sendMessageWithMarkdown(
                chatId, String.format("*ДУЭЛЬ МЕЖДУ %s И %s НАЧАЛАСЬ!*", chName, opName));

        String captionA = "*ДУЭЛЬ!*\n\n*Мем игрока A (" + chName + "):*\n"
                + (memeA.getDescription() != null ? memeA.getDescription() : "");
        String captionB =
                "*Мем игрока B (" + opName + "):*\n" + (memeB.getDescription() != null ? memeB.getDescription() : "");
        String voteText = getVoteCardText(battle, memeA, memeB);

        Integer messageId = telegramService.sendBattleStart(
                chatId, memeA.getFileId(), captionA, memeB.getFileId(), captionB, voteText, battle.getId());

        if (messageId != null) {
            battle.setTelegramMessageId(messageId);
            memeBattleRepository.save(battle);
        } else {
            log.error("Failed to send voting card for duel battle: {}", battle.getId());
            battle.setStatus("FAILED");
            memeBattleRepository.save(battle);
        }
    }

    private String getVoteCardText(MemeBattle battle, MemeModeration memeA, MemeModeration memeB) {
        int totalVotes = battle.getVotesA() + battle.getVotesB();
        double pctA = totalVotes > 0 ? (battle.getVotesA() * 100.0 / totalVotes) : 0.0;
        double pctB = totalVotes > 0 ? (battle.getVotesB() * 100.0 / totalVotes) : 0.0;

        return String.format(
                "*ГОЛОСОВАНИЕ В БАТТЛЕ!*\nКакой мем базированнее?\n\n" + "Текущие результаты:\n"
                        + "Мем А: *%.1f%%* (%d голосов)\n"
                        + "Мем Б: *%.1f%%* (%d голосов)\n\n"
                        + "Всего голосов: *%d*\n"
                        + "До окончания осталось: *%d минут*",
                pctA,
                battle.getVotesA(),
                pctB,
                battle.getVotesB(),
                totalVotes,
                Math.max(
                        0,
                        Duration.between(LocalDateTime.now(), battle.getEndTime())
                                .toMinutes()));
    }

    @Transactional
    public void cancelPendingDuel(MemeBattle duel, String reason) {
        log.info("Canceling expired duel: {} due to: {}", duel.getId(), reason);
        String oldStatus = duel.getStatus();
        duel.setStatus("EXPIRED");
        memeBattleRepository.save(duel);

        if ("MEME_SELECTION".equals(oldStatus)) {
            TelegramUser challenger =
                    telegramUserRepository.findById(duel.getChallengerId()).orElse(null);
            TelegramUser opponent =
                    telegramUserRepository.findById(duel.getOpponentId()).orElse(null);
            int bet = duel.getBet() != null ? duel.getBet() : 0;
            if (challenger != null) {
                challenger.setPoints(challenger.getPoints() + bet);
                telegramUserRepository.save(challenger);
                try {
                    telegramService.sendMessage(
                            challenger.getId(),
                            "Дуэль отменена (истекло время ожидания выбора мемов). Ставка возвращена.");
                } catch (Exception e) {
                    log.warn("Could not notify challenger user {}: {}", challenger.getId(), e.getMessage());
                }
            }
            if (opponent != null) {
                opponent.setPoints(opponent.getPoints() + bet);
                telegramUserRepository.save(opponent);
                try {
                    telegramService.sendMessage(
                            opponent.getId(),
                            "Дуэль отменена (истекло время ожидания выбора мемов). Ставка возвращена.");
                } catch (Exception e) {
                    log.warn("Could not notify opponent user {}: {}", opponent.getId(), e.getMessage());
                }
            }
        }

        if (duel.getTelegramChatId() != null && duel.getTelegramMessageId() != null) {
            String text = String.format("*Дуэль отменена!*%n_%s_", reason);
            telegramService.editMessageTextWithMarkdown(duel.getTelegramChatId(), duel.getTelegramMessageId(), text);
        }
    }

    @Transactional
    public void completeDuel(MemeBattle battle, String winnerId, int bet) {
        TelegramUser challenger =
                telegramUserRepository.findById(battle.getChallengerId()).orElseThrow();
        TelegramUser opponent =
                telegramUserRepository.findById(battle.getOpponentId()).orElseThrow();

        if (winnerId != null) {
            boolean challengerWon = winnerId.equals(battle.getMemeAId());
            TelegramUser winner = challengerWon ? challenger : opponent;
            TelegramUser loser = challengerWon ? opponent : challenger;

            winner.setPoints(winner.getPoints() + 2 * bet + 10);
            winner.setBattleWins(winner.getBattleWins() + 1);
            telegramUserRepository.save(winner);
            telegramUserRepository.save(loser);

            try {
                telegramService.sendMessageWithMarkdown(
                        winner.getId(),
                        String.format(
                                "*Поздравляем!*\nТвой мем победил в дуэли против %s!\n"
                                        + "Начислено *+%d очков* (ставка *%d* возвращена в двойном размере + *10* бонусных очков)!",
                                loser.getUsername() != null ? "@" + loser.getUsername() : loser.getFirstName(),
                                2 * bet + 10,
                                bet));

                telegramService.sendMessageWithMarkdown(
                        loser.getId(),
                        String.format(
                                "*Дуэль проиграна!*%nТвой мем уступил мему %s.%nСписано *-%d очков* ставки.",
                                winner.getUsername() != null ? "@" + winner.getUsername() : winner.getFirstName(),
                                bet));
            } catch (Exception e) {
                log.warn("Could not notify duelists: {}", e.getMessage());
            }
        } else {
            challenger.setPoints(challenger.getPoints() + bet);
            opponent.setPoints(opponent.getPoints() + bet);
            telegramUserRepository.save(challenger);
            telegramUserRepository.save(opponent);

            try {
                telegramService.sendMessage(challenger.getId(), "Ничья в дуэли! Ставка возвращена на ваш баланс.");
                telegramService.sendMessage(opponent.getId(), "Ничья в дуэли! Ставка возвращена на ваш баланс.");
            } catch (Exception e) {
                log.warn("Could not notify duelists about draw: {}", e.getMessage());
            }
        }
    }
}
