package com.example.order.rabbitmq;

import com.example.common.events.DocumentUploadEvent;
import com.example.document.rabbitmq.DocumentMessagingConfig;
import com.example.order.service.OrderService;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Imports an uploaded order spreadsheet as soon as its upload completes. Reacts to {@code document.file.upload} events
 * of type {@code ORDER}; everything else on the exchange is ignored. The order is created for the uploader.
 */
@Component
public class OrderImportListener {

    private static final Logger log = LoggerFactory.getLogger(OrderImportListener.class);

    private final OrderService orderService;

    public OrderImportListener(OrderService orderService) {
        this.orderService = orderService;
    }

    @RabbitListener(queues = DocumentMessagingConfig.ORDER_IMPORT_QUEUE)
    public void onDocumentUploaded(DocumentUploadEvent evt) {
        if (!"ORDER".equalsIgnoreCase(evt.documentType())) {
            return;
        }
        if (evt.documentIds() == null || evt.documentIds().isEmpty() || evt.uploadedByUserId() == null) {
            log.warn("Ignoring ORDER upload event without documents or uploader: {}", evt);
            return;
        }
        for (UUID documentId : evt.documentIds()) {
            try {
                var order = orderService.importOrderFromDocument(documentId, evt.uploadedByUserId().toString());
                log.info("Imported document {} as order {}", documentId, order.id());
            } catch (ResponseStatusException e) {
                if (e.getStatusCode().is5xxServerError()) {
                    throw e; // transient (e.g. storage down): let the listener retry
                }
                // permanent problem (not a spreadsheet, bad rows, unknown SKU): retrying would not help
                log.warn("Document {} was not imported as an order: {}", documentId, e.getReason());
            }
        }
    }
}
