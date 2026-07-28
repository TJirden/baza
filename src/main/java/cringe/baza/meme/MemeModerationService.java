package cringe.baza.meme;

import cringe.baza.bot.service.TelegramService;
import cringe.baza.domain.MemeImageHash;
import cringe.baza.domain.MemeModeration;
import cringe.baza.model.Meme;
import cringe.baza.model.ModerationStatus;
import cringe.baza.model.ReportStatus;
import cringe.baza.repository.jpa.MemeImageHashRepository;
import cringe.baza.repository.jpa.MemeModerationRepository;
import cringe.baza.repository.jpa.MemeReportRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class MemeModerationService {

    private final TelegramService telegramService;
    private final MemeModerationRepository repository;
    private final MemeProcessor memeProcessor;
    private final MemeReportRepository memeReportRepository;
    private final MemeImageHashRepository memeImageHashRepository;
    private final int complaintsThreshold;
    private final int imagePhashThreshold;

    public MemeModerationService(
            TelegramService telegramService,
            MemeModerationRepository repository,
            MemeProcessor memeProcessor,
            MemeReportRepository memeReportRepository,
            MemeImageHashRepository memeImageHashRepository,
            @Value("${app.bot.complaints-threshold}") int complaintsThreshold,
            @Value("${app.dedup.image-phash-threshold}") int imagePhashThreshold) {
        this.telegramService = telegramService;
        this.repository = repository;
        this.memeProcessor = memeProcessor;
        this.memeReportRepository = memeReportRepository;
        this.memeImageHashRepository = memeImageHashRepository;
        this.complaintsThreshold = complaintsThreshold;
        this.imagePhashThreshold = imagePhashThreshold;
    }

    public sealed interface ApprovalResult {
        record Approved() implements ApprovalResult {}

        record NotFound() implements ApprovalResult {}

        record DuplicateBlocked(String duplicateOf) implements ApprovalResult {}
    }

    public List<MemeModeration> getQuarantinedMemes() {
        return repository.findByStatus(ModerationStatus.QUARANTINED);
    }

    @Transactional
    public ApprovalResult approveMeme(String id) {
        Optional<MemeModeration> moderationOpt = repository.findById(id);
        if (moderationOpt.isEmpty()) {
            return new ApprovalResult.NotFound();
        }

        MemeModeration moderation = moderationOpt.get();
        if (ModerationStatus.APPROVED == moderation.getStatus()) {
            return new ApprovalResult.Approved();
        }

        Optional<MemeImageHash> hashOpt = memeImageHashRepository.findById(id);
        if (hashOpt.isPresent()) {
            long imageHash = hashOpt.get().getImageHash();
            Optional<String> duplicateIdOpt =
                    memeImageHashRepository.findNearestApprovedExcluding(imageHash, imagePhashThreshold, id);
            if (duplicateIdOpt.isPresent()) {
                String duplicateId = duplicateIdOpt.get();
                log.warn("Одобрение мема {} заблокировано: визуальный дубликат одобренного мема {}", id, duplicateId);
                moderation.setStatus(ModerationStatus.QUARANTINED);
                moderation.setModerationReason("Визуальный дубликат одобренного мема: " + duplicateId);
                repository.save(moderation);
                return new ApprovalResult.DuplicateBlocked(duplicateId);
            }
        } else {
            log.warn("Визуальный хеш для мема {} не найден, проверка дубликатов при одобрении пропущена", id);
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
        return new ApprovalResult.Approved();
    }

    public boolean rejectMeme(String id, String reason) {
        Optional<MemeModeration> moderationOpt = repository.findById(id);
        if (moderationOpt.isEmpty()) {
            return false;
        }

        MemeModeration moderation = moderationOpt.get();

        memeProcessor.delete(id);
        repository.delete(moderation);

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
        if (!repository.existsById(memeId)) {
            return new ReportResult(ReportStatus.NOT_FOUND, 0);
        }

        if (memeReportRepository.existsByMemeIdAndReporterUserId(memeId, userId)) {
            long count = memeReportRepository.countByMemeId(memeId);
            return new ReportResult(ReportStatus.ALREADY_REPORTED, count);
        }

        memeReportRepository.save(new cringe.baza.domain.MemeReport(memeId, userId));
        long count = memeReportRepository.countByMemeId(memeId);

        if (count >= complaintsThreshold) {
            memeProcessor.quarantine(memeId);
            memeReportRepository.deleteByMemeId(memeId);
            return new ReportResult(ReportStatus.QUARANTINED, complaintsThreshold);
        }

        return new ReportResult(ReportStatus.REPORT_ADDED, count);
    }

    public record ReportResult(ReportStatus status, long currentReports) {}
}
