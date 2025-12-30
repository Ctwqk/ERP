package com.example.document.rabbitmq;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class DocumentEventPublisher {
    private final RabbitTemplate rabbitTemplate;

    public DocumentEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishUploaded(DocumentUploadEvent event) {
        rabbitTemplate.convertAndSend(DocumentMessagingConfig.EXCHANGE, DocumentMessagingConfig.ROUTING_KEY, event);
    }
}
