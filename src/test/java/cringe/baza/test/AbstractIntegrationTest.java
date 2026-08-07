package cringe.baza.test;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public abstract class AbstractIntegrationTest {

    private static final String DELAYED_EXCHANGE_PLUGIN_URL =
            "https://github.com/rabbitmq/rabbitmq-delayed-message-exchange/releases/download/3.13.0/rabbitmq_delayed_message_exchange-3.13.0.ez";

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg17")
            .withDatabaseName("baza_test")
            .withUsername("test")
            .withPassword("test");

    @Container
    static final RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3.13-management")
            .withCommand(
                    "sh",
                    "-c",
                    "curl -sL -o /opt/rabbitmq/plugins/rabbitmq_delayed_message_exchange.ez "
                            + DELAYED_EXCHANGE_PLUGIN_URL
                            + " && rabbitmq-plugins enable --offline rabbitmq_delayed_message_exchange"
                            + " && rabbitmq-server");

    @DynamicPropertySource
    static void containersProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.rabbitmq.host", rabbitmq::getHost);
        registry.add("spring.rabbitmq.port", rabbitmq::getAmqpPort);
        registry.add("spring.rabbitmq.username", rabbitmq::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbitmq::getAdminPassword);
    }
}
