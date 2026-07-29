package cringe.baza.repository.jpa;

import cringe.baza.domain.MemeModeration;
import cringe.baza.model.MemeVisibility;
import cringe.baza.model.ModerationStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MemeModerationRepository extends JpaRepository<MemeModeration, String> {
    List<MemeModeration> findByStatus(ModerationStatus status);

    List<MemeModeration> findByStatusAndCreatedAtBefore(ModerationStatus status, LocalDateTime threshold);

    List<MemeModeration> findByStatusAndCreatedAtAfter(ModerationStatus status, LocalDateTime threshold);

    @Query(
            value = "SELECT id FROM meme_moderation WHERE status = 'PENDING' "
                    + "AND created_at < :threshold "
                    + "AND (last_enqueued_at IS NULL OR last_enqueued_at < :enqueueThreshold) "
                    + "ORDER BY created_at ASC LIMIT 100",
            nativeQuery = true)
    List<String> findPendingIdsOlderThan(
            @Param("threshold") LocalDateTime threshold, @Param("enqueueThreshold") LocalDateTime enqueueThreshold);

    @Modifying
    @Query("UPDATE MemeModeration m SET m.status = 'APPROVED', m.description = :description, "
            + "m.ocrText = :ocrText, m.moderationReason = NULL "
            + "WHERE m.id = :id AND m.status = 'PENDING'")
    int updateToApprovedIfPending(
            @Param("id") String id, @Param("description") String description, @Param("ocrText") String ocrText);

    @Modifying
    @Query("UPDATE MemeModeration m SET m.status = 'QUARANTINED', m.description = :description, "
            + "m.ocrText = :ocrText, m.moderationReason = :reason "
            + "WHERE m.id = :id AND m.status = 'PENDING'")
    int updateToQuarantinedIfPending(
            @Param("id") String id,
            @Param("description") String description,
            @Param("ocrText") String ocrText,
            @Param("reason") String reason);

    @Modifying
    @Query("UPDATE MemeModeration m SET m.retryCount = m.retryCount + 1, m.lastEnqueuedAt = :now "
            + "WHERE m.id = :id AND m.status = 'PENDING'")
    int incrementRetryCount(@Param("id") String id, @Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE MemeModeration m SET m.lastEnqueuedAt = :now WHERE m.id = :id")
    int updateLastEnqueuedAt(@Param("id") String id, @Param("now") LocalDateTime now);

    List<MemeModeration> findByStatusAndVisibility(ModerationStatus status, MemeVisibility visibility);

    List<MemeModeration> findByOwnerIdAndStatusAndVisibility(
            Long ownerId, ModerationStatus status, MemeVisibility visibility);

    @Query(
            value =
                    "SELECT id FROM meme_moderation WHERE status = 'APPROVED' AND (REPLACE(LOWER(ocr_text), 'ё', 'е') ILIKE :query OR REPLACE(LOWER(description), 'ё', 'е') ILIKE :query)",
            nativeQuery = true)
    List<String> findApprovedIdsByTextSearch(@Param("query") String query);

    @Query(
            value = "SELECT id FROM meme_moderation WHERE status = 'APPROVED' LIMIT :limit OFFSET :offset",
            nativeQuery = true)
    List<String> findApprovedIds(@Param("limit") int limit, @Param("offset") int offset);
}
