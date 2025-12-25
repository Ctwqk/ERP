package com.example.order.service;

import com.example.document.domain.DocumentLink;
import com.example.document.domain.DocumentLink.LinkType;
import com.example.document.domain.DocumentLink.Purpose;
import com.example.document.repository.DocumentLinkRepository;
import com.example.order.domain.Order;
import com.example.order.domain.Order.Status;
import com.example.order.domain.OrderItem;
import com.example.order.dto.CreateOrderRequest;
import com.example.order.dto.OrderDto;
import com.example.order.dto.OrderItemRequest;
import com.example.order.dto.UpdateOrderStatusRequest;
import com.example.order.repository.OrderRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final DocumentLinkRepository documentLinkRepository;
    private final ItemLookupClient itemLookupClient;
    private final CurrentUserProvider currentUserProvider;

    public OrderServiceImpl(OrderRepository orderRepository,
            DocumentLinkRepository documentLinkRepository,
            ItemLookupClient itemLookupClient,
            CurrentUserProvider currentUserProvider) {
        this.orderRepository = orderRepository;
        this.documentLinkRepository = documentLinkRepository;
        this.itemLookupClient = itemLookupClient;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    public OrderDto createOrder(CreateOrderRequest request) {
        validateRequest(request);

        String userId = currentUserProvider.getCurrentUserId();
        Order order = new Order();
        order.setUserId(userId);
        order.setStatus(Status.NEW);

        List<OrderItem> items = buildOrderItems(order, request.items());
        order.setItems(items);
        order.setTotalAmountCents(calculateTotal(items));

        Order saved = orderRepository.save(order);

        linkDocuments(saved.getId(), request.documentIds());

        List<UUID> documentIds = loadDocumentIds(saved.getId());
        return new OrderDto(saved, documentIds);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderDto getOrder(UUID id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
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
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        assertOwnership(order);
        if (!isValidTransition(order.getStatus(), request.status())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status transition");
        }
        order.setStatus(request.status());
        Order saved = orderRepository.save(order);
        return new OrderDto(saved, loadDocumentIds(saved.getId()));
    }

    private void validateRequest(CreateOrderRequest request) {
        if (request == null || request.items() == null || request.items().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order must contain items");
        }
        itemLookupClient.assertItemsExist(request.items().stream()
                .map(i -> new ItemReference(i.itemId(), i.sku()))
                .collect(Collectors.toList()));
    }

    private List<OrderItem> buildOrderItems(Order order, List<OrderItemRequest> requests) {
        List<OrderItem> items = new ArrayList<>();
        for (OrderItemRequest req : requests) {
            if (req == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order item is required");
            }
            if (!req.hasItemReference()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Item id or sku is required");
            }
            if (req.quantity() == null || req.quantity() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantity must be positive");
            }
            if (req.unitPriceCents() == null || req.unitPriceCents() < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unitPriceCents must be non-negative");
            }

            OrderItem item = new OrderItem();
            item.setOrder(order);
            item.setItemId(req.itemId());
            item.setSku(req.sku());
            item.setQuantity(req.quantity());
            item.setUnitPriceCents(req.unitPriceCents());
            long lineAmount = Math.multiplyExact(req.quantity().longValue(), req.unitPriceCents());
            item.setLineAmountCents(lineAmount);
            items.add(item);
        }
        return items;
    }

    private Long calculateTotal(List<OrderItem> items) {
        long total = 0;
        for (OrderItem item : items) {
            total = Math.addExact(total, item.getLineAmountCents());
        }
        return total;
    }

    private void linkDocuments(UUID orderId, List<UUID> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            return;
        }
        for (UUID docId : documentIds) {
            DocumentLink link = new DocumentLink();
            link.setLinkId(orderId);
            link.setDocumentId(docId);
            link.setLinkType(LinkType.ORDER);
            link.setPurpose(Purpose.PRIMARY);
            documentLinkRepository.save(link);
        }
    }

    private List<UUID> loadDocumentIds(UUID orderId) {
        return documentLinkRepository.findAllByLinkIdAndLinkType(orderId, LinkType.ORDER).stream()
                .map(DocumentLink::getDocumentId)
                .toList();
    }

    private void assertOwnership(Order order) {
        String currentUser = currentUserProvider.getCurrentUserId();
        if (!currentUser.equals(order.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your order");
        }
    }

    private boolean isValidTransition(Status current, Status target) {
        return switch (current) {
            case NEW -> target == Status.CONFIRMED || target == Status.CANCELED;
            case CONFIRMED -> target == Status.COMPLETED || target == Status.CANCELED;
            case CANCELED, COMPLETED -> false;
        };
    }
}


