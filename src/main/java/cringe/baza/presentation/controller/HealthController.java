package cringe.baza.presentation.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@Slf4j
public class HealthController {

    private final DataSource dataSource;

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "OK");
        response.put("timestamp", System.currentTimeMillis());

        try (Connection conn = dataSource.getConnection()) {
            response.put("database", "connected");
            log.debug("Database connection OK");
        } catch (Exception e) {
            response.put("database", "error: " + e.getMessage());
            log.error("Database connection error: {}", e.getMessage());
        }

        return response;
    }
}