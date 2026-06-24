package cringe.baza.bot.service;

import cringe.baza.bot.model.DuelActionResult;
import cringe.baza.domain.MemeBattle;
import cringe.baza.domain.MemeModeration;
import cringe.baza.domain.TelegramUser;
import cringe.baza.model.MemeVisibility;
import cringe.baza.model.ModerationStatus;
import cringe.baza.repository.jpa.MemeBattleRepository;
import cringe.baza.repository.jpa.MemeModerationRepository;
import cringe.baza.repository.jpa.TelegramUserRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemeDuelService {

    private final TelegramService telegramService;
    private final MemeModerationRepository memeModerationRepository;
    private final MemeBattleRepository memeBattleRepository;
    private final TelegramUserRepository telegramUserRepository;
    private final MemeDuelLifecycleService memeDuelLifecycleService;

    @Transactional
    public DuelActionResult acceptDuel(long battleId, long userId) {
        Optional<MemeBattle> battleOpt = memeBattleRepository.findById(battleId);
        if (battleOpt.isEmpty()) {
            return DuelActionResult.NOT_FOUND;
        }

        MemeBattle battle = battleOpt.get();
        if (!"PENDING".equals(battle.getStatus())) {
            return DuelActionResult.INACTIVE;
        }

        if (!battle.getOpponentId().equals(userId)) {
            return DuelActionResult.UNAUTHORIZED;
        }

        TelegramUser challenger =
                telegramUserRepository.findById(battle.getChallengerId()).orElse(null);
        TelegramUser opponent =
                telegramUserRepository.findById(battle.getOpponentId()).orElse(null);

        if (challenger == null || opponent == null) {
            return DuelActionResult.ERROR;
        }

        int bet = battle.getBet();
        if (challenger.getPoints() == null || challenger.getPoints() < bet) {
            battle.setStatus("FAILED");
            memeBattleRepository.save(battle);
            String text = "*Дуэль отменена!* У вызывающего игрока недостаточно очков.";
            telegramService.editMessageTextWithMarkdown(
                    battle.getTelegramChatId(), battle.getTelegramMessageId(), text);
            return DuelActionResult.CHALLENGER_INSUFFICIENT_POINTS;
        }

        if (opponent.getPoints() == null || opponent.getPoints() < bet) {
            return DuelActionResult.OPPONENT_INSUFFICIENT_POINTS;
        }

        challenger.setPoints(challenger.getPoints() - bet);
        opponent.setPoints(opponent.getPoints() - bet);
        telegramUserRepository.save(challenger);
        telegramUserRepository.save(opponent);

        battle.setStatus("MEME_SELECTION");
        memeBattleRepository.save(battle);

        String opponentName = opponent.getUsername() != null ? "@" + opponent.getUsername() : opponent.getFirstName();
        String text = String.format(
                "*Вызов принят игроком %s!*\n\nУчастники выбирают свои мемы в ЛС с ботом...", opponentName);
        telegramService.editMessageTextWithMarkdown(battle.getTelegramChatId(), battle.getTelegramMessageId(), text);

        sendMemeSelectionPrivateMessage(challenger.getId(), battle.getId());
        sendMemeSelectionPrivateMessage(opponent.getId(), battle.getId());

        return DuelActionResult.SUCCESS;
    }

    @Transactional
    public DuelActionResult declineDuel(long battleId, long userId) {
        Optional<MemeBattle> battleOpt = memeBattleRepository.findById(battleId);
        if (battleOpt.isEmpty()) {
            return DuelActionResult.NOT_FOUND;
        }

        MemeBattle battle = battleOpt.get();
        if (!"PENDING".equals(battle.getStatus())) {
            return DuelActionResult.INACTIVE;
        }

        if (!battle.getOpponentId().equals(userId) && !battle.getChallengerId().equals(userId)) {
            return DuelActionResult.UNAUTHORIZED;
        }

        battle.setStatus("DECLINED");
        memeBattleRepository.save(battle);

        String text;
        if (battle.getOpponentId().equals(userId)) {
            TelegramUser opponent = telegramUserRepository.findById(userId).orElseThrow();
            String opponentName =
                    opponent.getUsername() != null ? "@" + opponent.getUsername() : opponent.getFirstName();
            text = String.format("*Вызов отклонен игроком %s.*", opponentName);
        } else {
            TelegramUser challenger = telegramUserRepository.findById(userId).orElseThrow();
            String challengerName =
                    challenger.getUsername() != null ? "@" + challenger.getUsername() : challenger.getFirstName();
            text = String.format("*Вызов отменен игроком %s.*", challengerName);
        }

        telegramService.editMessageTextWithMarkdown(battle.getTelegramChatId(), battle.getTelegramMessageId(), text);

        return DuelActionResult.SUCCESS;
    }

    @Transactional
    public DuelActionResult selectDuelMeme(long battleId, long userId, String memeId) {
        Optional<MemeBattle> battleOpt = memeBattleRepository.findById(battleId);
        if (battleOpt.isEmpty()) {
            return DuelActionResult.NOT_FOUND;
        }

        MemeBattle battle = battleOpt.get();
        if (!"MEME_SELECTION".equals(battle.getStatus())) {
            return DuelActionResult.INACTIVE;
        }

        boolean isChallenger = battle.getChallengerId().equals(userId);
        boolean isOpponent = battle.getOpponentId().equals(userId);

        if (!isChallenger && !isOpponent) {
            return DuelActionResult.UNAUTHORIZED;
        }

        Optional<MemeModeration> memeOpt = memeModerationRepository.findById(memeId);
        if (memeOpt.isEmpty()) {
            return DuelActionResult.MEME_NOT_FOUND;
        }

        if (isChallenger) {
            if (Boolean.TRUE.equals(battle.getChallengerMemeSelected())) {
                return DuelActionResult.ALREADY_SELECTED;
            }
            battle.setMemeAId(memeId);
            battle.setChallengerMemeSelected(true);
        } else {
            if (Boolean.TRUE.equals(battle.getOpponentMemeSelected())) {
                return DuelActionResult.ALREADY_SELECTED;
            }
            battle.setMemeBId(memeId);
            battle.setOpponentMemeSelected(true);
        }

        memeBattleRepository.save(battle);

        telegramService.sendMessage(userId, "Мем успешно выбран! Ожидаем готовности второго игрока...");

        if (Boolean.TRUE.equals(battle.getChallengerMemeSelected())
                && Boolean.TRUE.equals(battle.getOpponentMemeSelected())) {
            memeDuelLifecycleService.startActiveDuel(battle);
        }

        return DuelActionResult.SUCCESS;
    }

    private void sendMemeSelectionPrivateMessage(long userId, long battleId) {
        List<MemeModeration> userMemes = memeModerationRepository.findByOwnerIdAndStatusAndVisibility(
                userId, ModerationStatus.APPROVED, MemeVisibility.PUBLIC);

        if (userMemes.isEmpty()) {
            telegramService.sendMessage(
                    userId, "У вас нет одобренных публичных мемов. Дуэль не может быть продолжена.");
            return;
        }

        StringBuilder sb =
                new StringBuilder("*ВЫБОР МЕМА ДЛЯ ДУЭЛИ*\n\nВыберите мем, который будет представлять вас в дуэли:\n");

        for (int i = 0; i < userMemes.size(); i++) {
            MemeModeration meme = userMemes.get(i);
            String desc = meme.getDescription();
            if (desc == null || desc.isBlank()) {
                desc = "Без описания (ID: " + meme.getId().substring(0, 8) + ")";
            } else if (desc.length() > 30) {
                desc = desc.substring(0, 27) + "...";
            }
            sb.append(String.format("%d. %s\n", i + 1, desc));
        }

        try {
            telegramService.sendDuelMemeSelection(userId, sb.toString(), battleId, userMemes);
        } catch (Exception e) {
            log.error("Failed to send private message to user {}: {}", userId, e.getMessage());
            Optional<MemeBattle> battleOpt = memeBattleRepository.findById(battleId);
            if (battleOpt.isPresent()) {
                MemeBattle battle = battleOpt.get();
                Optional<TelegramUser> userOpt = telegramUserRepository.findById(userId);
                String name = userOpt.map(u -> u.getUsername() != null ? "@" + u.getUsername() : u.getFirstName())
                        .orElse("Игрок");
                telegramService.sendMessage(
                        battle.getTelegramChatId(),
                        String.format("%s, напишите боту в ЛС (нажмите /start) для выбора мема!", name));
            }
        }
    }
}
