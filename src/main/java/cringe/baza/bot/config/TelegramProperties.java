package cringe.baza.bot.config;

import jakarta.validation.constraints.NotEmpty;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.validator.constraints.URL;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "app.telegram")
@Validated
@Getter
@Setter
@EqualsAndHashCode
@NoArgsConstructor
public class TelegramProperties {

    @NotEmpty
    @URL
    private String url;

    @NotEmpty
    private String token;

    @DurationUnit(ChronoUnit.MILLIS)
    private Duration updateListenerSleep = Duration.ofSeconds(1);

    private boolean debug;
}


