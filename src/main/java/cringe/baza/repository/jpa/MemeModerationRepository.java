package cringe.baza.repository.jpa;

import cringe.baza.domain.MemeModeration;
import cringe.baza.model.MemeVisibility;
import cringe.baza.model.ModerationStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MemeModerationRepository extends JpaRepository<MemeModeration, String> {
    List<MemeModeration> findByStatus(ModerationStatus status);

    List<MemeModeration> findByStatusAndCreatedAtBefore(ModerationStatus status, LocalDateTime threshold);

    List<MemeModeration> findByStatusAndCreatedAtAfter(ModerationStatus status, LocalDateTime threshold);

    List<MemeModeration> findByStatusAndVisibility(ModerationStatus status, MemeVisibility visibility);

    List<MemeModeration> findByOwnerIdAndStatusAndVisibility(
            Long ownerId, ModerationStatus status, MemeVisibility visibility);
}
