package cringe.baza.repository.jpa;

import cringe.baza.domain.MemeImageHash;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MemeImageHashRepository extends JpaRepository<MemeImageHash, String> {

    // TODO: запрос ниже — full table scan по meme_image_hashes. При росте объёма заменить на
    //       banded (LSH) индекс: разбить 64-битный хеш на N полос, завести N B-tree-колонок и
    //       искать кандидатов по точному совпадению хотя бы одной полосы (по принципу Дирихле
    //       при пороге T нужно T+1 полос), затем фильтровать полным bit_count.
    @Query(value = """
                    WITH candidates AS (
                        SELECT h.meme_id AS meme_id, bit_count(h.image_hash::bit(64) # :queryHash::bit(64)) AS dist
                        FROM meme_image_hashes h
                        JOIN meme_moderation m ON h.meme_id = m.id
                        WHERE m.status = 'APPROVED'
                    )
                    SELECT meme_id FROM candidates
                    WHERE dist <= :maxDistance
                    ORDER BY dist
                    LIMIT 1
                    """, nativeQuery = true)
    Optional<String> findNearestApproved(@Param("queryHash") long queryHash, @Param("maxDistance") int maxDistance);

    @Query(value = """
                    WITH candidates AS (
                        SELECT h.meme_id AS meme_id, bit_count(h.image_hash::bit(64) # :queryHash::bit(64)) AS dist
                        FROM meme_image_hashes h
                        JOIN meme_moderation m ON h.meme_id = m.id
                        WHERE m.status = 'APPROVED'
                          AND h.meme_id != :excludeId
                    )
                    SELECT meme_id FROM candidates
                    WHERE dist <= :maxDistance
                    ORDER BY dist
                    LIMIT 1
                    """, nativeQuery = true)
    Optional<String> findNearestApprovedExcluding(
            @Param("queryHash") long queryHash,
            @Param("maxDistance") int maxDistance,
            @Param("excludeId") String excludeId);

    void deleteByMemeId(String memeId);
}
