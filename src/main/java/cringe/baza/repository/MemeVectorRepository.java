package cringe.baza.repository;

import cringe.baza.model.IdRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class MemeVectorRepository implements IdRepository {

    private final VectorStore vectorStore;

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
    public void clear() {
        vectorStore.delete("fileId != ''");
    }
}