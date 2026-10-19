package com.example.inventory.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.inventory.PostgresTestConfig;
import com.example.inventory.repository.InventoryStockRepository;
import com.example.inventory.repository.InventoryTransactionRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;

/** Full-stack test against a real PostgreSQL: Flyway schema, Hibernate validation, locking and the REST contract. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfig.class)
class InventoryApiIT {

    @Autowired MockMvc mvc;
    @Autowired InventoryStockRepository stocks;
    @Autowired InventoryTransactionRepository transactions;

    private static JwtRequestPostProcessor user() {
        return jwt().jwt(j -> j.subject("clerk@example.com").claim("uid", UUID.randomUUID().toString()));
    }

    private void move(UUID item, String type, String qty) throws Exception {
        mvc.perform(post("/api/inventory/stocks/{id}/transactions", item).with(user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"%s\",\"quantity\":%s}".formatted(type, qty)))
                .andExpect(status().isCreated());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/inventory/stocks/{id}", UUID.randomUUID())).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void stockLifecycleOverRest() throws Exception {
        UUID item = UUID.randomUUID();

        mvc.perform(get("/api/inventory/stocks/{id}", item).with(user())).andExpect(status().isNotFound());

        move(item, "IN", "100");
        move(item, "OUT", "30.5");

        mvc.perform(get("/api/inventory/stocks/{id}", item).with(user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemId").value(item.toString()))
                .andExpect(jsonPath("$.quantity").value(69.5));

        mvc.perform(post("/api/inventory/stocks/{id}/transactions", item).with(user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"OUT\",\"quantity\":70}"))
                .andExpect(status().isConflict());

        UUID stockId = stocks.findByItemId(item).orElseThrow().getId();
        mvc.perform(get("/api/inventory/stocks/{id}/transactions", stockId).with(user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].transactionType").value("OUT"))
                .andExpect(jsonPath("$[1].transactionType").value("IN"));
    }

    @Test
    void invalidRequestsAreRejected() throws Exception {
        UUID item = UUID.randomUUID();
        mvc.perform(post("/api/inventory/stocks/{id}/transactions", item).with(user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"IN\",\"quantity\":0}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/inventory/stocks/{id}/transactions", item).with(user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"SIDEWAYS\",\"quantity\":1}"))
                .andExpect(status().isBadRequest());
        // token without a uid claim cannot be attributed to a user
        mvc.perform(post("/api/inventory/stocks/{id}/transactions", item).with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"IN\",\"quantity\":1}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void concurrentMovementsNeverLoseUpdatesOrOverdraw() throws Exception {
        UUID item = UUID.randomUUID();
        move(item, "IN", "20");

        int threads = 12;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return mvc.perform(post("/api/inventory/stocks/{id}/transactions", item).with(user())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"type\":\"OUT\",\"quantity\":3}"))
                        .andReturn().getResponse().getStatus();
            }));
        }
        start.countDown();
        int ok = 0;
        int rejected = 0;
        for (Future<Integer> f : results) {
            int s = f.get();
            if (s == 201) {
                ok++;
            } else if (s == 409) {
                rejected++;
            }
        }
        pool.shutdown();

        // 20 units / 3 per movement => exactly 6 succeed, the rest are refused, stock ends at 2
        assertThat(ok).isEqualTo(6);
        assertThat(rejected).isEqualTo(threads - 6);
        assertThat(stocks.findByItemId(item).orElseThrow().getQuantity()).isEqualByComparingTo(new BigDecimal("2"));
    }

    @Test
    void concurrentFirstInboundMovementsCreateExactlyOneStockRow() throws Exception {
        UUID item = UUID.randomUUID();
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return mvc.perform(post("/api/inventory/stocks/{id}/transactions", item).with(user())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"type\":\"IN\",\"quantity\":1}"))
                        .andReturn().getResponse().getStatus();
            }));
        }
        start.countDown();
        for (Future<Integer> f : results) {
            assertThat(f.get()).isEqualTo(201);
        }
        pool.shutdown();
        assertThat(stocks.findAll().stream().filter(s -> s.getItemId().equals(item))).hasSize(1);
        assertThat(stocks.findByItemId(item).orElseThrow().getQuantity()).isEqualByComparingTo(new BigDecimal("8"));
    }
}
