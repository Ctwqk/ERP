package com.example.task.messaging;

import com.example.common.events.EventTopology;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TaskMessagingConfig {

    /** Declared on start-up so publishing works even before any consumer has created its queue. */
    @Bean
    TopicExchange taskExchange() {
        return new TopicExchange(EventTopology.TASK_EXCHANGE, true, false);
    }
}
