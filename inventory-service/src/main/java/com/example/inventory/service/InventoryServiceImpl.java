package com.example.inventory.service;

import com.example.inventory.domain.InventoryStock;
import com.example.inventory.domain.InventoryTransaction;
import com.example.inventory.domain.InventoryTransaction.TransactionType;
import com.example.inventory.dto.InventoryStockDto;
import com.example.inventory.dto.InventoryTransactionDto;
import com.example.inventory.repository.InventoryStockRepository;
import com.example.inventory.repository.InventoryTransactionRepository;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import com.example.inventory.config.RabbitConfig;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class InventoryServiceImpl implements InventoryService {

    private final InventoryStockRepository stockRepo;
    private final InventoryTransactionRepository txRepo;
    private final RabbitTemplate rabbitTemplate;
    private final TopicExchange exchange;

    public InventoryServiceImpl(InventoryStockRepository stockRepo, InventoryTransactionRepository txRepo,
            RabbitTemplate rabbitTemplate, TopicExchange exchange) {
        this.stockRepo = stockRepo;
        this.txRepo = txRepo;
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
    }

    @Override
    @Transactional(readOnly = true)
    public InventoryStockDto getStockByItemId(UUID itemId) {
        InventoryStock stock = stockRepo.findByItemId(itemId)
                .orElseGet(() -> handleEmptyStock(itemId));
        return new InventoryStockDto(stock);
    }

    @Override
    public InventoryStockDto recordTransaction(UUID itemId, UUID userId, TransactionType type, double quantity) {
        if (itemId == null || userId == null || type == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "itemId, userId, type are required");
        }
        if (quantity <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "quantity must be > 0");
        }

        InventoryStock stock = stockRepo.findByItemId(itemId)
                .orElseGet(() -> handleEmptyStock(itemId));

        double newQty = computeNewQuantity(stock.getQuantity(), type, quantity);
        stock.setQuantity(newQty);
        stockRepo.save(stock);

        InventoryTransaction tx = new InventoryTransaction();
        tx.setStockId(stock.getId());
        tx.setUserId(userId);
        tx.setTransactionType(type);
        tx.setQuantity(quantity);
        txRepo.save(tx);

        if (type == TransactionType.IN) {
            publishInventoryIn(itemId, quantity);
        }

        return new InventoryStockDto(stock);
    }

    private void publishInventoryIn(UUID itemId, double quantity) {
        Map<String, Object> event = Map.of(
                "event", "inventory.in",
                "itemId", itemId.toString(),
                "quantity", quantity
        );
        rabbitTemplate.convertAndSend(exchange.getName(), RabbitConfig.INVENTORY_IN_ROUTING, event);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InventoryTransactionDto> listTransactions(UUID stockId) {
        return txRepo.findByStockIdOrderByCreatedAtDesc(stockId)
                .stream()
                .map(InventoryTransactionDto::new)
                .toList();
    }

    private InventoryStock handleEmptyStock(UUID itemId) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Stock not found for item " + itemId);
    }

    private double computeNewQuantity(double current, TransactionType type, double delta) {
        return switch (type) {
            case IN -> current + delta;
            case OUT -> {
                double next = current - delta;
                if (next < 0) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Insufficient stock");
                }
                yield next;
            }
        };
    }
}
