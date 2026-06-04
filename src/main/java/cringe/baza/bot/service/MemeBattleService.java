package cringe.baza.bot.service;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.AnswerCallbackQuery;
import com.pengrad.telegrambot.request.BaseRequest;
import com.pengrad.telegrambot.request.EditMessageText;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.request.SendPhoto;
import com.pengrad.telegrambot.response.SendResponse;
import cringe.baza.domain.MemeBattle;
import cringe.baza.domain.MemeBattleVote;
import cringe.baza.domain.MemeModeration;
import cringe.baza.domain.MemeRating;
import cringe.baza.domain.TelegramUser;
import cringe.baza.repository.jpa.MemeBattleRepository;
import cringe.baza.repository.jpa.MemeBattleVoteRepository;
import cringe.baza.repository.jpa.MemeModerationRepository;
import cringe.baza.repository.jpa.MemeRatingRepository;
import cringe.baza.repository.jpa.TelegramUserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemeBattleService {

    private final TelegramBot bot;
    private final MemeModerationRepository memeModerationRepository;
    private final MemeBattleRepository memeBattleRepository;
    private final MemeBattleVoteRepository memeBattleVoteRepository;
    private final MemeRatingRepository memeRatingRepository;
    private final TelegramUserRepository telegramUserRepository;
    private final VectorStore vectorStore;

    private final Random random = new Random();

    @Value("${app.battle.duration-minutes:10}")
    private int battleDurationMinutes;

    @Value("${app.battle.default-elo:1000}")
    private int defaultElo;

    @Transactional
    public void startBattle(long chatId) {
        log.info("Starting new meme battle in chat: {}", chatId);
        List<MemeModeration> approvedPublic = memeModerationRepository.findByStatusAndVisibility("APPROVED", "PUBLIC");

        if (approvedPublic.size() < 2) {
            bot.execute(new SendMessage(
                    chatId,
                    "⚠️ Недостаточно публичных мемов для проведения баттла (нужно минимум 2 одобренных мема)."));
            return;
        }

        MemeModeration memeA = approvedPublic.get(random.nextInt(approvedPublic.size()));
        MemeModeration memeB = null;

        try {
            List<Document> similarDocs = vectorStore.similaritySearch(SearchRequest.builder()
                    .query(memeA.getDescription())
                    .topK(10)
                    .build());

            for (Document doc : similarDocs) {
                String id = doc.getId();
                if (id.equals(memeA.getId())) {
                    continue;
                }
                Optional<MemeModeration> opt = memeModerationRepository.findById(id);
                if (opt.isPresent()
                        && "APPROVED".equals(opt.get().getStatus())
                        && "PUBLIC".equals(opt.get().getVisibility())) {
                    memeB = opt.get();
                    break;
                }
            }
        } catch (Exception e) {
            log.warn("Error finding similar meme via vector search: {}", e.getMessage());
        }

        if (memeB == null) {
            List<MemeModeration> candidates = approvedPublic.stream()
                    .filter(m -> !m.getId().equals(memeA.getId()))
                    .toList();
            if (!candidates.isEmpty()) {
                memeB = candidates.get(random.nextInt(candidates.size()));
            }
        }

        if (memeB == null) {
            bot.execute(new SendMessage(chatId, "⚠️ Не удалось подобрать пару для баттла мемов."));
            return;
        }

        MemeBattle battle = new MemeBattle();
        battle.setMemeAId(memeA.getId());
        battle.setMemeBId(memeB.getId());
        battle.setStartTime(LocalDateTime.now());
        battle.setEndTime(LocalDateTime.now().plusMinutes(battleDurationMinutes));
        battle.setStatus("ACTIVE");
        battle.setTelegramChatId(chatId);
        battle = memeBattleRepository.save(battle);

        String captionA = "⚔️ *БАТТЛ МЕМОВ!* ⚔️\n\n*Вариант А:*\n"
                + (memeA.getDescription() != null ? memeA.getDescription() : "");
        bot.execute(new SendPhoto(chatId, memeA.getFileId()).caption(captionA).parseMode(ParseMode.Markdown));

        String captionB = "*Вариант Б:*\n" + (memeB.getDescription() != null ? memeB.getDescription() : "");
        bot.execute(new SendPhoto(chatId, memeB.getFileId()).caption(captionB).parseMode(ParseMode.Markdown));

        String voteText = getVoteCardText(battle, memeA, memeB);
        InlineKeyboardMarkup keyboard = getVoteKeyboard(battle.getId());

        SendResponse response = bot.execute(
                new SendMessage(chatId, voteText).parseMode(ParseMode.Markdown).replyMarkup(keyboard));

        if (response != null && response.isOk()) {
            battle.setTelegramMessageId(response.message().messageId());
            memeBattleRepository.save(battle);
        } else {
            log.error("Failed to send voting card for battle: {}", battle.getId());
            battle.setStatus("FAILED");
            memeBattleRepository.save(battle);
        }
    }

    @Transactional
    public boolean registerVote(long battleId, long userId, String option) {
        Optional<MemeBattle> battleOpt = memeBattleRepository.findById(battleId);
        if (battleOpt.isEmpty() || !"ACTIVE".equals(battleOpt.get().getStatus())) {
            return false;
        }

        MemeBattle battle = battleOpt.get();

        if (memeBattleVoteRepository.existsByBattleIdAndUserId(battleId, userId)) {
            return false;
        }

        memeBattleVoteRepository.save(new MemeBattleVote(battleId, userId, option));

        if ("A".equalsIgnoreCase(option)) {
            battle.setVotesA(battle.getVotesA() + 1);
        } else {
            battle.setVotesB(battle.getVotesB() + 1);
        }
        memeBattleRepository.save(battle);

        updateVoteCardMessage(battle);

        return true;
    }

    private void updateVoteCardMessage(MemeBattle battle) {
        if (battle.getTelegramChatId() == null || battle.getTelegramMessageId() == null) {
            return;
        }

        Optional<MemeModeration> memeAOpt = memeModerationRepository.findById(battle.getMemeAId());
        Optional<MemeModeration> memeBOpt = memeModerationRepository.findById(battle.getMemeBId());

        if (memeAOpt.isPresent() && memeBOpt.isPresent()) {
            String text = getVoteCardText(battle, memeAOpt.get(), memeBOpt.get());
            InlineKeyboardMarkup keyboard = getVoteKeyboard(battle.getId());

            bot.execute(new EditMessageText(battle.getTelegramChatId(), battle.getTelegramMessageId(), text)
                    .parseMode(ParseMode.Markdown)
                    .replyMarkup(keyboard));
        }
    }

    @Transactional
    public void completeBattle(MemeBattle battle) {
        log.info("Completing meme battle: {}", battle.getId());
        battle.setStatus("COMPLETED");

        Optional<MemeModeration> memeAOpt = memeModerationRepository.findById(battle.getMemeAId());
        Optional<MemeModeration> memeBOpt = memeModerationRepository.findById(battle.getMemeBId());

        if (memeAOpt.isEmpty() || memeBOpt.isEmpty()) {
            log.error("Memes not found for battle completion: {}", battle.getId());
            memeBattleRepository.save(battle);
            return;
        }

        MemeModeration memeA = memeAOpt.get();
        MemeModeration memeB = memeBOpt.get();

        String winnerId = null;
        double scoreA = 0.5;
        double scoreB = 0.5;

        if (battle.getVotesA() > battle.getVotesB()) {
            winnerId = memeA.getId();
            scoreA = 1.0;
            scoreB = 0.0;
        } else if (battle.getVotesB() > battle.getVotesA()) {
            winnerId = memeB.getId();
            scoreA = 0.0;
            scoreB = 1.0;
        }

        battle.setWinnerMemeId(winnerId);
        memeBattleRepository.save(battle);

        MemeRating ratingA = memeRatingRepository
                .findById(memeA.getId())
                .orElse(new MemeRating(memeA.getId(), defaultElo, 0, 0, null));
        MemeRating ratingB = memeRatingRepository
                .findById(memeB.getId())
                .orElse(new MemeRating(memeB.getId(), defaultElo, 0, 0, null));

        int oldEloA = ratingA.getEloRating();
        int oldEloB = ratingB.getEloRating();

        double expectedA = 1.0 / (1.0 + Math.pow(10.0, (oldEloB - oldEloA) / 400.0));
        double expectedB = 1.0 / (1.0 + Math.pow(10.0, (oldEloA - oldEloB) / 400.0));

        int kFactor = 32;
        int newEloA = (int) Math.round(oldEloA + kFactor * (scoreA - expectedA));
        int newEloB = (int) Math.round(oldEloB + kFactor * (scoreB - expectedB));

        if (scoreA == 1.0) {
            ratingA.setWins(ratingA.getWins() + 1);
            ratingB.setLosses(ratingB.getLosses() + 1);
        } else if (scoreB == 1.0) {
            ratingB.setWins(ratingB.getWins() + 1);
            ratingA.setLosses(ratingA.getLosses() + 1);
        }

        ratingA.setEloRating(newEloA);
        ratingA.setLastBattleTime(LocalDateTime.now());
        ratingB.setEloRating(newEloB);
        ratingB.setLastBattleTime(LocalDateTime.now());

        memeRatingRepository.save(ratingA);
        memeRatingRepository.save(ratingB);

        boolean isDuel = "DUEL".equals(battle.getBattleType());
        int bet = battle.getBet() != null ? battle.getBet() : 0;

        if (isDuel) {
            TelegramUser challenger =
                    telegramUserRepository.findById(battle.getChallengerId()).orElseThrow();
            TelegramUser opponent =
                    telegramUserRepository.findById(battle.getOpponentId()).orElseThrow();

            if (winnerId != null) {
                boolean challengerWon = winnerId.equals(memeA.getId());
                TelegramUser winner = challengerWon ? challenger : opponent;
                TelegramUser loser = challengerWon ? opponent : challenger;

                winner.setPoints(winner.getPoints() + 2 * bet + 10);
                winner.setBattleWins(winner.getBattleWins() + 1);
                telegramUserRepository.save(winner);
                telegramUserRepository.save(loser);

                try {
                    bot.execute(new SendMessage(
                                    winner.getId(),
                                    String.format(
                                            "🎉 *Поздравляем!*\nТвой мем победил в дуэли против %s!\n"
                                                    + "Начислено *+%d очков* (ставка *%d* возвращена в двойном размере + *10* бонусных очков)!",
                                            loser.getUsername() != null
                                                    ? "@" + loser.getUsername()
                                                    : loser.getFirstName(),
                                            2 * bet + 10,
                                            bet))
                            .parseMode(ParseMode.Markdown));

                    bot.execute(new SendMessage(
                                    loser.getId(),
                                    String.format(
                                            "😢 *Дуэль проиграна!*\nТвой мем уступил мему %s.\nСписано *-%d очков* ставки.",
                                            winner.getUsername() != null
                                                    ? "@" + winner.getUsername()
                                                    : winner.getFirstName(),
                                            bet))
                            .parseMode(ParseMode.Markdown));
                } catch (Exception e) {
                    log.warn("Could not notify duelists: {}", e.getMessage());
                }
            } else {
                challenger.setPoints(challenger.getPoints() + bet);
                opponent.setPoints(opponent.getPoints() + bet);
                telegramUserRepository.save(challenger);
                telegramUserRepository.save(opponent);

                try {
                    bot.execute(new SendMessage(
                            challenger.getId(), "🤝 *Ничья в дуэли!* Ставка возвращена на ваш баланс."));
                    bot.execute(
                            new SendMessage(opponent.getId(), "🤝 *Ничья в дуэли!* Ставка возвращена на ваш баланс."));
                } catch (Exception e) {
                    log.warn("Could not notify duelists about draw: {}", e.getMessage());
                }
            }
        } else {
            if (winnerId != null) {
                MemeModeration winnerMeme = winnerId.equals(memeA.getId()) ? memeA : memeB;
                if (winnerMeme.getOwnerId() != null) {
                    Optional<TelegramUser> userOpt = telegramUserRepository.findById(winnerMeme.getOwnerId());
                    userOpt.ifPresent(user -> {
                        user.setBattleWins(user.getBattleWins() + 1);
                        user.setPoints(user.getPoints() + 10); // +10 очков за победу
                        telegramUserRepository.save(user);

                        try {
                            bot.execute(new SendMessage(
                                            user.getId(),
                                            "🎉 *Поздравляем!*\nТвой мем победил в сегодняшнем баттле!\nНачислено *+10 очков* и *+1 победа* в профиль!")
                                    .parseMode(ParseMode.Markdown));
                        } catch (Exception e) {
                            log.warn("Could not notify winner user {}: {}", user.getId(), e.getMessage());
                        }
                    });
                }
            }
        }

        if (battle.getTelegramChatId() != null && battle.getTelegramMessageId() != null) {
            StringBuilder resultText = new StringBuilder();
            if (isDuel) {
                resultText.append("🏁 *ДУЭЛЬ ЗАВЕРШЕНА!* 🏁\n\n");
            } else {
                resultText.append("🏁 *БАТТЛ ЗАВЕРШЕН!* 🏁\n\n");
            }

            int totalVotes = battle.getVotesA() + battle.getVotesB();
            double pctA = totalVotes > 0 ? (battle.getVotesA() * 100.0 / totalVotes) : 0.0;
            double pctB = totalVotes > 0 ? (battle.getVotesB() * 100.0 / totalVotes) : 0.0;

            String nameA = "Вариант А";
            String nameB = "Вариант Б";

            if (isDuel) {
                TelegramUser ch = telegramUserRepository
                        .findById(battle.getChallengerId())
                        .orElse(null);
                TelegramUser op =
                        telegramUserRepository.findById(battle.getOpponentId()).orElse(null);
                if (ch != null && op != null) {
                    nameA = ch.getUsername() != null ? "@" + ch.getUsername() : ch.getFirstName();
                    nameB = op.getUsername() != null ? "@" + op.getUsername() : op.getFirstName();
                }
            }

            resultText.append(String.format(
                    "%s: *%.1f%%* (%d голосов) | ELO: %d -> *%d*\n",
                    nameA, pctA, battle.getVotesA(), oldEloA, newEloA));
            resultText.append(String.format(
                    "%s: *%.1f%%* (%d голосов) | ELO: %d -> *%d*\n\n",
                    nameB, pctB, battle.getVotesB(), oldEloB, newEloB));

            if (winnerId != null) {
                String winnerTag = winnerId.equals(memeA.getId()) ? nameA : nameB;
                resultText.append("🏆 Победитель: *").append(winnerTag).append("*!");
                if (isDuel) {
                    resultText.append(String.format("\n💰 Выигрыш: *%d очков*!", bet));
                }
            } else {
                resultText.append("🤝 Ничья!");
            }

            bot.execute(new EditMessageText(
                            battle.getTelegramChatId(), battle.getTelegramMessageId(), resultText.toString())
                    .parseMode(ParseMode.Markdown));
        }
    }

    @Transactional
    public BaseRequest<?, ?> acceptDuel(long battleId, long userId, String callbackQueryId) {
        Optional<MemeBattle> battleOpt = memeBattleRepository.findById(battleId);
        if (battleOpt.isEmpty()) {
            return new AnswerCallbackQuery(callbackQueryId)
                    .text("⚠️ Дуэль не найдена!")
                    .showAlert(true);
        }

        MemeBattle battle = battleOpt.get();
        if (!"PENDING".equals(battle.getStatus())) {
            return new AnswerCallbackQuery(callbackQueryId)
                    .text("⚠️ Этот вызов уже неактивен!")
                    .showAlert(true);
        }

        if (!battle.getOpponentId().equals(userId)) {
            return new AnswerCallbackQuery(callbackQueryId)
                    .text("⚠️ Этот вызов предназначен не для вас!")
                    .showAlert(true);
        }

        TelegramUser challenger =
                telegramUserRepository.findById(battle.getChallengerId()).orElse(null);
        TelegramUser opponent =
                telegramUserRepository.findById(battle.getOpponentId()).orElse(null);

        if (challenger == null || opponent == null) {
            return new AnswerCallbackQuery(callbackQueryId)
                    .text("⚠️ Ошибка: участники дуэли не найдены.")
                    .showAlert(true);
        }

        int bet = battle.getBet();
        if (challenger.getPoints() == null || challenger.getPoints() < bet) {
            battle.setStatus("FAILED");
            memeBattleRepository.save(battle);
            String text = "❌ *Дуэль отменена!* У вызывающего игрока недостаточно очков.";
            bot.execute(new EditMessageText(battle.getTelegramChatId(), battle.getTelegramMessageId(), text)
                    .parseMode(ParseMode.Markdown));
            return new AnswerCallbackQuery(callbackQueryId)
                    .text("⚠️ У вызывающего недостаточно очков!")
                    .showAlert(true);
        }

        if (opponent.getPoints() == null || opponent.getPoints() < bet) {
            return new AnswerCallbackQuery(callbackQueryId)
                    .text("⚠️ У вас недостаточно очков!")
                    .showAlert(true);
        }

        challenger.setPoints(challenger.getPoints() - bet);
        opponent.setPoints(opponent.getPoints() - bet);
        telegramUserRepository.save(challenger);
        telegramUserRepository.save(opponent);

        battle.setStatus("MEME_SELECTION");
        memeBattleRepository.save(battle);

        String opponentName = opponent.getUsername() != null ? "@" + opponent.getUsername() : opponent.getFirstName();
        String text = String.format(
                "⚔️ *Вызов принят игроком %s!*\n\nУчастники выбирают свои мемы в ЛС с ботом...", opponentName);
        bot.execute(new EditMessageText(battle.getTelegramChatId(), battle.getTelegramMessageId(), text)
                .parseMode(ParseMode.Markdown));

        sendMemeSelectionPrivateMessage(challenger.getId(), battle.getId());
        sendMemeSelectionPrivateMessage(opponent.getId(), battle.getId());

        return new AnswerCallbackQuery(callbackQueryId)
                .text("Вы приняли вызов! Перейдите в ЛС с ботом для выбора мема.");
    }

    @Transactional
    public BaseRequest<?, ?> declineDuel(long battleId, long userId, String callbackQueryId) {
        Optional<MemeBattle> battleOpt = memeBattleRepository.findById(battleId);
        if (battleOpt.isEmpty()) {
            return new AnswerCallbackQuery(callbackQueryId)
                    .text("⚠️ Дуэль не найдена!")
                    .showAlert(true);
        }

        MemeBattle battle = battleOpt.get();
        if (!"PENDING".equals(battle.getStatus())) {
            return new AnswerCallbackQuery(callbackQueryId)
                    .text("⚠️ Этот вызов уже неактивен!")
                    .showAlert(true);
        }

        if (!battle.getOpponentId().equals(userId) && !battle.getChallengerId().equals(userId)) {
            return new AnswerCallbackQuery(callbackQueryId)
                    .text("⚠️ Вы не имеете отношения к этой дуэли!")
                    .showAlert(true);
        }

        battle.setStatus("DECLINED");
        memeBattleRepository.save(battle);

        String text;
        if (battle.getOpponentId().equals(userId)) {
            TelegramUser opponent = telegramUserRepository.findById(userId).orElseThrow();
            String opponentName =
                    opponent.getUsername() != null ? "@" + opponent.getUsername() : opponent.getFirstName();
            text = String.format("❌ *Вызов отклонен игроком %s.*", opponentName);
        } else {
            TelegramUser challenger = telegramUserRepository.findById(userId).orElseThrow();
            String challengerName =
                    challenger.getUsername() != null ? "@" + challenger.getUsername() : challenger.getFirstName();
            text = String.format("❌ *Вызов отменен игроком %s.*", challengerName);
        }

        bot.execute(new EditMessageText(battle.getTelegramChatId(), battle.getTelegramMessageId(), text)
                .parseMode(ParseMode.Markdown));

        return new AnswerCallbackQuery(callbackQueryId).text("Вызов отменен.");
    }

    @Transactional
    public BaseRequest<?, ?> selectDuelMeme(long battleId, long userId, String memeId, String callbackQueryId) {
        Optional<MemeBattle> battleOpt = memeBattleRepository.findById(battleId);
        if (battleOpt.isEmpty()) {
            return new AnswerCallbackQuery(callbackQueryId)
                    .text("⚠️ Дуэль не найдена!")
                    .showAlert(true);
        }

        MemeBattle battle = battleOpt.get();
        if (!"MEME_SELECTION".equals(battle.getStatus())) {
            return new AnswerCallbackQuery(callbackQueryId)
                    .text("⚠️ Выбор мемов уже завершен!")
                    .showAlert(true);
        }

        boolean isChallenger = battle.getChallengerId().equals(userId);
        boolean isOpponent = battle.getOpponentId().equals(userId);

        if (!isChallenger && !isOpponent) {
            return new AnswerCallbackQuery(callbackQueryId)
                    .text("⚠️ Вы не участвуете в этой дуэли!")
                    .showAlert(true);
        }

        Optional<MemeModeration> memeOpt = memeModerationRepository.findById(memeId);
        if (memeOpt.isEmpty()) {
            return new AnswerCallbackQuery(callbackQueryId)
                    .text("⚠️ Выбранный мем не найден!")
                    .showAlert(true);
        }

        if (isChallenger) {
            if (Boolean.TRUE.equals(battle.getChallengerMemeSelected())) {
                return new AnswerCallbackQuery(callbackQueryId)
                        .text("⚠️ Вы уже выбрали мем!")
                        .showAlert(true);
            }
            battle.setMemeAId(memeId);
            battle.setChallengerMemeSelected(true);
        } else {
            if (Boolean.TRUE.equals(battle.getOpponentMemeSelected())) {
                return new AnswerCallbackQuery(callbackQueryId)
                        .text("⚠️ Вы уже выбрали мем!")
                        .showAlert(true);
            }
            battle.setMemeBId(memeId);
            battle.setOpponentMemeSelected(true);
        }

        memeBattleRepository.save(battle);

        bot.execute(new SendMessage(userId, "✅ Мем успешно выбран! Ожидаем готовности второго игрока..."));

        if (Boolean.TRUE.equals(battle.getChallengerMemeSelected())
                && Boolean.TRUE.equals(battle.getOpponentMemeSelected())) {
            startActiveDuel(battle);
        }

        return new AnswerCallbackQuery(callbackQueryId).text("Мем выбран!");
    }

    private void sendMemeSelectionPrivateMessage(long userId, long battleId) {
        List<MemeModeration> userMemes =
                memeModerationRepository.findByOwnerIdAndStatusAndVisibility(userId, "APPROVED", "PUBLIC");

        if (userMemes.isEmpty()) {
            bot.execute(new SendMessage(
                    userId, "⚠️ У вас нет одобренных публичных мемов. Дуэль не может быть продолжена."));
            return;
        }

        StringBuilder sb = new StringBuilder(
                "⚔️ *ВЫБОР МЕМА ДЛЯ ДУЭЛИ* ⚔️\n\nВыберите мем, который будет представлять вас в дуэли:\n");
        InlineKeyboardButton[][] buttons = new InlineKeyboardButton[userMemes.size()][1];

        for (int i = 0; i < userMemes.size(); i++) {
            MemeModeration meme = userMemes.get(i);
            String desc = meme.getDescription();
            if (desc == null || desc.isBlank()) {
                desc = "Без описания (ID: " + meme.getId().substring(0, 8) + ")";
            } else if (desc.length() > 30) {
                desc = desc.substring(0, 27) + "...";
            }
            sb.append(String.format("%d. %s\n", i + 1, desc));
            buttons[i][0] = new InlineKeyboardButton(String.format("Мем %d", i + 1))
                    .callbackData(String.format("duel_select:%d:%s", battleId, meme.getId()));
        }

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup(buttons);
        try {
            bot.execute(new SendMessage(userId, sb.toString())
                    .parseMode(ParseMode.Markdown)
                    .replyMarkup(keyboard));
        } catch (Exception e) {
            log.error("Failed to send private message to user {}: {}", userId, e.getMessage());
            Optional<MemeBattle> battleOpt = memeBattleRepository.findById(battleId);
            if (battleOpt.isPresent()) {
                MemeBattle battle = battleOpt.get();
                Optional<TelegramUser> userOpt = telegramUserRepository.findById(userId);
                String name = userOpt.map(u -> u.getUsername() != null ? "@" + u.getUsername() : u.getFirstName())
                        .orElse("Игрок");
                bot.execute(new SendMessage(
                        battle.getTelegramChatId(),
                        String.format("⚠️ %s, напишите боту в ЛС (нажмите /start) для выбора мема!", name)));
            }
        }
    }

    private void startActiveDuel(MemeBattle battle) {
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

        bot.execute(new SendMessage(chatId, String.format("⚔️ *ДУЭЛЬ МЕЖДУ %s И %s НАЧАЛАСЬ!*", chName, opName))
                .parseMode(ParseMode.Markdown));

        String captionA = "⚔️ *ДУЭЛЬ!* ⚔️\n\n*Мем игрока A (" + chName + "):*\n"
                + (memeA.getDescription() != null ? memeA.getDescription() : "");
        bot.execute(new SendPhoto(chatId, memeA.getFileId()).caption(captionA).parseMode(ParseMode.Markdown));

        String captionB =
                "*Мем игрока B (" + opName + "):*\n" + (memeB.getDescription() != null ? memeB.getDescription() : "");
        bot.execute(new SendPhoto(chatId, memeB.getFileId()).caption(captionB).parseMode(ParseMode.Markdown));

        String voteText = getVoteCardText(battle, memeA, memeB);
        InlineKeyboardMarkup keyboard = getVoteKeyboard(battle.getId());

        SendResponse response = bot.execute(
                new SendMessage(chatId, voteText).parseMode(ParseMode.Markdown).replyMarkup(keyboard));

        if (response != null && response.isOk()) {
            battle.setTelegramMessageId(response.message().messageId());
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
                "⚔️ *ГОЛОСОВАНИЕ В БАТТЛЕ!* ⚔️\nКакой мем базированнее?\n\n" + "📊 *Текущие результаты:*\n"
                        + "Мем А: *%.1f%%* (%d голосов)\n"
                        + "Мем Б: *%.1f%%* (%d голосов)\n\n"
                        + "Всего голосов: *%d*\n"
                        + "🕒 До окончания осталось: *%d минут*",
                pctA,
                battle.getVotesA(),
                pctB,
                battle.getVotesB(),
                totalVotes,
                Math.max(
                        0,
                        java.time.Duration.between(LocalDateTime.now(), battle.getEndTime())
                                .toMinutes()));
    }

    private InlineKeyboardMarkup getVoteKeyboard(long battleId) {
        return new InlineKeyboardMarkup(
                new InlineKeyboardButton("👍 Вариант А").callbackData("vote:" + battleId + ":A"),
                new InlineKeyboardButton("👍 Вариант Б").callbackData("vote:" + battleId + ":B"));
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
                    bot.execute(new SendMessage(
                            challenger.getId(),
                            "❌ Дуэль отменена (истекло время ожидания выбора мемов). Ставка возвращена."));
                } catch (Exception e) {
                    log.warn("Could not notify challenger user {}: {}", challenger.getId(), e.getMessage());
                }
            }
            if (opponent != null) {
                opponent.setPoints(opponent.getPoints() + bet);
                telegramUserRepository.save(opponent);
                try {
                    bot.execute(new SendMessage(
                            opponent.getId(),
                            "❌ Дуэль отменена (истекло время ожидания выбора мемов). Ставка возвращена."));
                } catch (Exception e) {
                    log.warn("Could not notify opponent user {}: {}", opponent.getId(), e.getMessage());
                }
            }
        }

        if (duel.getTelegramChatId() != null && duel.getTelegramMessageId() != null) {
            String text = String.format("❌ *Дуэль отменена!*\n_%s_", reason);
            bot.execute(new EditMessageText(duel.getTelegramChatId(), duel.getTelegramMessageId(), text)
                    .parseMode(ParseMode.Markdown));
        }
    }
}
