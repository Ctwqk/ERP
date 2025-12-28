package com.example.document.service;

import com.example.order.config.RabbitConfig;
import com.example.order.events.DocumentUploadedEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class DocumentEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public DocumentEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishUploaded(DocumentUploadedEvent event) {
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.DOCUMENT_UPLOADED_ROUTING, event);
    }
}

