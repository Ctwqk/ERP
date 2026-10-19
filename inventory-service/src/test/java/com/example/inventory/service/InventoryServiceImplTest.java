package com.example.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.inventory.domain.InventoryStock;
import com.example.inventory.domain.InventoryTransaction;
import com.example.inventory.domain.InventoryTransaction.TransactionType;
import com.example.inventory.dto.InventoryStockDto;
import com.example.inventory.repository.InventoryStockRepository;
import com.example.inventory.repository.InventoryTransactionRepository;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class InventoryServiceImplTest {

    private static final UUID ITEM = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();

    @Mock InventoryStockRepository stockRepo;
    @Mock InventoryTransactionRepository txRepo;

    InventoryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new InventoryServiceImpl(stockRepo, txRepo);
    }

    private InventoryStock stockWith(String qty) {
        InventoryStock s = new InventoryStock();
        s.setId(UUID.randomUUID());
        s.setItemId(ITEM);
        s.setQuantity(new BigDecimal(qty));
        return s;
    }

    @Test
    void inboundMovementAddsToExistingStockAndWritesLedgerEntry() {
        InventoryStock stock = stockWith("10");
        when(stockRepo.findByItemIdForUpdate(ITEM)).thenReturn(Optional.of(stock));

        InventoryStockDto result = service.recordTransaction(ITEM, USER, TransactionType.IN, new BigDecimal("2.5"));

        assertThat(result.quantity()).isEqualByComparingTo("12.5");
        ArgumentCaptor<InventoryTransaction> tx = ArgumentCaptor.forClass(InventoryTransaction.class);
        verify(txRepo).save(tx.capture());
        assertThat(tx.getValue().getStockId()).isEqualTo(stock.getId());
        assertThat(tx.getValue().getUserId()).isEqualTo(USER);
        assertThat(tx.getValue().getTransactionType()).isEqualTo(TransactionType.IN);
        assertThat(tx.getValue().getQuantity()).isEqualByComparingTo("2.5");
    }

    @Test
    void firstInboundMovementCreatesTheStockRow() {
        when(stockRepo.findByItemIdForUpdate(ITEM)).thenReturn(Optional.of(stockWith("0")));

        service.recordTransaction(ITEM, USER, TransactionType.IN, BigDecimal.ONE);

        verify(stockRepo).createIfAbsent(any(UUID.class), org.mockito.ArgumentMatchers.eq(ITEM));
    }

    @Test
    void outboundMovementSubtractsStock() {
        when(stockRepo.findByItemIdForUpdate(ITEM)).thenReturn(Optional.of(stockWith("10")));

        InventoryStockDto result = service.recordTransaction(ITEM, USER, TransactionType.OUT, new BigDecimal("10"));

        assertThat(result.quantity()).isEqualByComparingTo("0");
        verify(stockRepo, never()).createIfAbsent(any(), any());
    }

    @Test
    void outboundBeyondAvailableStockIsRejectedAndNothingIsWritten() {
        when(stockRepo.findByItemIdForUpdate(ITEM)).thenReturn(Optional.of(stockWith("3")));

        assertThatThrownBy(() -> service.recordTransaction(ITEM, USER, TransactionType.OUT, new BigDecimal("3.000001")))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(stockRepo, never()).save(any());
        verify(txRepo, never()).save(any());
    }

    @Test
    void outboundForNeverStockedItemIsNotFound() {
        when(stockRepo.findByItemIdForUpdate(ITEM)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.recordTransaction(ITEM, USER, TransactionType.OUT, BigDecimal.ONE))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void nonPositiveQuantityIsRejected() {
        assertThatThrownBy(() -> service.recordTransaction(ITEM, USER, TransactionType.IN, BigDecimal.ZERO))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.recordTransaction(ITEM, USER, TransactionType.OUT, new BigDecimal("-1")))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void getStockForUnknownItemIsNotFound() {
        when(stockRepo.findByItemId(ITEM)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getStockByItemId(ITEM))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
