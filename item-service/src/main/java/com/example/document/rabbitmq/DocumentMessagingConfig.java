package com.example.document.rabbitmq;

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
 * Publishes document events on the {@code document.events} topic exchange and owns the durable queue from which this
 * service imports uploaded order spreadsheets. Other services bind their own queues to the same exchange.
 */
@Configuration
public class DocumentMessagingConfig {

    public static final String ORDER_IMPORT_QUEUE = "item.order-import.q";

    @Bean
    Declarables documentTopology() {
        TopicExchange exchange = new TopicExchange(EventTopology.DOCUMENT_EXCHANGE, true, false);
        Queue importQueue = QueueBuilder.durable(ORDER_IMPORT_QUEUE).build();
        Binding binding = BindingBuilder.bind(importQueue).to(exchange).with(EventTopology.DOCUMENT_UPLOADED_KEY);
        return new Declarables(exchange, importQueue, binding);
    }
}
