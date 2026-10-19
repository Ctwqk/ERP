package com.example.order.service;

import com.example.document.domain.Document;
import com.example.document.domain.Document.DocType;
import com.example.document.domain.DocumentLink;
import com.example.document.domain.DocumentLink.LinkType;
import com.example.document.domain.DocumentLink.Purpose;
import com.example.document.repository.DocumentLinkRepository;
import com.example.document.repository.DocumentRepository;
import com.example.document.service.DocumentStorage;
import com.example.order.domain.Order;
import com.example.order.domain.Order.Status;
import com.example.order.domain.OrderItem;
import com.example.order.dto.CreateOrderRequest;
import com.example.order.dto.ItemReference;
import com.example.order.dto.OrderDto;
import com.example.order.dto.OrderItemRequest;
import com.example.order.dto.UpdateOrderStatusRequest;
import com.example.order.integration.InventoryClient;
import com.example.order.integration.ItemLookup;
import com.example.order.integration.ItemLookup.ResolvedItem;
import com.example.order.repository.OrderRepository;
import com.example.order.utils.ReadXlsx;
import com.example.order.utils.ReadXlsx.ExcelOrderData;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class OrderServiceImpl implements OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);

    private final OrderRepository orderRepository;
    private final DocumentLinkRepository documentLinkRepository;
    private final DocumentRepository documentRepository;
    private final DocumentStorage documentStorage;
    private final ItemLookup itemLookup;
    private final InventoryClient inventoryClient;
    private final CurrentUserProvider currentUserProvider;
    private final boolean checkStockOnConfirm;

    public OrderServiceImpl(OrderRepository orderRepository,
            DocumentLinkRepository documentLinkRepository,
            DocumentRepository documentRepository,
            DocumentStorage documentStorage,
            ItemLookup itemLookup,
            InventoryClient inventoryClient,
            CurrentUserProvider currentUserProvider,
            @Value("${erp.orders.check-stock-on-confirm:true}") boolean checkStockOnConfirm) {
        this.orderRepository = orderRepository;
        this.documentLinkRepository = documentLinkRepository;
        this.documentRepository = documentRepository;
        this.documentStorage = documentStorage;
        this.itemLookup = itemLookup;
        this.inventoryClient = inventoryClient;
        this.currentUserProvider = currentUserProvider;
        this.checkStockOnConfirm = checkStockOnConfirm;
    }

    @Override
    public OrderDto createOrder(CreateOrderRequest request) {
        return createOrderFor(currentUserProvider.getCurrentUserId(), request);
    }

    private OrderDto createOrderFor(String userId, CreateOrderRequest request) {
        if (request == null || request.items() == null || request.items().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order must contain items");
        }
        List<ResolvedItem> resolved = itemLookup.resolve(request.items().stream()
                .map(i -> i == null ? null : new ItemReference(i.itemId(), i.sku()))
                .collect(Collectors.toList()));

        Order order = new Order();
        order.setUserId(userId);
        order.setStatus(Status.NEW);
        List<OrderItem> items = buildOrderItems(order, request.items(), resolved);
        order.setItems(items);
        order.setTotalAmountCents(calculateTotal(items));
        Order saved = orderRepository.save(order);

        linkDocuments(saved.getId(), request.documentIds());
        return new OrderDto(saved, loadDocumentIds(saved.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public OrderDto getOrder(UUID id) {
        Order order = find(id);
        assertOwnership(order);
        return new OrderDto(order, loadDocumentIds(order.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderDto> listMyOrders(Pageable pageable) {
        String userId = currentUserProvider.getCurrentUserId();
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(order -> new OrderDto(order, loadDocumentIds(order.getId())));
    }

    @Override
    public OrderDto updateStatus(UUID id, UpdateOrderStatusRequest request) {
        if (request == null || request.status() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status is required");
        }
        Order order = find(id);
        assertOwnership(order);
        if (!isValidTransition(order.getStatus(), request.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Invalid status transition " + order.getStatus() + " -> " + request.status());
        }
        if (request.status() == Status.CONFIRMED && checkStockOnConfirm) {
            assertStockAvailable(order);
        }
        order.setStatus(request.status());
        Order saved = orderRepository.save(order);
        return new OrderDto(saved, loadDocumentIds(saved.getId()));
    }

    /** Sums the demand per item and refuses to confirm when inventory-service reports less on hand. */
    private void assertStockAvailable(Order order) {
        Map<UUID, Long> demand = new LinkedHashMap<>();
        Map<UUID, String> skus = new LinkedHashMap<>();
        for (OrderItem line : order.getItems()) {
            demand.merge(line.getItemId(), line.getQuantity().longValue(), Long::sum);
            skus.putIfAbsent(line.getItemId(), line.getSku());
        }
        List<String> shortages = new ArrayList<>();
        demand.forEach((itemId, required) -> {
            BigDecimal available = inventoryClient.availableQuantity(itemId);
            if (available.compareTo(BigDecimal.valueOf(required)) < 0) {
                shortages.add("%s (available %s, required %d)".formatted(skus.get(itemId),
                        available.stripTrailingZeros().toPlainString(), required));
            }
        });
        if (!shortages.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Insufficient stock to confirm order: " + String.join(", ", shortages));
        }
    }

    private List<OrderItem> buildOrderItems(Order order, List<OrderItemRequest> requests,
            List<ResolvedItem> resolved) {
        List<OrderItem> items = new ArrayList<>();
        for (int i = 0; i < requests.size(); i++) {
            OrderItemRequest req = requests.get(i);
            if (req.quantity() == null || req.quantity() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantity must be positive");
            }
            if (req.unitPriceCents() == null || req.unitPriceCents() < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unitPriceCents must be non-negative");
            }
            OrderItem item = new OrderItem();
            item.setOrder(order);
            item.setItemId(resolved.get(i).itemId());
            item.setSku(resolved.get(i).sku());
            item.setQuantity(req.quantity());
            item.setUnitPriceCents(req.unitPriceCents());
            item.setLineAmountCents(multiply(req.quantity().longValue(), req.unitPriceCents()));
            items.add(item);
        }
        return items;
    }

    private static long multiply(long quantity, long unitPriceCents) {
        try {
            return Math.multiplyExact(quantity, unitPriceCents);
        } catch (ArithmeticException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Line amount is too large");
        }
    }

    private Long calculateTotal(List<OrderItem> items) {
        long total = 0;
        try {
            for (OrderItem item : items) {
                total = Math.addExact(total, item.getLineAmountCents());
            }
        } catch (ArithmeticException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order total is too large");
        }
        return total;
    }

    private void linkDocuments(UUID orderId, List<UUID> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            return;
        }
        for (UUID docId : documentIds) {
            if (!documentRepository.existsById(docId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Document not found: " + docId);
            }
            link(orderId, docId, Purpose.PRIMARY);
        }
    }

    private void link(UUID orderId, UUID documentId, Purpose purpose) {
        DocumentLink link = new DocumentLink();
        link.setLinkId(orderId);
        link.setDocumentId(documentId);
        link.setLinkType(LinkType.ORDER);
        link.setPurpose(purpose);
        documentLinkRepository.save(link);
    }

    private List<UUID> loadDocumentIds(UUID orderId) {
        return documentLinkRepository.findAllByLinkIdAndLinkType(orderId, LinkType.ORDER).stream()
                .map(DocumentLink::getDocumentId)
                .toList();
    }

    private Order find(UUID id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    }

    private void assertOwnership(Order order) {
        String currentUser = currentUserProvider.getCurrentUserId();
        if (!currentUser.equals(order.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your order");
        }
    }

    static boolean isValidTransition(Status current, Status target) {
        return switch (current) {
            case NEW -> target == Status.CONFIRMED || target == Status.CANCELED;
            case CONFIRMED -> target == Status.COMPLETED || target == Status.CANCELED;
            case CANCELED, COMPLETED -> false;
        };
    }

    @Override
    public OrderDto createOrderFromXlsx(UUID documentId) {
        return importOrderFromDocument(documentId, currentUserProvider.getCurrentUserId());
    }

    @Override
    public OrderDto importOrderFromDocument(UUID documentId, String userId) {
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
        if (!userId.equals(String.valueOf(doc.getCreatedByUserId()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the uploader can import this document");
        }
        if (doc.getDocType() != DocType.ORDER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Document is not an ORDER document");
        }
        if (doc.getOriginalFilename() == null || !doc.getOriginalFilename().toLowerCase().endsWith(".xlsx")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only .xlsx documents can be imported");
        }
        if (doc.getStatus() != Document.Status.APPROVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Document upload is not complete");
        }
        // idempotent: a document that was already turned into an order (e.g. message redelivery) is not imported twice
        Optional<Order> existing = documentLinkRepository.findAllByDocumentId(documentId).stream()
                .filter(l -> l.getLinkType() == LinkType.ORDER)
                .map(l -> orderRepository.findById(l.getLinkId()))
                .flatMap(Optional::stream)
                .findFirst();
        if (existing.isPresent()) {
            if (!userId.equals(existing.get().getUserId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your order");
            }
            log.info("Document {} was already imported as order {}", documentId, existing.get().getId());
            return new OrderDto(existing.get(), loadDocumentIds(existing.get().getId()));
        }

        ExcelOrderData data;
        try (InputStream in = documentStorage.open(doc.getBucket(), doc.getObjectKey())) {
            data = ReadXlsx.readOrderWithMeta(in);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Failed to import order from xlsx: " + e.getMessage(), e);
        }
        OrderDto created = createOrderFor(userId, data.orderRequest());
        link(created.id(), documentId, Purpose.REFERENCE);
        return new OrderDto(find(created.id()), loadDocumentIds(created.id()));
    }
}
