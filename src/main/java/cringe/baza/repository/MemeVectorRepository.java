package cringe.baza.repository;

import cringe.baza.model.IdRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import cringe.baza.model.Meme;
import org.springframework.jdbc.core.JdbcTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;

@Repository
@RequiredArgsConstructor
public class MemeVectorRepository implements IdRepository {

    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void save(String id, String description, String chatId) {
        Document document = new Document(
                id,
                description,
                Map.of(
                        "fileId", chatId
                )
        );
        vectorStore.add(List.of(document));
    }

    @Override
    public List<String> findSimilarIds(String query, int limit) {
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(limit)
                .similarityThreshold(0.5)
                .build();

        return vectorStore.similaritySearch(request)
                .stream()
                .map(Document::getId)
                .toList();
    }

    @Override
    public List<String> findSimilarFileIds(String query, int limit) {
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(limit)
                .similarityThreshold(0.5)
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
                            return new Meme(content, (String) metadata.get("fileId"));
                        } catch (Exception e) {
                            return new Meme(content, "");
                        }
                    },
                    id
            ));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}