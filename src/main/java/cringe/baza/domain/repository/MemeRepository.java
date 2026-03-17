package cringe.baza.domain.repository;

import cringe.baza.domain.model.Meme;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MemeRepository extends JpaRepository<Meme, String> {

    @Query("SELECT m FROM Meme m WHERE LOWER(m.description) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<Meme> searchByDescription(@Param("query") String query);

    List<Meme> findAllByOrderByCreatedAtDesc();

    List<Meme> findByFilenameContainingIgnoreCase(String filename);

    @Query("SELECT m FROM Meme m WHERE m.createdAt >= CURRENT_DATE - :days")
    List<Meme> findRecentMemes(@Param("days") int days);

    long count();
}