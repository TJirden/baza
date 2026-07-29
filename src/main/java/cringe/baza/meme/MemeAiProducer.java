package cringe.baza.meme;

import cringe.baza.bot.config.MemeAiQueueConfig;
import cringe.baza.model.IdRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MemeAiProducer {

    private final RabbitTemplate rabbitTemplate;
    private final IdRepository idRepository;

    public void enqueueForProcessing(String memeId) {
        rabbitTemplate.convertAndSend(MemeAiQueueConfig.AI_PROCESS_QUEUE, memeId);
        idRepository.markEnqueued(memeId);
        log.info("Мем {} поставлен в очередь на AI-обработку", memeId);
    }
}
