package cringe.baza.repository.jpa;

import cringe.baza.domain.MemeImageHash;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MemeImageHashRepository extends JpaRepository<MemeImageHash, String> {

    @Query(value = """
                    SELECT meme_id FROM meme_image_hashes
                    WHERE bit_count(image_hash::bit(64) # :queryHash::bit(64)) <= :maxDistance
                    ORDER BY bit_count(image_hash::bit(64) # :queryHash::bit(64))
                    LIMIT 1
                    """, nativeQuery = true)
    Optional<String> findNearest(@Param("queryHash") long queryHash, @Param("maxDistance") int maxDistance);

    @Query(value = """
                    SELECT h.meme_id FROM meme_image_hashes h
                    JOIN meme_moderation m ON h.meme_id = m.id
                    WHERE m.status = 'APPROVED'
                      AND h.meme_id != :excludeId
                      AND bit_count(h.image_hash::bit(64) # :queryHash::bit(64)) <= :maxDistance
                    ORDER BY bit_count(h.image_hash::bit(64) # :queryHash::bit(64))
                    LIMIT 1
                    """, nativeQuery = true)
    Optional<String> findNearestApprovedExcluding(
            @Param("queryHash") long queryHash,
            @Param("maxDistance") int maxDistance,
            @Param("excludeId") String excludeId);

    void deleteByMemeId(String memeId);
}
