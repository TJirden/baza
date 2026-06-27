package cringe.baza.repository.jpa;

import cringe.baza.domain.MemeModeration;
import cringe.baza.model.MemeVisibility;
import cringe.baza.model.ModerationStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MemeModerationRepository extends JpaRepository<MemeModeration, String> {
    List<MemeModeration> findByStatus(ModerationStatus status);

    List<MemeModeration> findByStatusAndCreatedAtBefore(ModerationStatus status, LocalDateTime threshold);

    List<MemeModeration> findByStatusAndCreatedAtAfter(ModerationStatus status, LocalDateTime threshold);

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
