package com.example.inventory.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {
    public static final String EXCHANGE = "erp.events";
    public static final String INVENTORY_IN_ROUTING = "inventory.in";
    public static final String INVENTORY_IN_QUEUE = "inventory.in.queue";

    @Bean
    TopicExchange erpExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    Queue inventoryInQueue() {
        return new Queue(INVENTORY_IN_QUEUE, true);
    }

    @Bean
    Binding inventoryInBinding(Queue inventoryInQueue, TopicExchange erpExchange) {
        return BindingBuilder.bind(inventoryInQueue).to(erpExchange).with(INVENTORY_IN_ROUTING);
    }
}

