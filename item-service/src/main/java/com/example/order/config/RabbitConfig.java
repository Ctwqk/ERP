package com.example.order.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {
    public static final String EXCHANGE = "erp.events";
    public static final String DOCUMENT_UPLOADED_ROUTING = "document.uploaded";
    public static final String DOCUMENT_UPLOADED_QUEUE = "order.document.uploaded.queue";

    @Bean
    TopicExchange erpExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    Queue documentUploadedQueue() {
        return new Queue(DOCUMENT_UPLOADED_QUEUE, true);
    }

    @Bean
    Binding documentUploadedBinding(Queue documentUploadedQueue, TopicExchange erpExchange) {
        return BindingBuilder.bind(documentUploadedQueue).to(erpExchange).with(DOCUMENT_UPLOADED_ROUTING);
    }
}

