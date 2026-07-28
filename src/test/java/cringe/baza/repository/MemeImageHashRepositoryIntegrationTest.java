package cringe.baza.repository;

import static org.junit.jupiter.api.Assertions.*;

import cringe.baza.domain.MemeImageHash;
import cringe.baza.domain.MemeModeration;
import cringe.baza.model.MemeVisibility;
import cringe.baza.model.ModerationStatus;
import cringe.baza.repository.jpa.MemeImageHashRepository;
import cringe.baza.repository.jpa.MemeModerationRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIf("isDockerAvailable")
class MemeImageHashRepositoryIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg17");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @MockitoBean
    private VectorStore vectorStore;

    @Autowired
    private MemeImageHashRepository memeImageHashRepository;

    @Autowired
    private MemeModerationRepository memeModerationRepository;

    static boolean isDockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    void findNearest_ExactMatch_FoundsIt() {
        memeImageHashRepository.save(new MemeImageHash("meme-1", 0xDEADBEEFL));

        Optional<String> result = memeImageHashRepository.findNearest(0xDEADBEEFL, 0);

        assertTrue(result.isPresent());
        assertEquals("meme-1", result.get());
    }

    @Test
    void findNearest_NegativeLongHighBit_Works() {
        long hashWithMsb = 0x8000000000000000L;
        memeImageHashRepository.save(new MemeImageHash("meme-msb", hashWithMsb));

        Optional<String> result = memeImageHashRepository.findNearest(hashWithMsb, 0);

        assertTrue(result.isPresent());
        assertEquals("meme-msb", result.get());
    }

    @Test
    void findNearest_WithinThreshold_FoundsIt() {
        memeImageHashRepository.save(new MemeImageHash("meme-a", 0L));

        Optional<String> result = memeImageHashRepository.findNearest(31L, 5);

        assertTrue(result.isPresent());
        assertEquals("meme-a", result.get());
    }

    @Test
    void findNearest_BeyondThreshold_NotFound() {
        memeImageHashRepository.save(new MemeImageHash("meme-a", 0L));

        Optional<String> result = memeImageHashRepository.findNearest(63L, 5);

        assertTrue(result.isEmpty());
    }

    @Test
    void findNearestApprovedExcluding_FindsApprovedDuplicate() {
        long hash = 0xDEADBEEFL;

        saveModeration("meme-original", ModerationStatus.APPROVED);
        memeImageHashRepository.save(new MemeImageHash("meme-original", hash));

        Optional<String> result = memeImageHashRepository.findNearestApprovedExcluding(hash, 0, "meme-dup");

        assertTrue(result.isPresent());
        assertEquals("meme-original", result.get());
    }

    @Test
    void findNearestApprovedExcluding_ExcludesSelf() {
        long hash = 0xDEADBEEFL;

        saveModeration("meme-1", ModerationStatus.APPROVED);
        memeImageHashRepository.save(new MemeImageHash("meme-1", hash));

        Optional<String> result = memeImageHashRepository.findNearestApprovedExcluding(hash, 0, "meme-1");

        assertTrue(result.isEmpty());
    }

    @Test
    void findNearestApprovedExcluding_OnlyQuarantinedExists_NotFound() {
        long hash = 0xDEADBEEFL;

        saveModeration("meme-quarantined", ModerationStatus.QUARANTINED);
        memeImageHashRepository.save(new MemeImageHash("meme-quarantined", hash));

        Optional<String> result = memeImageHashRepository.findNearestApprovedExcluding(hash, 0, "meme-dup");

        assertTrue(result.isEmpty());
    }

    private void saveModeration(String id, ModerationStatus status) {
        memeModerationRepository.save(
                new MemeModeration(id, "file-" + id, "desc", "ocr", 1L, MemeVisibility.PUBLIC, "", status, null));
    }
}
