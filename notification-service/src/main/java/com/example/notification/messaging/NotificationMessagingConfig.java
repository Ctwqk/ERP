package com.example.notification.messaging;

import com.example.common.events.EventTopology;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the exchanges this service consumes from plus its own durable queues. Declarations are idempotent, so
 * start-up order between producers and this service does not matter.
 */
@Configuration
public class NotificationMessagingConfig {

    public static final String TASK_COMPLETED_QUEUE = "notification.task-completed.q";
    public static final String DOCUMENT_UPLOADED_QUEUE = "notification.document-uploaded.q";

    @Bean
    Declarables notificationTopology() {
        TopicExchange taskExchange = new TopicExchange(EventTopology.TASK_EXCHANGE, true, false);
        TopicExchange documentExchange = new TopicExchange(EventTopology.DOCUMENT_EXCHANGE, true, false);
        Queue taskQueue = QueueBuilder.durable(TASK_COMPLETED_QUEUE).build();
        Queue documentQueue = QueueBuilder.durable(DOCUMENT_UPLOADED_QUEUE).build();
        Binding taskBinding = BindingBuilder.bind(taskQueue).to(taskExchange).with(EventTopology.TASK_COMPLETED_KEY);
        Binding documentBinding = BindingBuilder.bind(documentQueue).to(documentExchange)
                .with(EventTopology.DOCUMENT_UPLOADED_KEY);
        return new Declarables(taskExchange, documentExchange, taskQueue, documentQueue, taskBinding,
                documentBinding);
    }
}
