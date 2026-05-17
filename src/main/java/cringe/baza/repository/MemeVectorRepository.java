package cringe.baza.repository;

import cringe.baza.model.IdRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import cringe.baza.model.Meme;
import org.springframework.jdbc.core.JdbcTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;

@Repository
@RequiredArgsConstructor
public class MemeVectorRepository implements IdRepository {

    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void save(String id, Meme meme) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("fileId", meme.fileId());
        if (meme.ownerId() != null) {
            metadata.put("ownerId", String.valueOf(meme.ownerId()));
        }
        if (meme.visibility() != null) {
            metadata.put("visibility", meme.visibility());
        }
        if (meme.groupIds() != null) {
            for (Long groupId : meme.groupIds()) {
                metadata.put("group_" + groupId, true);
            }
        }

        Document document = new Document(id, meme.description(), metadata);
        vectorStore.add(List.of(document));
    }

    public List<Map<String, Object>> findAll(int limit, int offset) {
        return jdbcTemplate.queryForList(
                "SELECT id, content, metadata FROM vector_store ORDER BY id LIMIT ? OFFSET ?",
                limit, offset
        );
    }

    private String buildFilterExpression(Long userId, List<Long> userGroupIds) {
        StringBuilder filter = new StringBuilder();
        filter.append("visibility == 'PUBLIC'");

        if (userId != null) {
            filter.append(String.format(" || ownerId == '%d'", userId));
        }

        if (userGroupIds != null && !userGroupIds.isEmpty()) {
            for (Long groupId : userGroupIds) {
                filter.append(String.format(" || group_%d == true", groupId));
            }
        }
        return filter.toString();
    }

    @Override
    public List<String> findSimilarIds(String query, int limit, Long userId, List<Long> userGroupIds) {
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(limit)
                .similarityThreshold(0.5)
                .filterExpression(buildFilterExpression(userId, userGroupIds))
                .build();

        return vectorStore.similaritySearch(request)
                .stream()
                .map(Document::getId)
                .toList();
    }

    @Override
    public List<String> findSimilarFileIds(String query, int limit, Long userId, List<Long> userGroupIds) {
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(limit)
                .similarityThreshold(0.5)
                .filterExpression(buildFilterExpression(userId, userGroupIds))
                .build();

        return vectorStore.similaritySearch(request)
                .stream()
                .map(doc -> (String) doc.getMetadata().get("fileId"))
                .filter(fileId -> fileId != null && !fileId.isBlank())
                .toList();
    }

    @Override
    public void delete(String id) {
        vectorStore.delete(List.of(id));
    }

    @Override
    public Optional<Meme> findById(String id) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    "SELECT content, metadata FROM vector_store WHERE id = ?::uuid",
                    (rs, rowNum) -> {
                        String content = rs.getString("content");
                        String metadataStr = rs.getString("metadata");
                        try {
                            Map<String, Object> metadata = objectMapper.readValue(metadataStr, Map.class);
                            String fileId = (String) metadata.get("fileId");
                            Long ownerId = metadata.containsKey("ownerId") ? Long.parseLong(String.valueOf(metadata.get("ownerId"))) : null;
                            String visibility = (String) metadata.get("visibility");
                            
                            List<Long> groupIds = new ArrayList<>();
                            for (String key : metadata.keySet()) {
                                if (key.startsWith("group_") && Boolean.TRUE.equals(metadata.get(key))) {
                                    try {
                                        groupIds.add(Long.parseLong(key.substring(6)));
                                    } catch (NumberFormatException ignored) {}
                                }
                            }
                            
                            return new Meme(id, content, fileId, ownerId, visibility, groupIds);
                        } catch (Exception e) {
                            return null;
                        }
                    },
                    id
            ));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}