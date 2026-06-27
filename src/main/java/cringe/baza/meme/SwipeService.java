package cringe.baza.meme;

import cringe.baza.user.UserSessionService;
import cringe.baza.bot.service.TelegramService;

import cringe.baza.bot.model.UserState;
import cringe.baza.domain.MemeModeration;
import cringe.baza.domain.MemeRating;
import cringe.baza.domain.MemeSwipeVote;
import cringe.baza.model.MemeVisibility;
import cringe.baza.model.ModerationStatus;
import cringe.baza.repository.jpa.MemeModerationRepository;
import cringe.baza.repository.jpa.MemeRatingRepository;
import cringe.baza.repository.jpa.MemeSwipeVoteRepository;
import cringe.baza.repository.jpa.TelegramUserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SwipeService {

    private final TelegramService telegramService;
    private final MemeModerationRepository memeModerationRepository;
    private final MemeRatingRepository memeRatingRepository;
    private final MemeSwipeVoteRepository swipeVoteRepository;
    private final TelegramUserRepository telegramUserRepository;
    private final UserSessionService sessionService;

    private final Random random = new Random();

    @Value("${app.battle.default-elo:1000}")
    private int defaultElo;

    public Optional<MemeModeration> getNextMemeForUser(long userId) {
        List<MemeModeration> publicApproved =
                memeModerationRepository.findByStatusAndVisibility(ModerationStatus.APPROVED, MemeVisibility.PUBLIC);
        List<String> votedMemeIds = swipeVoteRepository.findVotedMemeIdsByUserId(userId);

        List<MemeModeration> candidates = publicApproved.stream()
                .filter(m -> m.getOwnerId() == null || !m.getOwnerId().equals(userId))
                .filter(m -> !votedMemeIds.contains(m.getId()))
                .toList();

        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(candidates.get(random.nextInt(candidates.size())));
    }

    @Transactional
    public void registerSwipeVote(long userId, String memeId, String voteType) {
        Optional<MemeModeration> memeOpt = memeModerationRepository.findById(memeId);
        if (memeOpt.isEmpty()) {
            log.error("Meme not found for swipe rating: {}", memeId);
            return;
        }

        if (swipeVoteRepository.existsByMemeIdAndUserId(memeId, userId)) {
            log.info("User {} already voted for meme {}", userId, memeId);
            return;
        }

        MemeSwipeVote swipeVote = new MemeSwipeVote(memeId, userId, voteType);
        swipeVoteRepository.save(swipeVote);
        MemeModeration meme = memeOpt.get();

        MemeRating rating =
                memeRatingRepository.findById(memeId).orElse(new MemeRating(memeId, defaultElo, 0, 0, null));

        int oldElo = rating.getEloRating();
        double expected = 1.0 / (1.0 + Math.pow(10.0, (1000.0 - oldElo) / 400.0));
        double score = "BASE".equalsIgnoreCase(voteType) ? 1.0 : 0.0;

        int kFactor = 16;
        int newElo = (int) Math.round(oldElo + kFactor * (score - expected));

        rating.setEloRating(newElo);
        rating.setLastBattleTime(LocalDateTime.now());

        if (score == 1.0) {
            rating.setWins(rating.getWins() + 1);
            if (meme.getOwnerId() != null) {
                telegramUserRepository.findById(meme.getOwnerId()).ifPresent(owner -> {
                    owner.setPoints((owner.getPoints() != null ? owner.getPoints() : 0) + 1);
                    telegramUserRepository.save(owner);
                    log.info("Awarded +1 point to user {} for meme {}", owner.getId(), memeId);
                });
            }
        } else {
            rating.setLosses(rating.getLosses() + 1);
        }

        memeRatingRepository.save(rating);
        log.info("Meme {} ELO updated: {} -> {}", memeId, oldElo, newElo);
    }

    @Transactional
    public void sendSwipeCard(long chatId, long userId) {
        Optional<MemeModeration> memeOpt = getNextMemeForUser(userId);
        if (memeOpt.isEmpty()) {
            telegramService.sendMessageWithMarkdown(
                    chatId,
                    "🎉 *Вы оценили все доступные мемы!*\nЗагружайте новые мемы или подождите, пока это сделают другие.");
            sessionService.setUserState(chatId, UserState.DEFAULT);
            return;
        }

        MemeModeration meme = memeOpt.get();
        MemeRating rating = memeRatingRepository
                .findById(meme.getId())
                .orElse(new MemeRating(meme.getId(), defaultElo, 0, 0, null));

        String caption = String.format(
                "🔥 *Оценка мема*\n\n" + "%s\n\n" + "📊 Текущий рейтинг: *%d ELO* (В: %d, П: %d)",
                (meme.getDescription() != null ? meme.getDescription() : "Без описания"),
                rating.getEloRating(),
                rating.getWins(),
                rating.getLosses());

        telegramService.sendSwipeCard(chatId, meme.getFileId(), caption, meme.getId());
    }
}
