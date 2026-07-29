package cringe.baza.bot.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MemeAiQueueConfig {

    public static final String AI_PROCESS_QUEUE = "ai.process";
    public static final String AI_PROCESS_DLQ = "ai.process.dlq";
    public static final String AI_DLX_EXCHANGE = "ai.dlx";

    @Bean
    DirectExchange aiDlxExchange() {
        return new DirectExchange(AI_DLX_EXCHANGE, true, false);
    }

    @Bean
    Queue aiProcessQueue() {
        return QueueBuilder.durable(AI_PROCESS_QUEUE)
                .withArgument("x-dead-letter-exchange", AI_DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", AI_PROCESS_DLQ)
                .build();
    }

    @Bean
    Queue aiProcessDlq() {
        return QueueBuilder.durable(AI_PROCESS_DLQ).build();
    }

    @Bean
    Binding aiProcessBinding(Queue aiProcessQueue, DirectExchange aiDlxExchange) {
        return BindingBuilder.bind(aiProcessQueue).to(aiDlxExchange).with(AI_PROCESS_QUEUE);
    }

    @Bean
    Binding aiProcessDlqBinding(Queue aiProcessDlq, DirectExchange aiDlxExchange) {
        return BindingBuilder.bind(aiProcessDlq).to(aiDlxExchange).with(AI_PROCESS_DLQ);
    }

    @Bean
    MessageConverter jacksonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
