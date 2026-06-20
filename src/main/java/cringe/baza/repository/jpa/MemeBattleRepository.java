package cringe.baza.repository.jpa;

import cringe.baza.domain.MemeBattle;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MemeBattleRepository extends JpaRepository<MemeBattle, Long> {
    List<MemeBattle> findByStatus(String status);

    @Query("SELECT b FROM MemeBattle b WHERE b.memeAId = :memeId OR b.memeBId = :memeId OR b.winnerMemeId = :memeId")
    List<MemeBattle> findReferencingMeme(@Param("memeId") String memeId);
}
