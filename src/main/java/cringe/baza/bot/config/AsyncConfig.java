package cringe.baza.bot.config;

import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "memeAsyncExecutor")
    // TODO: SimpleAsyncTaskExecutor с concurrencyLimit блокирует вызывающий поток при достижении лимита
    //       (через ConcurrencyThrottleInterceptor), что может подвешивать обработку Telegram-апдейтов
    //       при всплеске загрузок. Заменить на ThreadPoolTaskExecutor с очередью и политикой отклонения.
    public Executor memeAsyncExecutor(@Value("${app.meme.async-concurrency}") int concurrencyLimit) {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("meme-vt-");
        executor.setVirtualThreads(true);
        executor.setConcurrencyLimit(concurrencyLimit);
        return executor;
    }
}
