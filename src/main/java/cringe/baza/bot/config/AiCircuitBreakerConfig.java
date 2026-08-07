package cringe.baza.bot.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnClass({CircuitBreaker.class, MeterRegistry.class})
@RequiredArgsConstructor
public class AiCircuitBreakerConfig {

    public static final String AI_CIRCUIT_BREAKER = "aiAnalysis";

    private final MeterRegistry meterRegistry;

    @Bean
    CircuitBreakerRegistry circuitBreakerRegistry(
            @Value("${app.ai.circuit-breaker.failure-rate-threshold:50}") float failureRateThreshold,
            @Value("${app.ai.circuit-breaker.slow-call-rate-threshold:80}") float slowCallRateThreshold,
            @Value("${app.ai.circuit-breaker.slow-call-duration-threshold:10s}") Duration slowCallDurationThreshold,
            @Value("${app.ai.circuit-breaker.wait-duration-in-open-state:30s}") Duration waitDurationInOpenState,
            @Value("${app.ai.circuit-breaker.sliding-window-size:20}") int slidingWindowSize,
            @Value("${app.ai.circuit-breaker.minimum-number-of-calls:10}") int minimumNumberOfCalls) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(failureRateThreshold)
                .slowCallRateThreshold(slowCallRateThreshold)
                .slowCallDurationThreshold(slowCallDurationThreshold)
                .waitDurationInOpenState(waitDurationInOpenState)
                .slidingWindowSize(slidingWindowSize)
                .minimumNumberOfCalls(minimumNumberOfCalls)
                .permittedNumberOfCallsInHalfOpenState(3)
                .build();
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
        registry.circuitBreaker(AI_CIRCUIT_BREAKER, config);
        TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(registry).bindTo(meterRegistry);
        return registry;
    }

    @Bean(AI_CIRCUIT_BREAKER)
    CircuitBreaker aiCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker(AI_CIRCUIT_BREAKER);
    }
}
