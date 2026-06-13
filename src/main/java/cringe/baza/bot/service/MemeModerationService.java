package cringe.baza.bot.service;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.request.SendMessage;
import cringe.baza.domain.MemeModeration;
import cringe.baza.model.Meme;
import cringe.baza.processor.MemeProcessor;
import cringe.baza.repository.jpa.MemeModerationRepository;
import cringe.baza.repository.jpa.MemeReportRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemeModerationService {

    private final TelegramBot bot;
    private final MemeModerationRepository repository;
    private final MemeProcessor memeProcessor;
    private final MemeReportRepository memeReportRepository;

    public List<MemeModeration> getQuarantinedMemes() {
        return repository.findByStatus("QUARANTINED");
    }

    public boolean approveMeme(String id) {
        Optional<MemeModeration> moderationOpt = repository.findById(id);
        if (moderationOpt.isEmpty()) {
            return false;
        }

        MemeModeration moderation = moderationOpt.get();
        if ("APPROVED".equals(moderation.getStatus())) {
            return true;
        }

        List<Long> groupIds = new ArrayList<>();
        if (moderation.getGroupIds() != null && !moderation.getGroupIds().isBlank()) {
            for (String g : moderation.getGroupIds().split(",")) {
                try {
                    groupIds.add(Long.parseLong(g.trim()));
                } catch (NumberFormatException ignored) {
                }
            }
        }

        memeProcessor.save(new Meme(
                moderation.getId(),
                moderation.getDescription(),
                moderation.getOcrText(),
                moderation.getFileId(),
                moderation.getOwnerId(),
                moderation.getVisibility(),
                groupIds));

        moderation.setStatus("APPROVED");
        moderation.setModerationReason(null);
        repository.save(moderation);

        if (moderation.getOwnerId() != null) {
            try {
                bot.execute(new SendMessage(
                                moderation.getOwnerId(),
                                "*Ваш мем успешно одобрен!*\n\n"
                                        + "*ID мема:* `" + id + "`\n"
                                        + "Он прошел модерацию и теперь доступен в поиске.")
                        .parseMode(com.pengrad.telegrambot.model.request.ParseMode.Markdown));
            } catch (Exception e) {
                log.warn(
                        "Не удалось отправить уведомление о разблокировке автору мема {}: {}",
                        moderation.getOwnerId(),
                        e.getMessage());
            }
        }
        return true;
    }

    public boolean rejectMeme(String id, String reason) {
        Optional<MemeModeration> moderationOpt = repository.findById(id);
        if (moderationOpt.isEmpty()) {
            return false;
        }

        MemeModeration moderation = moderationOpt.get();

        repository.deleteById(id);

        memeProcessor.delete(id);

        if (moderation.getOwnerId() != null) {
            try {
                bot.execute(new SendMessage(
                                moderation.getOwnerId(),
                                "*Ваш мем был отклонен модератором!*\n\n"
                                        + "*ID мема:* `" + id + "`\n"
                                        + "*Причина:* "
                                        + (reason != null && !reason.isBlank()
                                                ? reason
                                                : "Нарушение правил сообщества"))
                        .parseMode(com.pengrad.telegrambot.model.request.ParseMode.Markdown));
            } catch (Exception e) {
                log.warn(
                        "Не удалось отправить уведомление об отклонении автору мема {}: {}",
                        moderation.getOwnerId(),
                        e.getMessage());
            }
        }
        return true;
    }

    @org.springframework.transaction.annotation.Transactional
    public ReportResult reportMeme(String memeId, Long userId) {
        if (memeReportRepository.existsByMemeIdAndReporterUserId(memeId, userId)) {
            long count = memeReportRepository.countByMemeId(memeId);
            return new ReportResult("ALREADY_REPORTED", count);
        }

        memeReportRepository.save(new cringe.baza.domain.MemeReport(memeId, userId));
        long count = memeReportRepository.countByMemeId(memeId);

        if (count >= 3) {
            memeProcessor.quarantine(memeId);
            memeReportRepository.deleteByMemeId(memeId);
            return new ReportResult("QUARANTINED", 3);
        }

        return new ReportResult("REPORT_ADDED", count);
    }

    public record ReportResult(String status, long currentReports) {}
}
