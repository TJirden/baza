package cringe.baza.bot.service;

import cringe.baza.domain.MemeModeration;
import cringe.baza.model.Meme;
import cringe.baza.model.ModerationStatus;
import cringe.baza.processor.MemeProcessor;
import cringe.baza.repository.jpa.MemeModerationRepository;
import cringe.baza.repository.jpa.MemeReportRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class MemeModerationService {

    private final TelegramService telegramService;
    private final MemeModerationRepository repository;
    private final MemeProcessor memeProcessor;
    private final MemeReportRepository memeReportRepository;
    private final int complaintsThreshold;

    public MemeModerationService(
            TelegramService telegramService,
            MemeModerationRepository repository,
            MemeProcessor memeProcessor,
            MemeReportRepository memeReportRepository,
            @Value("${app.bot.complaints-threshold}") int complaintsThreshold) {
        this.telegramService = telegramService;
        this.repository = repository;
        this.memeProcessor = memeProcessor;
        this.memeReportRepository = memeReportRepository;
        this.complaintsThreshold = complaintsThreshold;
    }

    public List<MemeModeration> getQuarantinedMemes() {
        return repository.findByStatus(ModerationStatus.QUARANTINED);
    }

    public boolean approveMeme(String id) {
        Optional<MemeModeration> moderationOpt = repository.findById(id);
        if (moderationOpt.isEmpty()) {
            return false;
        }

        MemeModeration moderation = moderationOpt.get();
        if (ModerationStatus.APPROVED == moderation.getStatus()) {
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

        moderation.setStatus(ModerationStatus.APPROVED);
        moderation.setModerationReason(null);
        repository.save(moderation);

        if (moderation.getOwnerId() != null) {
            try {
                telegramService.sendMessageWithMarkdown(
                        moderation.getOwnerId(),
                        "*Ваш мем успешно одобрен!*\n\n"
                                + "*ID мема:* `" + id + "`\n"
                                + "Он прошел модерацию и теперь доступен в поиске.");
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
                telegramService.sendMessageWithMarkdown(
                        moderation.getOwnerId(),
                        "*Ваш мем был отклонен модератором!*\n\n"
                                + "*ID мема:* `" + id + "`\n"
                                + "*Причина:* "
                                + (reason != null && !reason.isBlank() ? reason : "Нарушение правил сообщества"));
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

        if (count >= complaintsThreshold) {
            memeProcessor.quarantine(memeId);
            memeReportRepository.deleteByMemeId(memeId);
            return new ReportResult("QUARANTINED", complaintsThreshold);
        }

        return new ReportResult("REPORT_ADDED", count);
    }

    public record ReportResult(String status, long currentReports) {}
}
