package cringe.baza.repository.jpa;

import cringe.baza.domain.MemeBattle;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MemeBattleRepository extends JpaRepository<MemeBattle, Long> {
    List<MemeBattle> findByStatus(String status);
}
