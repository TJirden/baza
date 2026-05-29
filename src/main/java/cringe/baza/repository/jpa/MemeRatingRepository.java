package cringe.baza.repository.jpa;

import cringe.baza.domain.MemeRating;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MemeRatingRepository extends JpaRepository<MemeRating, String> {
    List<MemeRating> findTop5ByOrderByEloRatingDesc();
}
