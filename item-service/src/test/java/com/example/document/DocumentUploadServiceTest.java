package com.example.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.common.events.DocumentUploadEvent;
import com.example.document.domain.Document;
import com.example.document.domain.Document.DocType;
import com.example.document.domain.Document.Status;
import com.example.document.domain.DocumentLink;
import com.example.document.domain.DocumentLink.LinkType;
import com.example.document.dto.CompleteUploadRequest;
import com.example.document.dto.InitUploadRequest;
import com.example.document.dto.InitUploadResponse;
import com.example.document.rabbitmq.DocumentEventPublisher;
import com.example.document.repository.DocumentLinkRepository;
import com.example.document.repository.DocumentRepository;
import com.example.document.service.DocumentStorage;
import com.example.document.service.DocumentUploadService;
import com.example.item.repository.ItemRepository;
import com.example.order.domain.Order;
import com.example.order.repository.OrderRepository;
import io.minio.StatObjectResponse;
import java.util.Optional;
import java.util.UUID;
import okhttp3.Headers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class DocumentUploadServiceTest {

    private static final UUID USER = UUID.randomUUID();
    private static final UUID LINK = UUID.randomUUID();

    @Mock DocumentStorage storage;
    @Mock DocumentRepository docs;
    @Mock DocumentLinkRepository links;
    @Mock ItemRepository items;
    @Mock OrderRepository orders;
    @Mock DocumentEventPublisher publisher;

    @InjectMocks DocumentUploadService service;

    @BeforeEach
    void setUp() {
        lenient().when(storage.bucket()).thenReturn("erp-assets");
        lenient().when(docs.save(any(Document.class))).thenAnswer(inv -> {
            Document d = inv.getArgument(0);
            if (d.getId() == null) {
                d.setId(UUID.randomUUID());
            }
            return d;
        });
        lenient().when(storage.presignedPutUrl(any(), any(), anyInt())).thenReturn("http://minio/put-url");
    }

    private static InitUploadRequest request(String linkType, Long size, DocType type) {
        return new InitUploadRequest("drawing.pdf", "application/pdf", size, null, linkType, type);
    }

    private static ResponseStatusException status(Throwable t, HttpStatus expected) {
        assertThat(t).isInstanceOf(ResponseStatusException.class);
        assertThat(((ResponseStatusException) t).getStatusCode()).isEqualTo(expected);
        return (ResponseStatusException) t;
    }

    @Test
    void initRegistersDraftDocumentLinkedToItemAndReturnsPresignedUrl() {
        when(items.existsById(LINK)).thenReturn(true);

        InitUploadResponse response = service.init(LINK, request(null, 1234L, null), USER);

        ArgumentCaptor<Document> doc = ArgumentCaptor.forClass(Document.class);
        verify(docs).save(doc.capture());
        assertThat(doc.getValue().getStatus()).isEqualTo(Status.DRAFT);
        assertThat(doc.getValue().getDocType()).isEqualTo(DocType.ITEM);
        assertThat(doc.getValue().getTitle()).isEqualTo("drawing.pdf");
        assertThat(doc.getValue().getSizeBytes()).isEqualTo(1234L);
        assertThat(doc.getValue().getCreatedByUserId()).isEqualTo(USER);
        assertThat(doc.getValue().getObjectKey()).startsWith("items/" + LINK + "/");
        assertThat(response.uploadUrl()).isEqualTo("http://minio/put-url");
        assertThat(response.documentId()).isEqualTo(doc.getValue().getId());

        ArgumentCaptor<DocumentLink> link = ArgumentCaptor.forClass(DocumentLink.class);
        verify(links).save(link.capture());
        assertThat(link.getValue().getLinkId()).isEqualTo(LINK);
        assertThat(link.getValue().getLinkType()).isEqualTo(LinkType.ITEM);
        verify(storage).ensureBucket();
    }

    @Test
    void initAcceptsFreshOrderBatchAndDefaultsToOrderDocType() {
        service.init(LINK, request("order", 10L, null), USER);

        ArgumentCaptor<Document> doc = ArgumentCaptor.forClass(Document.class);
        verify(docs).save(doc.capture());
        assertThat(doc.getValue().getDocType()).isEqualTo(DocType.ORDER);
        assertThat(doc.getValue().getObjectKey()).startsWith("orders/" + LINK + "/");
    }

    @Test
    void initAcceptsAnExistingOrderOwnedByTheUploader() {
        Order order = new Order();
        order.setId(LINK);
        order.setUserId(USER.toString());
        when(orders.findById(LINK)).thenReturn(Optional.of(order));

        InitUploadResponse response = service.init(LINK, request("ORDER", 10L, null), USER);

        assertThat(response.uploadUrl()).isEqualTo("http://minio/put-url");
        ArgumentCaptor<DocumentLink> link = ArgumentCaptor.forClass(DocumentLink.class);
        verify(links).save(link.capture());
        assertThat(link.getValue().getDocumentId()).isEqualTo(response.documentId());
        assertThat(link.getValue().getLinkId()).isEqualTo(LINK);
        assertThat(link.getValue().getLinkType()).isEqualTo(LinkType.ORDER);
    }

    @Test
    void initRejectsAnotherUsersOrderBeforeStorageOrPersistence() {
        Order order = new Order();
        order.setId(LINK);
        order.setUserId(UUID.randomUUID().toString());
        when(orders.findById(LINK)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.init(LINK, request("ORDER", 10L, null), USER))
                .satisfies(t -> status(t, HttpStatus.FORBIDDEN));

        verifyNoInteractions(storage, docs, links, publisher);
    }

    @Test
    void initRequiresUserIdBeforeStorageOrPersistence() {
        assertThatThrownBy(() -> service.init(LINK, request("ORDER", 10L, null), null))
                .satisfies(t -> status(t, HttpStatus.UNAUTHORIZED));

        verifyNoInteractions(storage, docs, links, items, orders, publisher);
    }

    @Test
    void initRejectsUnknownItemBadSizeAndBadLinkType() {
        when(items.existsById(LINK)).thenReturn(false);
        assertThatThrownBy(() -> service.init(LINK, request("ITEM", 10L, null), USER))
                .satisfies(t -> status(t, HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> service.init(LINK, request("ORDER", 0L, null), USER))
                .satisfies(t -> status(t, HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.init(LINK, request("ORDER", 200L * 1024 * 1024, null), USER))
                .satisfies(t -> status(t, HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.init(LINK, request("PLANET", 10L, null), USER))
                .satisfies(t -> status(t, HttpStatus.BAD_REQUEST));
        verify(docs, never()).save(any());
    }

    private Document draft(long size) {
        Document d = new Document();
        d.setId(UUID.randomUUID());
        d.setDocType(DocType.ORDER);
        d.setCreatedByUserId(USER);
        d.setStatus(Status.DRAFT);
        d.setBucket("erp-assets");
        d.setObjectKey("orders/x/y");
        d.setSizeBytes(size);
        when(docs.findById(d.getId())).thenReturn(Optional.of(d));
        return d;
    }

    private StatObjectResponse stat(long size) {
        return new StatObjectResponse(Headers.of("Content-Length", String.valueOf(size), "ETag", "\"abc\"",
                "Last-Modified", "Mon, 19 Oct 2026 10:00:00 GMT"), "erp-assets", null, "orders/x/y");
    }

    @Test
    void completeApprovesDocumentAndPublishesEventAfterVerifyingStoredSize() {
        Document doc = draft(500);
        when(links.existsByDocumentIdAndLinkId(doc.getId(), LINK)).thenReturn(true);
        when(storage.stat("erp-assets", "orders/x/y")).thenReturn(Optional.of(stat(500)));

        assertThat(service.complete(LINK, new CompleteUploadRequest(doc.getId()), USER, "u@example.com").ok()).isTrue();

        assertThat(doc.getStatus()).isEqualTo(Status.APPROVED);
        assertThat(doc.getEtag()).isNotBlank();
        ArgumentCaptor<DocumentUploadEvent> event = ArgumentCaptor.forClass(DocumentUploadEvent.class);
        verify(publisher).publishUploaded(event.capture());
        assertThat(event.getValue().documentIds()).containsExactly(doc.getId());
        assertThat(event.getValue().documentType()).isEqualTo("ORDER");
        assertThat(event.getValue().linkId()).isEqualTo(LINK);
        assertThat(event.getValue().uploadedByUserId()).isEqualTo(USER);
        assertThat(event.getValue().uploadedBy()).isEqualTo("u@example.com");
    }

    @Test
    void completeFailsWhenNothingWasUploaded() {
        Document doc = draft(500);
        when(links.existsByDocumentIdAndLinkId(doc.getId(), LINK)).thenReturn(true);
        when(storage.stat(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.complete(LINK, new CompleteUploadRequest(doc.getId()), USER, "u"))
                .satisfies(t -> status(t, HttpStatus.CONFLICT));
        assertThat(doc.getStatus()).isEqualTo(Status.DRAFT);
        verify(publisher, never()).publishUploaded(any());
    }

    @Test
    void completeFailsOnSizeMismatch() {
        Document doc = draft(500);
        when(links.existsByDocumentIdAndLinkId(doc.getId(), LINK)).thenReturn(true);
        when(storage.stat(any(), any())).thenReturn(Optional.of(stat(499)));

        assertThatThrownBy(() -> service.complete(LINK, new CompleteUploadRequest(doc.getId()), USER, "u"))
                .satisfies(t -> assertThat(status(t, HttpStatus.BAD_REQUEST).getReason()).contains("Size mismatch"));
        verify(publisher, never()).publishUploaded(any());
    }

    @Test
    void completeRequiresMatchingLinkAndDraftState() {
        Document doc = draft(500);
        when(links.existsByDocumentIdAndLinkId(eq(doc.getId()), any())).thenReturn(false);
        assertThatThrownBy(() -> service.complete(UUID.randomUUID(), new CompleteUploadRequest(doc.getId()), USER, "u"))
                .satisfies(t -> status(t, HttpStatus.FORBIDDEN));

        Document done = draft(500);
        done.setStatus(Status.APPROVED);
        when(links.existsByDocumentIdAndLinkId(done.getId(), LINK)).thenReturn(true);
        assertThatThrownBy(() -> service.complete(LINK, new CompleteUploadRequest(done.getId()), USER, "u"))
                .satisfies(t -> status(t, HttpStatus.CONFLICT));

        UUID missing = UUID.randomUUID();
        when(docs.findById(missing)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.complete(LINK, new CompleteUploadRequest(missing), USER, "u"))
                .satisfies(t -> status(t, HttpStatus.NOT_FOUND));
    }
    @Test
    void anotherUserCannotCompleteUploadOrPublishItsEvent() {
        Document doc = draft(500);
        assertThatThrownBy(() -> service.complete(LINK, new CompleteUploadRequest(doc.getId()),
                UUID.randomUUID(), "another-user"))
                .satisfies(t -> status(t, HttpStatus.FORBIDDEN));
        verify(publisher, never()).publishUploaded(any());
        verify(storage, never()).stat(any(), any());
    }

}
