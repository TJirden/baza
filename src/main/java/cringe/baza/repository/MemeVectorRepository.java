package cringe.baza.repository;

import cringe.baza.domain.MemeBattle;
import cringe.baza.domain.MemeModeration;
import cringe.baza.model.IdRepository;
import cringe.baza.model.Meme;
import cringe.baza.model.MemeVisibility;
import cringe.baza.model.ModerationStatus;
import cringe.baza.repository.jpa.MemeBattleRepository;
import cringe.baza.repository.jpa.MemeBattleVoteRepository;
import cringe.baza.repository.jpa.MemeModerationRepository;
import cringe.baza.repository.jpa.MemeRatingRepository;
import cringe.baza.repository.jpa.MemeReportRepository;
import cringe.baza.repository.jpa.MemeSwipeVoteRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class MemeVectorRepository implements IdRepository {

    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;
    private final MemeModerationRepository memeModerationRepository;
    private final MemeRatingRepository memeRatingRepository;
    private final MemeReportRepository memeReportRepository;
    private final MemeSwipeVoteRepository memeSwipeVoteRepository;
    private final MemeBattleRepository memeBattleRepository;
    private final MemeBattleVoteRepository memeBattleVoteRepository;

    @Override
    public void save(String id, Meme meme) {
        Document document = new Document(id, meme.description(), Map.of());
        vectorStore.add(List.of(document));

        String groupIdsStr = meme.groupIds() != null
                ? meme.groupIds().stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","))
                : "";

        MemeModeration moderation = new MemeModeration(
                id,
                meme.fileId(),
                meme.description(),
                meme.ocrText(),
                meme.ownerId(),
                meme.visibility(),
                groupIdsStr,
                ModerationStatus.APPROVED,
                null);
        memeModerationRepository.save(moderation);
    }

    public List<Map<String, Object>> findAll(int limit, int offset) {
        return jdbcTemplate.queryForList(
                "SELECT id FROM meme_moderation WHERE status = 'APPROVED' LIMIT ? OFFSET ?", limit, offset);
    }

    @Override
    public List<String> findSimilarIds(String query, int limit, Long userId, List<Long> userGroupIds) {
        SearchRequest request = SearchRequest.builder().query(query).topK(100).build();

        List<String> vectorIds = vectorStore.similaritySearch(request).stream()
                .map(Document::getId)
                .toList();
        String normalizedQuery = "%" + query.toLowerCase().replace('ё', 'е') + "%";
        List<String> textIds = jdbcTemplate.queryForList(
                "SELECT id FROM meme_moderation WHERE status = 'APPROVED' AND (REPLACE(LOWER(ocr_text), 'ё', 'е') ILIKE ? OR REPLACE(LOWER(description), 'ё', 'е') ILIKE ?)",
                String.class,
                normalizedQuery,
                normalizedQuery);

        Map<String, Double> rrfScores = new HashMap<>();

        for (int i = 0; i < vectorIds.size(); i++) {
            String id = vectorIds.get(i);
            rrfScores.put(id, rrfScores.getOrDefault(id, 0.0) + 1.0 / (60.0 + i));
        }

        for (int i = 0; i < textIds.size(); i++) {
            String id = textIds.get(i);
            rrfScores.put(id, rrfScores.getOrDefault(id, 0.0) + 1.0 / (60.0 + i));
        }

        List<String> combinedIds = rrfScores.entrySet().stream()
                .sorted((e1, e2) -> Double.compare(e2.getValue(), e1.getValue()))
                .map(Map.Entry::getKey)
                .toList();

        if (combinedIds.isEmpty()) {
            return List.of();
        }

        List<MemeModeration> matches = memeModerationRepository.findAllById(combinedIds).stream()
                .filter(m -> ModerationStatus.APPROVED == m.getStatus())
                .filter(m -> {
                    if (MemeVisibility.PUBLIC == m.getVisibility()) {
                        return true;
                    }
                    if (userId != null && userId.equals(m.getOwnerId())) {
                        return true;
                    }
                    if (MemeVisibility.GROUP == m.getVisibility() && userGroupIds != null && !userGroupIds.isEmpty()) {
                        if (m.getGroupIds() != null && !m.getGroupIds().isBlank()) {
                            for (String g : m.getGroupIds().split(",")) {
                                try {
                                    if (userGroupIds.contains(Long.parseLong(g.trim()))) {
                                        return true;
                                    }
                                } catch (NumberFormatException ignored) {
                                }
                            }
                        }
                    }
                    return false;
                })
                .toList();

        Map<String, Integer> orderMap = new HashMap<>();
        for (int i = 0; i < combinedIds.size(); i++) {
            orderMap.put(combinedIds.get(i), i);
        }

        return matches.stream()
                .map(MemeModeration::getId)
                .sorted((id1, id2) -> Integer.compare(orderMap.get(id1), orderMap.get(id2)))
                .limit(limit)
                .toList();
    }

    @Override
    public List<String> findSimilarFileIds(String query, int limit, Long userId, List<Long> userGroupIds) {
        List<String> ids = findSimilarIds(query, limit, userId, userGroupIds);
        return ids.stream()
                .map(memeModerationRepository::findById)
                .flatMap(Optional::stream)
                .map(MemeModeration::getFileId)
                .filter(fileId -> fileId != null && !fileId.isBlank())
                .toList();
    }

    @Transactional
    @Override
    public void delete(String id) {
        if (memeRatingRepository.existsById(id)) {
            memeRatingRepository.deleteById(id);
        }
        memeReportRepository.deleteByMemeId(id);
        memeSwipeVoteRepository.deleteByMemeId(id);

        List<MemeBattle> battles = memeBattleRepository.findReferencingMeme(id);
        for (MemeBattle battle : battles) {
            memeBattleVoteRepository.deleteByBattleId(battle.getId());
        }
        memeBattleRepository.deleteAll(battles);

        vectorStore.delete(List.of(id));
        memeModerationRepository.deleteById(id);
    }

    @Override
    public void quarantine(String id) {
        vectorStore.delete(List.of(id));
        memeModerationRepository.findById(id).ifPresent(m -> {
            m.setStatus(ModerationStatus.QUARANTINED);
            m.setModerationReason("Жалобы пользователей");
            memeModerationRepository.save(m);
        });
    }

    @Override
    public Optional<Meme> findById(String id) {
        return memeModerationRepository
                .findById(id)
                .filter(m -> ModerationStatus.APPROVED == m.getStatus())
                .map(m -> {
                    List<Long> groupIds = new ArrayList<>();
                    if (m.getGroupIds() != null && !m.getGroupIds().isBlank()) {
                        for (String g : m.getGroupIds().split(",")) {
                            try {
                                groupIds.add(Long.parseLong(g.trim()));
                            } catch (NumberFormatException ignored) {
                            }
                        }
                    }
                    return new Meme(
                            m.getId(),
                            m.getDescription(),
                            m.getOcrText(),
                            m.getFileId(),
                            m.getOwnerId(),
                            m.getVisibility(),
                            groupIds);
                });
    }

    public Optional<String> findDuplicateMemeId(String description, double similarityThreshold) {
        try {
            SearchRequest request = SearchRequest.builder()
                    .query(description)
                    .topK(1)
                    .similarityThreshold(similarityThreshold)
                    .build();

            List<Document> results = vectorStore.similaritySearch(request);
            if (results != null && !results.isEmpty()) {
                return Optional.of(results.get(0).getId());
            }
        } catch (Exception e) {
        }
        return Optional.empty();
    }
}
