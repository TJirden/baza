package cringe.baza.meme;

import cringe.baza.bot.config.MemeAiQueueConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MemeAiProducer {

    private final RabbitTemplate rabbitTemplate;

    public void enqueueForProcessing(String memeId) {
        rabbitTemplate.convertAndSend(MemeAiQueueConfig.AI_PROCESS_QUEUE, memeId);
        log.info("Мем {} поставлен в очередь на AI-обработку", memeId);
    }
}
