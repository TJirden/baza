package cringe.baza.bot.config;

import com.google.genai.Client;
import com.google.genai.errors.ApiException;
import com.google.genai.errors.GenAiIOException;
import com.google.genai.types.HttpOptions;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;

@Configuration
@ConditionalOnClass({Client.class, RetryTemplate.class})
public class AiConfig {

    @Bean
    RetryTemplate retryTemplate(
            @Value("${app.ai.retry.max-attempts:3}") int maxAttempts,
            @Value("${app.ai.retry.initial-interval:1000}") long initialInterval,
            @Value("${app.ai.retry.multiplier:2}") double multiplier,
            @Value("${app.ai.retry.max-interval:8000}") long maxInterval) {
        RetryPolicy retryPolicy = RetryPolicy.builder()
                .maxRetries(maxAttempts)
                .includes(ApiException.class)
                .includes(GenAiIOException.class)
                .includes(ResourceAccessException.class)
                .excludes(CallNotPermittedException.class)
                .delay(Duration.ofMillis(initialInterval))
                .multiplier(multiplier)
                .maxDelay(Duration.ofMillis(maxInterval))
                .predicate(AiConfig::isTransient)
                .build();
        return new RetryTemplate(retryPolicy);
    }

    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
    Client googleGenAiClient(
            @Value("${spring.ai.google.genai.api-key:}") String apiKey,
            @Value("${spring.ai.google.genai.project-id:dummy-project}") String projectId,
            @Value("${spring.ai.google.genai.location:us-central1}") String location,
            @Value("${app.ai.timeout-ms:30000}") int timeoutMs) {
        Client.Builder builder = Client.builder();
        if (StringUtils.hasText(apiKey)) {
            builder.apiKey(apiKey);
        } else {
            builder.project(projectId).location(location).vertexAI(true);
        }
        builder.httpOptions(HttpOptions.builder().timeout(timeoutMs).build());
        return builder.build();
    }

    static boolean isTransient(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof CallNotPermittedException) {
                return false;
            }
            if (current instanceof ApiException apiException) {
                int code = apiException.code();
                if (code >= 400 && code < 500 && code != 429) {
                    return false;
                }
            }
            current = current.getCause();
        }
        return true;
    }
}
