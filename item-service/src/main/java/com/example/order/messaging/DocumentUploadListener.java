package com.example.order.messaging;

import com.example.document.domain.DocumentLink;
import com.example.document.domain.DocumentLink.LinkType;
import com.example.document.domain.DocumentLink.Purpose;
import com.example.document.repository.DocumentLinkRepository;
import com.example.order.config.RabbitConfig;
import com.example.order.events.DocumentUploadedEvent;
import java.util.UUID;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DocumentUploadListener {

    private final DocumentLinkRepository documentLinkRepository;

    public DocumentUploadListener(DocumentLinkRepository documentLinkRepository) {
        this.documentLinkRepository = documentLinkRepository;
    }

    @RabbitListener(queues = RabbitConfig.DOCUMENT_UPLOADED_QUEUE)
    @Transactional
    public void onDocumentUploaded(DocumentUploadedEvent event) {
        if (event == null || event.documentId() == null || event.linkType() == null || event.linkId() == null) {
            return;
        }
        if (!"ORDER".equalsIgnoreCase(event.linkType())) {
            return;
        }
        UUID docId = UUID.fromString(event.documentId());
        UUID linkId = UUID.fromString(event.linkId());
        if (documentLinkRepository.existsByDocumentIdAndLinkId(docId, linkId)) {
            return;
        }
        DocumentLink link = new DocumentLink();
        link.setDocumentId(docId);
        link.setLinkId(linkId);
        link.setLinkType(LinkType.ORDER);
        link.setPurpose(Purpose.PRIMARY);
        documentLinkRepository.save(link);
    }
}

