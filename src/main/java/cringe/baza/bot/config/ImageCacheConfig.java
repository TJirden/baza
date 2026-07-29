package cringe.baza.bot.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnClass(Caffeine.class)
public class ImageCacheConfig {

    @Bean
    Cache<String, byte[]> imageBytesCache(
            @Value("${app.ai.image-cache.ttl-minutes:60}") int ttlMinutes,
            @Value("${app.ai.image-cache.max-size:500}") int maxSize) {
        return Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(ttlMinutes))
                .maximumSize(maxSize)
                .build();
    }
}
