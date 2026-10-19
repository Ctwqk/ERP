package com.example.inventory.service;

import com.example.inventory.domain.InventoryStock;
import com.example.inventory.domain.InventoryTransaction;
import com.example.inventory.domain.InventoryTransaction.TransactionType;
import com.example.inventory.dto.InventoryStockDto;
import com.example.inventory.dto.InventoryTransactionDto;
import com.example.inventory.repository.InventoryStockRepository;
import com.example.inventory.repository.InventoryTransactionRepository;
import java.math.BigDecimal;
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

    public InventoryServiceImpl(InventoryStockRepository stockRepo, InventoryTransactionRepository txRepo) {
        this.stockRepo = stockRepo;
        this.txRepo = txRepo;
    }

    @Override
    @Transactional(readOnly = true)
    public InventoryStockDto getStockByItemId(UUID itemId) {
        return new InventoryStockDto(stockRepo.findByItemId(itemId).orElseThrow(() -> noStock(itemId)));
    }

    @Override
    public InventoryStockDto recordTransaction(UUID itemId, UUID userId, TransactionType type, BigDecimal quantity) {
        if (itemId == null || userId == null || type == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "itemId, userId, type are required");
        }
        if (quantity == null || quantity.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "quantity must be > 0");
        }

        if (type == TransactionType.IN) {
            stockRepo.createIfAbsent(UUID.randomUUID(), itemId);
        }
        InventoryStock stock = stockRepo.findByItemIdForUpdate(itemId).orElseThrow(() -> noStock(itemId));

        stock.setQuantity(computeNewQuantity(stock.getQuantity(), type, quantity));
        stockRepo.save(stock);

        InventoryTransaction tx = new InventoryTransaction();
        tx.setStockId(stock.getId());
        tx.setUserId(userId);
        tx.setTransactionType(type);
        tx.setQuantity(quantity);
        txRepo.save(tx);

        return new InventoryStockDto(stock);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InventoryTransactionDto> listTransactions(UUID stockId) {
        return txRepo.findByStockIdOrderByCreatedAtDesc(stockId)
                .stream()
                .map(InventoryTransactionDto::new)
                .toList();
    }

    private static ResponseStatusException noStock(UUID itemId) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Stock not found for item " + itemId);
    }

    static BigDecimal computeNewQuantity(BigDecimal current, TransactionType type, BigDecimal delta) {
        return switch (type) {
            case IN -> current.add(delta);
            case OUT -> {
                BigDecimal next = current.subtract(delta);
                if (next.signum() < 0) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Insufficient stock: available " + current.stripTrailingZeros().toPlainString()
                                    + ", requested " + delta.stripTrailingZeros().toPlainString());
                }
                yield next;
            }
        };
    }
}
