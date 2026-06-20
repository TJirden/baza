package cringe.baza.repository.jpa;

import cringe.baza.domain.MemeBattleVote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MemeBattleVoteRepository extends JpaRepository<MemeBattleVote, Long> {
    boolean existsByBattleIdAndUserId(Long battleId, Long userId);

    void deleteByBattleId(Long battleId);
}
