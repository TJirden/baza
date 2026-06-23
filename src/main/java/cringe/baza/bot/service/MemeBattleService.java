package cringe.baza.bot.service;

import cringe.baza.domain.MemeBattle;
import cringe.baza.domain.MemeBattleVote;
import cringe.baza.domain.MemeModeration;
import cringe.baza.domain.MemeRating;
import cringe.baza.domain.TelegramUser;
import cringe.baza.model.MemeVisibility;
import cringe.baza.model.ModerationStatus;
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

    private final TelegramService telegramService;
    private final MemeModerationRepository memeModerationRepository;
    private final MemeBattleRepository memeBattleRepository;
    private final MemeBattleVoteRepository memeBattleVoteRepository;
    private final MemeRatingRepository memeRatingRepository;
    private final TelegramUserRepository telegramUserRepository;
    private final VectorStore vectorStore;
    private final MemeDuelLifecycleService memeDuelLifecycleService;

    private final Random random = new Random();

    @Value("${app.battle.duration-minutes:10}")
    private int battleDurationMinutes;

    @Value("${app.battle.default-elo:1000}")
    private int defaultElo;

    @Transactional
    public void startBattle(long chatId) {
        log.info("Starting new meme battle in chat: {}", chatId);
        List<MemeModeration> approvedPublic =
                memeModerationRepository.findByStatusAndVisibility(ModerationStatus.APPROVED, MemeVisibility.PUBLIC);

        if (approvedPublic.size() < 2) {
            telegramService.sendMessage(
                    chatId, "⚠️ Недостаточно публичных мемов для проведения баттла (нужно минимум 2 одобренных мема).");
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
                        && ModerationStatus.APPROVED == opt.get().getStatus()
                        && MemeVisibility.PUBLIC == opt.get().getVisibility()) {
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
            telegramService.sendMessage(chatId, "⚠️ Не удалось подобрать пару для баттла мемов.");
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
        String captionB = "*Вариант Б:*\n" + (memeB.getDescription() != null ? memeB.getDescription() : "");
        String voteText = getVoteCardText(battle, memeA, memeB);

        Integer messageId = telegramService.sendBattleStart(
                chatId, memeA.getFileId(), captionA, memeB.getFileId(), captionB, voteText, battle.getId());

        if (messageId != null) {
            battle.setTelegramMessageId(messageId);
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
            telegramService.editBattleVoteCard(
                    battle.getTelegramChatId(), battle.getTelegramMessageId(), text, battle.getId());
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
            memeDuelLifecycleService.completeDuel(battle, winnerId, bet);
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
                            telegramService.sendMessageWithMarkdown(
                                    user.getId(),
                                    "🎉 *Поздравляем!*\nТвой мем победил в сегодняшнем баттле!\nНачислено *+10 очков* и *+1 победа* в профиль!");
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

            telegramService.editMessageTextWithMarkdown(
                    battle.getTelegramChatId(), battle.getTelegramMessageId(), resultText.toString());
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
}
