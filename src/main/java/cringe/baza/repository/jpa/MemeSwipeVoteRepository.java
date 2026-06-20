package cringe.baza.repository.jpa;

import cringe.baza.domain.MemeSwipeVote;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MemeSwipeVoteRepository extends JpaRepository<MemeSwipeVote, Long> {
    boolean existsByMemeIdAndUserId(String memeId, Long userId);

    @Query("SELECT v.memeId FROM MemeSwipeVote v WHERE v.userId = :userId")
    List<String> findVotedMemeIdsByUserId(@Param("userId") Long userId);

    void deleteByMemeId(String memeId);
}
