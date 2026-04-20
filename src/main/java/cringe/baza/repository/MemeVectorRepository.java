package cringe.baza.repository;

import cringe.baza.model.IdRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class MemeVectorRepository implements IdRepository {

    private final VectorStore vectorStore;

    @Override
    public void save(String id, String description, long chatId) {
        Document document = new Document(
                id,
                description,
                Map.of(
                        "chatId", chatId
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

        return Optional.ofNullable(vectorStore.similaritySearch(request))
                .orElseGet(Collections::emptyList)
                .stream()
                .map(Document::getId)
                .toList();
    }

    @Override
    public void delete(String id) {
        vectorStore.delete(List.of(id));
    }

    @Override
    public void clear() {
        vectorStore.delete("chatId >= 0");
    }
}