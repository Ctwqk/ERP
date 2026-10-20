package com.example.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.common.events.DocumentUploadEvent;
import com.example.common.events.EventTopology;
import com.example.item.ContainersConfig;
import com.example.item.ItemServiceApplication;
import com.example.order.utils.XlsxFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * The whole document pipeline against real PostgreSQL, MinIO and RabbitMQ: presigned upload, completion, the
 * {@code document.file.upload} event and the automatic import of an uploaded order spreadsheet.
 */
@SpringBootTest(classes = ItemServiceApplication.class)
@AutoConfigureMockMvc
@Import(ContainersConfig.class)
class DocumentFlowIT {

    @Autowired MockMvc mvc;
    @Autowired RabbitAdmin admin;
    @Autowired RabbitTemplate rabbit;
    @Autowired ObjectMapper json;

    private final HttpClient http = HttpClient.newHttpClient();

    private static JwtRequestPostProcessor as(UUID uid) {
        return jwt().jwt(j -> j.subject(uid + "@example.com").claim("uid", uid.toString()));
    }

    private void createItem(UUID caller, String sku) throws Exception {
        String uoms = mvc.perform(get("/api/uoms").with(as(caller))).andReturn().getResponse().getContentAsString();
        List<String> ea = JsonPath.read(uoms, "$[?(@.code=='EA')].id");
        mvc.perform(post("/api/items").with(as(caller)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"skuCode\":\"%s\",\"name\":\"%s\",\"itemType\":\"RAW\",\"baseUomId\":\"%s\"}"
                                .formatted(sku, sku, ea.get(0))))
                .andExpect(status().isCreated());
    }

    private String initUpload(UUID caller, UUID linkId, String filename, int size, String linkType) throws Exception {
        return mvc.perform(post("/api/documents/{id}/images:init", linkId).with(as(caller))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"filename\":\"%s\",\"contentType\":\"application/octet-stream\",\"sizeBytes\":%d,\"linkType\":\"%s\"}"
                                .formatted(filename, size, linkType)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private int put(String url, byte[] bytes) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(url)).PUT(HttpRequest.BodyPublishers.ofByteArray(bytes))
                .build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    @Test
    void onlyTheOrderOwnerCanInitializeAnUploadLinkedToAnExistingOrder() throws Exception {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        createItem(alice, "DOC-ORDER-OWNER");
        String order = mvc.perform(post("/api/orders").with(as(alice)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"sku\":\"DOC-ORDER-OWNER\",\"quantity\":1,\"unitPriceCents\":10}]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String orderId = JsonPath.read(order, "$.id");

        mvc.perform(post("/api/documents/{id}/images:init", orderId).with(as(bob))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"filename\":\"drawing.pdf\",\"sizeBytes\":10,\"linkType\":\"ORDER\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/orders/{id}", orderId).with(as(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentIds").isEmpty());

        String init = initUpload(alice, UUID.fromString(orderId), "drawing.pdf", 10, "ORDER");
        String documentId = JsonPath.read(init, "$.documentId");
        mvc.perform(get("/api/orders/{id}", orderId).with(as(alice)))
                .andExpect(jsonPath("$.documentIds.length()").value(1))
                .andExpect(jsonPath("$.documentIds[0]").value(documentId));
    }

    @Test
    void uploadedOrderSpreadsheetBecomesAnOrderForTheUploader() throws Exception {
        UUID uploader = UUID.randomUUID();
        createItem(uploader, "IMP-BOLT");
        createItem(uploader, "IMP-NUT");
        byte[] xlsx = XlsxFixtures.orderBySku(new Object[] {"IMP-BOLT", 4, 250}, new Object[] {"IMP-NUT", 10, 15});

        Queue probe = new Queue("test.document-upload.probe", false, false, true);
        admin.declareQueue(probe);
        admin.declareBinding(BindingBuilder.bind(probe).to(new TopicExchange(EventTopology.DOCUMENT_EXCHANGE, true, false))
                .with(EventTopology.DOCUMENT_UPLOADED_KEY));

        UUID importBatch = UUID.randomUUID();   // ORDER uploads have no order yet; the id just groups the upload
        String init = initUpload(uploader, importBatch, "po-4711.xlsx", xlsx.length, "ORDER");
        String documentId = JsonPath.read(init, "$.documentId");
        String uploadUrl = JsonPath.read(init, "$.uploadUrl");

        // nothing uploaded yet -> cannot complete
        mvc.perform(post("/api/documents/{id}/images:complete", importBatch).with(as(uploader))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"documentId\":\"%s\"}".formatted(documentId)))
                .andExpect(status().isConflict());

        // An unrelated account cannot hijack completion or rewrite the document owner.
        UUID other = UUID.randomUUID();
        mvc.perform(post("/api/documents/{id}/images:complete", importBatch).with(as(other))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"documentId\":\"%s\"}".formatted(documentId)))
                .andExpect(status().isForbidden());
        mvc.perform(MockMvcRequestBuilders.put("/api/documents/{id}", documentId).with(as(other))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"createdByUserId\":\"%s\"}".formatted(other)))
                .andExpect(status().isForbidden());

        assertThat(put(uploadUrl, xlsx)).isEqualTo(200);   // straight to MinIO with the presigned URL

        mvc.perform(post("/api/documents/{id}/images:complete", importBatch).with(as(uploader))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"documentId\":\"%s\"}".formatted(documentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
        mvc.perform(get("/api/documents/{id}", documentId).with(as(uploader)))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.docType").value("ORDER"));

        // 1) the event went out on the document exchange
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            Object received = rabbit.receiveAndConvert(probe.getName(), 500);
            assertThat(received).isNotNull();
            DocumentUploadEvent event = json.convertValue(received, DocumentUploadEvent.class);
            assertThat(event.documentIds()).containsExactly(UUID.fromString(documentId));
            assertThat(event.documentType()).isEqualTo("ORDER");
            assertThat(event.uploadedByUserId()).isEqualTo(uploader);
        });

        // 2) ... and item-service's own consumer turned the spreadsheet into an order owned by the uploader
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                mvc.perform(get("/api/orders").with(as(uploader)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.totalElements").value(1))
                        .andExpect(jsonPath("$.content[0].totalAmountCents").value(1150))
                        .andExpect(jsonPath("$.content[0].items.length()").value(2))
                        .andExpect(jsonPath("$.content[0].documentIds[0]").value(documentId)));

        // the same document cannot be imported twice (redelivery / manual import)
        String orderId = JsonPath.read(mvc.perform(get("/api/orders").with(as(uploader))).andReturn().getResponse()
                .getContentAsString(), "$.content[0].id");
        mvc.perform(post("/api/orders/{docId}/import", documentId).with(as(uploader)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(orderId));
        mvc.perform(get("/api/orders").with(as(uploader))).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(post("/api/orders/{docId}/import", documentId).with(as(other)))
                .andExpect(status().isForbidden());
    }

    @Test
    void itemDocumentsAreListedAndDownloadableThroughPresignedUrls() throws Exception {
        UUID user = UUID.randomUUID();
        createItem(user, "DOC-ITEM");
        String itemId = JsonPath.read(mvc.perform(get("/api/items/sku/DOC-ITEM").with(as(user))).andReturn()
                .getResponse().getContentAsString(), "$.id");
        byte[] bytes = "%PDF-1.4 fake drawing".getBytes();

        String init = initUpload(user, UUID.fromString(itemId), "drawing.pdf", bytes.length, "ITEM");
        String documentId = JsonPath.read(init, "$.documentId");

        // not downloadable until the upload is completed
        mvc.perform(get("/api/documents/documents/{id}:download-url", documentId).with(as(user)))
                .andExpect(status().isConflict());

        assertThat(put(JsonPath.read(init, "$.uploadUrl"), bytes)).isEqualTo(200);
        mvc.perform(post("/api/documents/{id}/images:complete", itemId).with(as(user))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"documentId\":\"%s\"}".formatted(documentId)))
                .andExpect(status().isOk());

        mvc.perform(get("/api/documents").param("itemId", itemId).with(as(user)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(documentId))
                .andExpect(jsonPath("$[0].title").value("drawing.pdf"));
        mvc.perform(get("/api/items/{id}/documents", itemId).with(as(user)))
                .andExpect(jsonPath("$[0]").value(documentId));

        String url = JsonPath.read(mvc.perform(get("/api/documents/documents/{id}:download-url", documentId)
                        .with(as(user))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                "$.url");
        HttpResponse<byte[]> download = http.send(HttpRequest.newBuilder(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(download.statusCode()).isEqualTo(200);
        assertThat(download.body()).isEqualTo(bytes);
    }

    @Test
    void sizeMismatchAndUnknownItemAreRejected() throws Exception {
        UUID user = UUID.randomUUID();
        createItem(user, "DOC-SIZE");
        String itemId = JsonPath.read(mvc.perform(get("/api/items/sku/DOC-SIZE").with(as(user))).andReturn()
                .getResponse().getContentAsString(), "$.id");

        String init = initUpload(user, UUID.fromString(itemId), "a.bin", 100, "ITEM");
        assertThat(put(JsonPath.read(init, "$.uploadUrl"), new byte[40])).isEqualTo(200);
        mvc.perform(post("/api/documents/{id}/images:complete", itemId).with(as(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"documentId\":\"%s\"}".formatted((String) JsonPath.read(init, "$.documentId"))))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/documents/{id}/images:init", UUID.randomUUID()).with(as(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"filename\":\"a.bin\",\"sizeBytes\":10}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/documents/{id}/images:init", itemId).with(as(user))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"filename\":\"\",\"sizeBytes\":10}"))
                .andExpect(status().isBadRequest());
    }
}
