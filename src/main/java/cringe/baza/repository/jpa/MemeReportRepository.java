package cringe.baza.repository.jpa;

import cringe.baza.domain.MemeReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MemeReportRepository extends JpaRepository<MemeReport, Long> {
    long countByMemeId(String memeId);

    boolean existsByMemeIdAndReporterUserId(String memeId, Long reporterUserId);

    void deleteByMemeId(String memeId);
}
