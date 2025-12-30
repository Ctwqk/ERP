package com.example.document.rabbitmq;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;

@Configuration
public class DocumentMessagingConfig {
    public static final String EXCHANGE = "document.events";
    public static final String ROUTING_KEY = "document.file.upload";
    public static final String QUEUE = "document.file.upload.q";

    @Bean
    TopicExchange documentExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    Queue documentQueue() {
        return QueueBuilder.durable(QUEUE).build();
    }

    @Bean
    Binding documentBinding() {
        return BindingBuilder.bind(documentQueue()).to(documentExchange()).with(ROUTING_KEY);
    }
}