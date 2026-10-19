package com.example.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.document.domain.Document;
import com.example.document.domain.Document.DocType;
import com.example.document.domain.DocumentLink;
import com.example.document.domain.DocumentLink.LinkType;
import com.example.document.repository.DocumentLinkRepository;
import com.example.document.repository.DocumentRepository;
import com.example.document.service.DocumentStorage;
import com.example.order.domain.Order;
import com.example.order.domain.Order.Status;
import com.example.order.dto.CreateOrderRequest;
import com.example.order.dto.OrderDto;
import com.example.order.dto.OrderItemRequest;
import com.example.order.dto.UpdateOrderStatusRequest;
import com.example.order.integration.InventoryClient;
import com.example.order.integration.ItemLookup;
import com.example.order.integration.ItemLookup.ResolvedItem;
import com.example.order.repository.OrderRepository;
import com.example.order.utils.XlsxFixtures;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    private static final String ME = UUID.randomUUID().toString();

    @Mock OrderRepository orders;
    @Mock DocumentLinkRepository links;
    @Mock DocumentRepository documents;
    @Mock DocumentStorage storage;
    @Mock ItemLookup itemLookup;
    @Mock InventoryClient inventory;
    @Mock CurrentUserProvider currentUser;

    OrderServiceImpl service;
    final UUID bolt = UUID.randomUUID();
    final UUID nut = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new OrderServiceImpl(orders, links, documents, storage, itemLookup, inventory, currentUser, true);
        lenient().when(currentUser.getCurrentUserId()).thenReturn(ME);
        lenient().when(orders.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            if (o.getId() == null) {
                o.setId(UUID.randomUUID());
            }
            return o;
        });
        lenient().when(links.findAllByLinkIdAndLinkType(any(), any())).thenReturn(List.of());
    }

    private static OrderItemRequest line(String sku, int qty, long price) {
        return new OrderItemRequest(null, sku, qty, price);
    }

    private void resolves(ResolvedItem... items) {
        when(itemLookup.resolve(any())).thenReturn(List.of(items));
    }

    private Order stored(Status status, String owner, int boltQty, int nutQty) {
        Order o = new Order();
        o.setId(UUID.randomUUID());
        o.setUserId(owner);
        o.setStatus(status);
        o.setTotalAmountCents(0L);
        for (Object[] l : new Object[][] {{bolt, "BOLT", boltQty}, {nut, "NUT", nutQty}}) {
            com.example.order.domain.OrderItem item = new com.example.order.domain.OrderItem();
            item.setOrder(o);
            item.setItemId((UUID) l[0]);
            item.setSku((String) l[1]);
            item.setQuantity((Integer) l[2]);
            item.setUnitPriceCents(1L);
            item.setLineAmountCents(((Integer) l[2]).longValue());
            o.getItems().add(item);
        }
        when(orders.findById(o.getId())).thenReturn(Optional.of(o));
        return o;
    }

    private static void assertStatus(Throwable t, HttpStatus status) {
        assertThat(t).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(status));
    }

    @Test
    void createComputesLineAndOrderTotalsInCentsAndResolvesSku() {
        resolves(new ResolvedItem(bolt, "BOLT"), new ResolvedItem(nut, "NUT"));

        OrderDto dto = service.createOrder(new CreateOrderRequest(
                List.of(line("BOLT", 3, 250), line("NUT", 10, 15)), null));

        assertThat(dto.status()).isEqualTo(Status.NEW);
        assertThat(dto.userId()).isEqualTo(ME);
        assertThat(dto.items()).extracting("lineAmountCents").containsExactly(750L, 150L);
        assertThat(dto.items()).extracting("itemId").containsExactly(bolt, nut);
        assertThat(dto.totalAmountCents()).isEqualTo(900L);
    }

    @Test
    void createRejectsEmptyOrBadLines() {
        assertThatThrownBy(() -> service.createOrder(new CreateOrderRequest(List.of(), null)))
                .satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));

        resolves(new ResolvedItem(bolt, "BOLT"));
        assertThatThrownBy(() -> service.createOrder(new CreateOrderRequest(List.of(line("BOLT", 0, 5)), null)))
                .satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.createOrder(new CreateOrderRequest(List.of(line("BOLT", 1, -5)), null)))
                .satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.createOrder(
                new CreateOrderRequest(List.of(line("BOLT", Integer.MAX_VALUE, Long.MAX_VALUE)), null)))
                .satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        verify(orders, never()).save(any());
    }

    @Test
    void attachedDocumentsMustExist() {
        resolves(new ResolvedItem(bolt, "BOLT"));
        UUID doc = UUID.randomUUID();
        when(documents.existsById(doc)).thenReturn(false);

        assertThatThrownBy(() -> service.createOrder(new CreateOrderRequest(List.of(line("BOLT", 1, 1)), List.of(doc))))
                .satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
    }

    @ParameterizedTest
    @CsvSource({
            "NEW,CONFIRMED,true", "NEW,CANCELED,true", "NEW,COMPLETED,false", "NEW,NEW,false",
            "CONFIRMED,COMPLETED,true", "CONFIRMED,CANCELED,true", "CONFIRMED,NEW,false",
            "COMPLETED,CANCELED,false", "COMPLETED,NEW,false", "CANCELED,CONFIRMED,false"})
    void statusTransitionTable(Status from, Status to, boolean allowed) {
        assertThat(OrderServiceImpl.isValidTransition(from, to)).isEqualTo(allowed);
    }

    @Test
    void confirmingWithEnoughStockSucceeds() {
        Order order = stored(Status.NEW, ME, 5, 2);
        when(inventory.availableQuantity(bolt)).thenReturn(new BigDecimal("5"));
        when(inventory.availableQuantity(nut)).thenReturn(new BigDecimal("100"));

        assertThat(service.updateStatus(order.getId(), new UpdateOrderStatusRequest(Status.CONFIRMED)).status())
                .isEqualTo(Status.CONFIRMED);
    }

    @Test
    void confirmingWithShortStockIsConflictAndListsEveryShortage() {
        Order order = stored(Status.NEW, ME, 5, 2);
        when(inventory.availableQuantity(bolt)).thenReturn(new BigDecimal("4.5"));
        when(inventory.availableQuantity(nut)).thenReturn(BigDecimal.ZERO);

        assertThatThrownBy(() -> service.updateStatus(order.getId(), new UpdateOrderStatusRequest(Status.CONFIRMED)))
                .satisfies(t -> {
                    assertStatus(t, HttpStatus.CONFLICT);
                    assertThat(((ResponseStatusException) t).getReason())
                            .contains("BOLT (available 4.5, required 5)")
                            .contains("NUT (available 0, required 2)");
                });
        assertThat(order.getStatus()).isEqualTo(Status.NEW);
    }

    @Test
    void stockCheckOnlyHappensWhenConfirming() {
        Order order = stored(Status.NEW, ME, 5, 2);

        service.updateStatus(order.getId(), new UpdateOrderStatusRequest(Status.CANCELED));

        verifyNoInteractions(inventory);
    }

    @Test
    void stockCheckCanBeDisabled() {
        OrderServiceImpl noCheck = new OrderServiceImpl(orders, links, documents, storage, itemLookup, inventory,
                currentUser, false);
        Order order = stored(Status.NEW, ME, 5, 2);

        noCheck.updateStatus(order.getId(), new UpdateOrderStatusRequest(Status.CONFIRMED));

        verifyNoInteractions(inventory);
    }

    @Test
    void demandForTheSameItemIsAggregatedAcrossLines() {
        Order order = stored(Status.NEW, ME, 3, 0);
        com.example.order.domain.OrderItem again = new com.example.order.domain.OrderItem();
        again.setOrder(order);
        again.setItemId(bolt);
        again.setSku("BOLT");
        again.setQuantity(4);
        again.setUnitPriceCents(1L);
        again.setLineAmountCents(4L);
        order.getItems().add(again);
        when(inventory.availableQuantity(bolt)).thenReturn(new BigDecimal("6"));   // 3 + 4 = 7 needed
        when(inventory.availableQuantity(nut)).thenReturn(new BigDecimal("100"));

        assertThatThrownBy(() -> service.updateStatus(order.getId(), new UpdateOrderStatusRequest(Status.CONFIRMED)))
                .satisfies(t -> assertThat(((ResponseStatusException) t).getReason()).contains("required 7"));
    }

    @Test
    void ordersBelongToTheirOwner() {
        Order theirs = stored(Status.NEW, "someone-else", 1, 1);

        assertThatThrownBy(() -> service.getOrder(theirs.getId())).satisfies(t -> assertStatus(t, HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> service.updateStatus(theirs.getId(), new UpdateOrderStatusRequest(Status.CANCELED)))
                .satisfies(t -> assertStatus(t, HttpStatus.FORBIDDEN));
    }

    @Test
    void finishedOrdersCannotChangeAgain() {
        Order done = stored(Status.COMPLETED, ME, 1, 1);

        assertThatThrownBy(() -> service.updateStatus(done.getId(), new UpdateOrderStatusRequest(Status.CANCELED)))
                .satisfies(t -> assertStatus(t, HttpStatus.CONFLICT));
    }

    @Test
    void unknownOrderIsNotFound() {
        UUID id = UUID.randomUUID();
        when(orders.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOrder(id)).satisfies(t -> assertStatus(t, HttpStatus.NOT_FOUND));
    }

    // ----- spreadsheet import --------------------------------------------------------------------------------

    private Document orderDocument(String filename, Document.Status status) {
        Document d = new Document();
        d.setId(UUID.randomUUID());
        d.setDocType(DocType.ORDER);
        d.setCreatedByUserId(UUID.fromString(ME));
        d.setOriginalFilename(filename);
        d.setStatus(status);
        d.setBucket("b");
        d.setObjectKey("k");
        when(documents.findById(d.getId())).thenReturn(Optional.of(d));
        return d;
    }

    @Test
    void importCreatesOrderForTheGivenUserAndLinksTheSourceDocument() {
        Document doc = orderDocument("po.xlsx", Document.Status.APPROVED);
        when(links.findAllByDocumentId(doc.getId())).thenReturn(List.of());
        when(storage.open("b", "k")).thenReturn(new ByteArrayInputStream(
                XlsxFixtures.orderBySku(new Object[] {"BOLT", 4, 100})));
        resolves(new ResolvedItem(bolt, "BOLT"));
        when(orders.findById(any(UUID.class))).thenAnswer(inv -> {
            Order o = new Order();
            o.setId(inv.getArgument(0));
            o.setUserId(ME);
            o.setStatus(Status.NEW);
            o.setTotalAmountCents(400L);
            return Optional.of(o);
        });

        OrderDto dto = service.importOrderFromDocument(doc.getId(), ME);

        assertThat(dto.userId()).isEqualTo(ME);
        org.mockito.ArgumentCaptor<DocumentLink> link = org.mockito.ArgumentCaptor.forClass(DocumentLink.class);
        verify(links).save(link.capture());
        assertThat(link.getValue().getDocumentId()).isEqualTo(doc.getId());
        assertThat(link.getValue().getLinkType()).isEqualTo(LinkType.ORDER);
        assertThat(link.getValue().getPurpose()).isEqualTo(DocumentLink.Purpose.REFERENCE);
    }

    @Test
    void importIsIdempotentForAnAlreadyImportedDocument() {
        Document doc = orderDocument("po.xlsx", Document.Status.APPROVED);
        Order existing = new Order();
        existing.setId(UUID.randomUUID());
        existing.setUserId(ME);
        existing.setStatus(Status.NEW);
        existing.setTotalAmountCents(1L);
        DocumentLink link = new DocumentLink();
        link.setDocumentId(doc.getId());
        link.setLinkId(existing.getId());
        link.setLinkType(LinkType.ORDER);
        when(links.findAllByDocumentId(doc.getId())).thenReturn(List.of(link));
        when(orders.findById(existing.getId())).thenReturn(Optional.of(existing));

        OrderDto dto = service.importOrderFromDocument(doc.getId(), ME);

        assertThat(dto.id()).isEqualTo(existing.getId());
        verifyNoInteractions(storage);
        verify(orders, never()).save(any());
    }

    @Test
    void importRejectsWrongDocuments() {
        Document pdf = orderDocument("po.pdf", Document.Status.APPROVED);
        assertThatThrownBy(() -> service.importOrderFromDocument(pdf.getId(), ME))
                .satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));

        Document draft = orderDocument("po.xlsx", Document.Status.DRAFT);
        assertThatThrownBy(() -> service.importOrderFromDocument(draft.getId(), ME))
                .satisfies(t -> assertStatus(t, HttpStatus.CONFLICT));

        Document item = orderDocument("po.xlsx", Document.Status.APPROVED);
        item.setDocType(DocType.ITEM);
        assertThatThrownBy(() -> service.importOrderFromDocument(item.getId(), ME))
                .satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));

        UUID missing = UUID.randomUUID();
        when(documents.findById(missing)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.importOrderFromDocument(missing, ME))
                .satisfies(t -> assertStatus(t, HttpStatus.NOT_FOUND));
    }

    @Test
    void corruptSpreadsheetIsABadRequest() {
        Document doc = orderDocument("po.xlsx", Document.Status.APPROVED);
        when(links.findAllByDocumentId(doc.getId())).thenReturn(List.of());
        when(storage.open("b", "k")).thenReturn(new ByteArrayInputStream("not a workbook".getBytes()));

        assertThatThrownBy(() -> service.importOrderFromDocument(doc.getId(), ME))
                .satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
    }
    @Test
    void anotherUserCannotImportOrReadAnOrderThroughItsSourceDocument() {
        Document doc = orderDocument("po.xlsx", Document.Status.APPROVED);
        assertThatThrownBy(() -> service.importOrderFromDocument(doc.getId(), UUID.randomUUID().toString()))
                .satisfies(t -> assertStatus(t, HttpStatus.FORBIDDEN));
        verifyNoInteractions(storage);
        verify(orders, never()).save(any());
    }

}
