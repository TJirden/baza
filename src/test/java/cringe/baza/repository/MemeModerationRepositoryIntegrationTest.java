package cringe.baza.repository;

import static org.junit.jupiter.api.Assertions.*;

import cringe.baza.domain.MemeModeration;
import cringe.baza.model.MemeVisibility;
import cringe.baza.model.ModerationStatus;
import cringe.baza.repository.jpa.MemeModerationRepository;
import java.time.LocalDateTime;
import java.util.List;
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
class MemeModerationRepositoryIntegrationTest {

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
    private MemeModerationRepository memeModerationRepository;

    static boolean isDockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Exception e) {
            return false;
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // claimForProcessing
    // ──────────────────────────────────────────────────────────────────

    @Test
    void claimForProcessing_Pending_TransitionsToProcessing() {
        saveModeration("meme-1", ModerationStatus.PENDING);

        LocalDateTime now = LocalDateTime.now();
        int updated = memeModerationRepository.claimForProcessing("meme-1", now, now.minusMinutes(120));

        assertEquals(1, updated);
        MemeModeration result = memeModerationRepository.findById("meme-1").orElseThrow();
        assertEquals(ModerationStatus.PROCESSING, result.getStatus());
        assertNotNull(result.getProcessingStartedAt());
    }

    @Test
    void claimForProcessing_ProcessingAndStartedAtNull_Reclaims() {
        saveModeration("meme-1", ModerationStatus.PROCESSING);
        MemeModeration m = memeModerationRepository.findById("meme-1").orElseThrow();
        m.setProcessingStartedAt(null);
        memeModerationRepository.save(m);

        LocalDateTime now = LocalDateTime.now();
        int updated = memeModerationRepository.claimForProcessing("meme-1", now, now.minusMinutes(120));

        assertEquals(1, updated);
        MemeModeration result = memeModerationRepository.findById("meme-1").orElseThrow();
        assertEquals(ModerationStatus.PROCESSING, result.getStatus());
        assertNotNull(result.getProcessingStartedAt());
    }

    @Test
    void claimForProcessing_ProcessingAndStuck_Reclaims() {
        saveModeration("meme-1", ModerationStatus.PROCESSING);
        MemeModeration m = memeModerationRepository.findById("meme-1").orElseThrow();
        LocalDateTime oldTime = LocalDateTime.now().minusMinutes(200);
        m.setProcessingStartedAt(oldTime);
        memeModerationRepository.save(m);

        LocalDateTime now = LocalDateTime.now();
        int updated = memeModerationRepository.claimForProcessing("meme-1", now, now.minusMinutes(120));

        assertEquals(1, updated);
        MemeModeration result = memeModerationRepository.findById("meme-1").orElseThrow();
        assertEquals(ModerationStatus.PROCESSING, result.getStatus());
        assertNotEquals(oldTime, result.getProcessingStartedAt());
    }

    @Test
    void claimForProcessing_ProcessingAndFreshStartedAt_DoesNotReclaim() {
        saveModeration("meme-1", ModerationStatus.PROCESSING);
        MemeModeration m = memeModerationRepository.findById("meme-1").orElseThrow();
        LocalDateTime recentTime = LocalDateTime.now().minusMinutes(5);
        m.setProcessingStartedAt(recentTime);
        memeModerationRepository.save(m);

        LocalDateTime now = LocalDateTime.now();
        int updated = memeModerationRepository.claimForProcessing("meme-1", now, now.minusMinutes(120));

        assertEquals(0, updated);
    }

    @Test
    void claimForProcessing_Approved_DoesNotReclaim() {
        saveModeration("meme-1", ModerationStatus.APPROVED);

        LocalDateTime now = LocalDateTime.now();
        int updated = memeModerationRepository.claimForProcessing("meme-1", now, now.minusMinutes(120));

        assertEquals(0, updated);
    }

    @Test
    void claimForProcessing_Quarantined_DoesNotReclaim() {
        saveModeration("meme-1", ModerationStatus.QUARANTINED);

        LocalDateTime now = LocalDateTime.now();
        int updated = memeModerationRepository.claimForProcessing("meme-1", now, now.minusMinutes(120));

        assertEquals(0, updated);
    }

    // ──────────────────────────────────────────────────────────────────
    // incrementRetryCount
    // ──────────────────────────────────────────────────────────────────

    @Test
    void incrementRetryCount_Processing_IncrementsAndNullsStartedAt() {
        saveModeration("meme-1", ModerationStatus.PROCESSING);
        MemeModeration m = memeModerationRepository.findById("meme-1").orElseThrow();
        m.setProcessingStartedAt(LocalDateTime.now());
        m.setRetryCount(2);
        memeModerationRepository.save(m);

        int updated = memeModerationRepository.incrementRetryCount("meme-1", LocalDateTime.now());

        assertEquals(1, updated);
        MemeModeration result = memeModerationRepository.findById("meme-1").orElseThrow();
        assertEquals(ModerationStatus.PROCESSING, result.getStatus());
        assertEquals(3, result.getRetryCount());
        assertNull(result.getProcessingStartedAt());
    }

    @Test
    void incrementRetryCount_Pending_DoesNotUpdate() {
        saveModeration("meme-1", ModerationStatus.PENDING);

        int updated = memeModerationRepository.incrementRetryCount("meme-1", LocalDateTime.now());

        assertEquals(0, updated);
    }

    // ──────────────────────────────────────────────────────────────────
    // findByStatusAndProcessingStartedAtBefore
    // ──────────────────────────────────────────────────────────────────

    @Test
    void findByStatusAndProcessingStartedAtBefore_StuckProcessing_Finds() {
        saveModeration("meme-stuck", ModerationStatus.PROCESSING);
        MemeModeration m = memeModerationRepository.findById("meme-stuck").orElseThrow();
        m.setProcessingStartedAt(LocalDateTime.now().minusHours(50));
        memeModerationRepository.save(m);

        List<MemeModeration> result = memeModerationRepository.findByStatusAndProcessingStartedAtBefore(
                ModerationStatus.PROCESSING, LocalDateTime.now().minusHours(48));

        assertEquals(1, result.size());
        assertEquals("meme-stuck", result.get(0).getId());
    }

    @Test
    void findByStatusAndProcessingStartedAtBefore_FreshProcessing_NotFound() {
        saveModeration("meme-fresh", ModerationStatus.PROCESSING);
        MemeModeration m = memeModerationRepository.findById("meme-fresh").orElseThrow();
        m.setProcessingStartedAt(LocalDateTime.now().minusMinutes(5));
        memeModerationRepository.save(m);

        List<MemeModeration> result = memeModerationRepository.findByStatusAndProcessingStartedAtBefore(
                ModerationStatus.PROCESSING, LocalDateTime.now().minusHours(48));

        assertTrue(result.isEmpty());
    }

    // ──────────────────────────────────────────────────────────────────
    // findPendingIdsOlderThan
    // ──────────────────────────────────────────────────────────────────

    @Test
    void findPendingIdsOlderThan_OldPending_Finds() {
        saveModeration("meme-old", ModerationStatus.PENDING);
        MemeModeration m = memeModerationRepository.findById("meme-old").orElseThrow();
        m.setCreatedAt(LocalDateTime.now().minusMinutes(30));
        memeModerationRepository.save(m);

        List<String> result = memeModerationRepository.findPendingIdsOlderThan(
                LocalDateTime.now().minusMinutes(5), LocalDateTime.now().minusMinutes(120));

        assertTrue(result.contains("meme-old"));
    }

    @Test
    void findPendingIdsOlderThan_Processing_DoesNotFind() {
        saveModeration("meme-processing", ModerationStatus.PROCESSING);
        MemeModeration m = memeModerationRepository.findById("meme-processing").orElseThrow();
        m.setCreatedAt(LocalDateTime.now().minusMinutes(30));
        m.setProcessingStartedAt(LocalDateTime.now().minusMinutes(3));
        memeModerationRepository.save(m);

        List<String> result = memeModerationRepository.findPendingIdsOlderThan(
                LocalDateTime.now().minusMinutes(5), LocalDateTime.now().minusMinutes(120));

        assertFalse(result.contains("meme-processing"));
    }

    // ──────────────────────────────────────────────────────────────────
    // findStuckProcessingIds
    // ──────────────────────────────────────────────────────────────────

    @Test
    void findStuckProcessingIds_StuckProcessing_Finds() {
        saveModeration("meme-stuck", ModerationStatus.PROCESSING);
        MemeModeration m = memeModerationRepository.findById("meme-stuck").orElseThrow();
        m.setProcessingStartedAt(LocalDateTime.now().minusMinutes(200));
        m.setLastEnqueuedAt(LocalDateTime.now().minusMinutes(200));
        memeModerationRepository.save(m);

        List<String> result = memeModerationRepository.findStuckProcessingIds(
                LocalDateTime.now().minusMinutes(120), LocalDateTime.now().minusMinutes(120));

        assertTrue(result.contains("meme-stuck"));
    }

    @Test
    void findStuckProcessingIds_FreshProcessing_DoesNotFind() {
        saveModeration("meme-fresh", ModerationStatus.PROCESSING);
        MemeModeration m = memeModerationRepository.findById("meme-fresh").orElseThrow();
        m.setProcessingStartedAt(LocalDateTime.now().minusMinutes(5));
        memeModerationRepository.save(m);

        List<String> result = memeModerationRepository.findStuckProcessingIds(
                LocalDateTime.now().minusMinutes(120), LocalDateTime.now().minusMinutes(120));

        assertFalse(result.contains("meme-fresh"));
    }

    @Test
    void findStuckProcessingIds_StartedAtNullButRecentlyEnqueued_DoesNotFind() {
        saveModeration("meme-retry", ModerationStatus.PROCESSING);
        MemeModeration m = memeModerationRepository.findById("meme-retry").orElseThrow();
        m.setProcessingStartedAt(null);
        m.setLastEnqueuedAt(LocalDateTime.now().minusMinutes(2));
        memeModerationRepository.save(m);

        List<String> result = memeModerationRepository.findStuckProcessingIds(
                LocalDateTime.now().minusMinutes(120), LocalDateTime.now().minusMinutes(120));

        assertFalse(result.contains("meme-retry"));
    }

    @Test
    void findStuckProcessingIds_StartedAtNullAndOldEnqueue_Finds() {
        saveModeration("meme-retry", ModerationStatus.PROCESSING);
        MemeModeration m = memeModerationRepository.findById("meme-retry").orElseThrow();
        m.setProcessingStartedAt(null);
        m.setLastEnqueuedAt(LocalDateTime.now().minusMinutes(200));
        memeModerationRepository.save(m);

        List<String> result = memeModerationRepository.findStuckProcessingIds(
                LocalDateTime.now().minusMinutes(120), LocalDateTime.now().minusMinutes(120));

        assertTrue(result.contains("meme-retry"));
    }

    // ──────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────

    private void saveModeration(String id, ModerationStatus status) {
        memeModerationRepository.save(
                new MemeModeration(id, "file-" + id, "desc", "ocr", 1L, MemeVisibility.PUBLIC, "", status, null));
    }
}
