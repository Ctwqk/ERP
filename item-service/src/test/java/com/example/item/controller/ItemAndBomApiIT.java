package com.example.item.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.item.ContainersConfig;
import com.example.item.ItemServiceApplication;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Items, units of measure and BOMs over REST against PostgreSQL (Flyway schema + Hibernate validation). */
@SpringBootTest(classes = ItemServiceApplication.class)
@AutoConfigureMockMvc
@Import(ContainersConfig.class)
class ItemAndBomApiIT {

    @Autowired MockMvc mvc;

    private static JwtRequestPostProcessor user() {
        return jwt().jwt(j -> j.subject("planner@example.com").claim("uid", UUID.randomUUID().toString()));
    }

    private String json(ResultActions result, String path) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), path);
    }

    private String uomId(String code) throws Exception {
        String body = mvc.perform(get("/api/uoms").with(user())).andReturn().getResponse().getContentAsString();
        java.util.List<String> ids = JsonPath.read(body, "$[?(@.code=='" + code + "')].id");
        return ids.get(0);
    }

    private String createItem(String sku, String type, String uom) throws Exception {
        return json(mvc.perform(post("/api/items").with(user()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"skuCode":"%s","name":"Item %s","itemType":"%s","baseUomId":"%s"}"""
                                .formatted(sku, sku, type, uom)))
                .andExpect(status().isCreated()), "$.id");
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/items")).andExpect(status().isUnauthorized());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    @Test
    void seededUnitsOfMeasureAndCustomOnes() throws Exception {
        mvc.perform(get("/api/uoms").with(user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code=='EA')]").exists())
                .andExpect(jsonPath("$[?(@.code=='KG')]").exists());
        mvc.perform(post("/api/uoms").with(user()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"pallet\",\"name\":\"Pallet\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("PALLET"));
        mvc.perform(post("/api/uoms").with(user()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"PALLET\",\"name\":\"Again\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void itemCrudSearchAndValidation() throws Exception {
        String ea = uomId("EA");
        String id = createItem("IT-100", "RAW", ea);
        createItem("IT-200", "FG", ea);

        mvc.perform(post("/api/items").with(user()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"skuCode\":\"IT-100\",\"name\":\"dup\",\"itemType\":\"RAW\",\"baseUomId\":\"%s\"}"
                                .formatted(ea)))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/items").with(user()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"skuCode\":\"IT-300\",\"name\":\"x\",\"itemType\":\"RAW\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/items").with(user()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"skuCode\":\"IT-300\",\"name\":\"x\",\"itemType\":\"NOPE\",\"baseUomId\":\"%s\"}"
                                .formatted(ea)))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/items/{id}", id).with(user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skuCode").value("IT-100"))
                .andExpect(jsonPath("$.active").value(true));
        mvc.perform(get("/api/items/sku/IT-200").with(user())).andExpect(status().isOk());
        mvc.perform(get("/api/items/{id}", UUID.randomUUID()).with(user())).andExpect(status().isNotFound());

        // native search query with and without optional filters
        mvc.perform(get("/api/items").param("keyword", "it-1").with(user()))
                .andExpect(jsonPath("$.content[?(@.skuCode=='IT-100')]").exists())
                .andExpect(jsonPath("$.content[?(@.skuCode=='IT-200')]").doesNotExist());
        mvc.perform(get("/api/items").param("itemType", "FG").with(user()))
                .andExpect(jsonPath("$.content[?(@.skuCode=='IT-200')]").exists())
                .andExpect(jsonPath("$.content[?(@.skuCode=='IT-100')]").doesNotExist());
        mvc.perform(get("/api/items").with(user())).andExpect(status().isOk());

        // sorting by entity property works (camelCase too) and nonsense is a 400, not a 500
        mvc.perform(get("/api/items").param("sort", "skuCode,desc").param("keyword", "IT-").with(user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].skuCode").value("IT-200"));
        mvc.perform(get("/api/items").param("sort", "[\"string\"]").with(user()))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/items").param("sort", "noSuchField").with(user()))
                .andExpect(status().isBadRequest());

        mvc.perform(put("/api/items/{id}", id).with(user()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Renamed\",\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed"))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.skuCode").value("IT-100"));
        mvc.perform(get("/api/items").param("active", "false").with(user()))
                .andExpect(jsonPath("$.content[?(@.skuCode=='IT-100')]").exists());
        mvc.perform(put("/api/items/{id}", UUID.randomUUID()).with(user()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"ghost\"}"))
                .andExpect(status().isNotFound());

        mvc.perform(delete("/api/items/{id}", id).with(user())).andExpect(status().isNoContent());
        mvc.perform(delete("/api/items/{id}", id).with(user())).andExpect(status().isNotFound());
    }

    @Test
    void bomLifecycleRulesAndRequirements() throws Exception {
        String ea = uomId("EA");
        String kg = uomId("KG");
        String product = createItem("BOM-FG", "FG", ea);
        String steel = createItem("BOM-STEEL", "RAW", kg);
        String bolt = createItem("BOM-BOLT", "RAW", ea);

        String bomId = json(mvc.perform(post("/api/boms").with(user()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productItemId":"%s","revision":"A","lines":[
                                  {"componentItemId":"%s","qtyPer":2.5,"scrapRate":0.2},
                                  {"componentItemId":"%s","qtyPer":4}]}""".formatted(product, steel, bolt)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.lines", hasSize(2)))
                .andExpect(jsonPath("$.lines[0].lineNo").value(10))
                .andExpect(jsonPath("$.lines[0].uomId").value(kg))   // defaults to the component's base UOM
                .andExpect(jsonPath("$.lines[1].lineNo").value(20)), "$.id");

        // single-level explosion: gross = qtyPer * qty / (1 - scrap)
        mvc.perform(get("/api/boms/{id}/requirements", bomId).param("quantity", "10").with(user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].componentSku").value("BOM-STEEL"))
                .andExpect(jsonPath("$[0].netQuantity").value(25.0))
                .andExpect(jsonPath("$[0].grossQuantity").value(31.25))
                .andExpect(jsonPath("$[1].netQuantity").value(40.0))
                .andExpect(jsonPath("$[1].grossQuantity").value(40.0));
        mvc.perform(get("/api/boms/{id}/requirements", bomId).param("quantity", "0").with(user()))
                .andExpect(status().isBadRequest());

        // editing rules for DRAFT BOMs
        mvc.perform(post("/api/boms/{id}/lines", bomId).with(user()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"componentItemId\":\"%s\",\"qtyPer\":1}".formatted(product)))
                .andExpect(status().isBadRequest());   // product inside its own BOM
        mvc.perform(post("/api/boms/{id}/lines", bomId).with(user()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"componentItemId\":\"%s\",\"qtyPer\":1}".formatted(bolt)))
                .andExpect(status().isConflict());     // duplicate component
        mvc.perform(post("/api/boms/{id}/lines", bomId).with(user()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"componentItemId\":\"%s\",\"qtyPer\":1,\"scrapRate\":1}".formatted(steel)))
                .andExpect(status().isBadRequest());

        // activation, then it is frozen
        mvc.perform(put("/api/boms/{id}", bomId).with(user()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        mvc.perform(post("/api/boms/{id}/lines", bomId).with(user()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"componentItemId\":\"%s\",\"qtyPer\":1}".formatted(
                                createItem("BOM-NUT", "RAW", ea))))
                .andExpect(status().isConflict());
        mvc.perform(delete("/api/boms/{id}", bomId).with(user())).andExpect(status().isConflict());

        // a second BOM for the same product+revision cannot become ACTIVE while the first is
        String secondId = json(mvc.perform(post("/api/boms").with(user()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productItemId":"%s","revision":"A","lines":[{"componentItemId":"%s","qtyPer":1}]}"""
                                .formatted(product, bolt)))
                .andExpect(status().isCreated()), "$.id");
        mvc.perform(put("/api/boms/{id}", secondId).with(user()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isConflict());

        // retire the first one; now the second can be activated, and obsolete is final
        mvc.perform(put("/api/boms/{id}", bomId).with(user()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"OBSOLETE\"}"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/boms/{id}", secondId).with(user()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/boms/{id}", bomId).with(user()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isConflict());

        mvc.perform(get("/api/boms").param("productItemId", product).param("status", "ACTIVE").with(user()))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(secondId));
        mvc.perform(get("/api/boms").param("productItemId", product).with(user()))
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/boms/{id}/lines", secondId).with(user()))
                .andExpect(jsonPath("$", hasSize(1)));

        // an item that is part of a BOM cannot be deleted
        mvc.perform(delete("/api/items/{id}", bolt).with(user())).andExpect(status().isConflict());
    }

    @Test
    void emptyBomCannotBeActivated() throws Exception {
        String product = createItem("BOM-EMPTY", "FG", uomId("EA"));
        String id = json(mvc.perform(post("/api/boms").with(user()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productItemId\":\"%s\"}".formatted(product)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.revision").value("A")), "$.id");

        mvc.perform(put("/api/boms/{id}", id).with(user()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isConflict());
        assertThat(id).isNotBlank();
    }
}
