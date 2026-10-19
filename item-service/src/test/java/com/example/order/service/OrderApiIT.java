package com.example.order.service;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.item.ContainersConfig;
import com.example.item.ItemServiceApplication;
import com.example.item.ContainersConfig.FakeInventory;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;

/** Order REST API on PostgreSQL with a controllable inventory. */
@SpringBootTest(classes = ItemServiceApplication.class)
@AutoConfigureMockMvc
@Import(ContainersConfig.class)
class OrderApiIT {

    @Autowired MockMvc mvc;
    @Autowired FakeInventory inventory;

    private static JwtRequestPostProcessor as(UUID uid) {
        return jwt().jwt(j -> j.subject(uid + "@example.com").claim("uid", uid.toString()));
    }

    private String createItem(UUID caller, String sku) throws Exception {
        String uoms = mvc.perform(get("/api/uoms").with(as(caller))).andReturn().getResponse().getContentAsString();
        List<String> ea = JsonPath.read(uoms, "$[?(@.code=='EA')].id");
        String body = mvc.perform(post("/api/items").with(as(caller)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"skuCode\":\"%s\",\"name\":\"%s\",\"itemType\":\"FG\",\"baseUomId\":\"%s\"}"
                                .formatted(sku, sku, ea.get(0))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private String createOrder(UUID caller, String sku, int qty, long price) throws Exception {
        String body = mvc.perform(post("/api/orders").with(as(caller)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"sku\":\"%s\",\"quantity\":%d,\"unitPriceCents\":%d}]}"
                                .formatted(sku, qty, price)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/orders")).andExpect(status().isUnauthorized());
    }

    @Test
    void createResolvesSkuToItemAndComputesTotals() throws Exception {
        UUID alice = UUID.randomUUID();
        String itemId = createItem(alice, "ORD-A");

        mvc.perform(post("/api/orders").with(as(alice)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"sku\":\"ORD-A\",\"quantity\":3,\"unitPriceCents\":250},"
                                + "{\"itemId\":\"%s\",\"quantity\":2,\"unitPriceCents\":100}]}".formatted(itemId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.userId").value(alice.toString()))
                .andExpect(jsonPath("$.items[0].itemId").value(itemId))
                .andExpect(jsonPath("$.items[1].sku").value("ORD-A"))
                .andExpect(jsonPath("$.totalAmountCents").value(950));
    }

    @Test
    void unknownSkuAndInvalidLinesAreBadRequests() throws Exception {
        UUID alice = UUID.randomUUID();
        mvc.perform(post("/api/orders").with(as(alice)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"sku\":\"NOPE\",\"quantity\":1,\"unitPriceCents\":1}]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/orders").with(as(alice)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/orders").with(as(alice)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"sku\":\"X\",\"quantity\":0,\"unitPriceCents\":1}]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void inactiveItemsCannotBeOrdered() throws Exception {
        UUID alice = UUID.randomUUID();
        String itemId = createItem(alice, "ORD-OLD");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/items/{id}", itemId)
                        .with(as(alice)).contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/orders").with(as(alice)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"sku\":\"ORD-OLD\",\"quantity\":1,\"unitPriceCents\":1}]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ordersArePrivateToTheirOwner() throws Exception {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        createItem(alice, "ORD-P");
        String id = createOrder(alice, "ORD-P", 1, 10);

        mvc.perform(get("/api/orders/{id}", id).with(as(alice))).andExpect(status().isOk());
        mvc.perform(get("/api/orders/{id}", id).with(as(bob))).andExpect(status().isForbidden());
        mvc.perform(patch("/api/orders/{id}/status", id).with(as(bob)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELED\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/orders").with(as(alice))).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/orders").with(as(bob))).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/orders/{id}", UUID.randomUUID()).with(as(alice))).andExpect(status().isNotFound());
    }

    @Test
    void confirmingChecksInventoryAndLifecycleIsEnforced() throws Exception {
        UUID alice = UUID.randomUUID();
        String itemId = createItem(alice, "ORD-S");
        String id = createOrder(alice, "ORD-S", 5, 100);

        // nothing in stock -> 409 and the order stays NEW
        mvc.perform(patch("/api/orders/{id}/status", id).with(as(alice)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CONFIRMED\"}"))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/orders/{id}", id).with(as(alice))).andExpect(jsonPath("$.status").value("NEW"));

        inventory.set(UUID.fromString(itemId), "5");
        mvc.perform(patch("/api/orders/{id}/status", id).with(as(alice)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CONFIRMED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        mvc.perform(patch("/api/orders/{id}/status", id).with(as(alice)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"NEW\"}"))
                .andExpect(status().isConflict());
        mvc.perform(patch("/api/orders/{id}/status", id).with(as(alice)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isOk());
        mvc.perform(patch("/api/orders/{id}/status", id).with(as(alice)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELED\"}"))
                .andExpect(status().isConflict());
    }
}
