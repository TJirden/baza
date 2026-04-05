package cringe.baza.bot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class BotApplication {

    static void main(String[] args) {
        SpringApplication.run(BotApplication.class, args);
    }
}

