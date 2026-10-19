package com.example.document.rabbitmq;

import com.example.common.events.DocumentUploadEvent;
import com.example.common.events.EventTopology;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Publishes {@link DocumentUploadEvent}s. Inside a transaction the message is sent only after commit, so consumers
 * never see a document that was rolled back. Delivery is best-effort (no outbox): a broker outage is logged.
 */
@Service
public class DocumentEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(DocumentEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public DocumentEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishUploaded(DocumentUploadEvent event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send(event);
                }
            });
        } else {
            send(event);
        }
    }

    private void send(DocumentUploadEvent event) {
        try {
            rabbitTemplate.convertAndSend(EventTopology.DOCUMENT_EXCHANGE, EventTopology.DOCUMENT_UPLOADED_KEY, event);
            log.info("Published document upload event for {}", event.documentIds());
        } catch (AmqpException ex) {
            log.error("Could not publish document upload event for {}", event.documentIds(), ex);
        }
    }
}
