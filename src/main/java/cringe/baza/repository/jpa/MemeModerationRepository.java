package cringe.baza.repository.jpa;

import cringe.baza.domain.MemeModeration;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MemeModerationRepository extends JpaRepository<MemeModeration, String> {
    List<MemeModeration> findByStatus(String status);

    List<MemeModeration> findByStatusAndCreatedAtBefore(String status, LocalDateTime threshold);

    List<MemeModeration> findByStatusAndVisibility(String status, String visibility);
}
