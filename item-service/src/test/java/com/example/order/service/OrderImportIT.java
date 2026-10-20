package com.example.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.document.domain.Document;
import com.example.document.repository.DocumentRepository;
import com.example.item.ContainersConfig;
import com.example.item.ItemServiceApplication;
import com.example.item.domain.Item;
import com.example.item.repository.ItemRepository;
import com.example.item.repository.UomRepository;
import com.example.order.dto.OrderDto;
import com.example.order.repository.OrderRepository;
import com.example.order.utils.XlsxFixtures;
import com.jayway.jsonpath.JsonPath;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Import provenance and serialization against real PostgreSQL and MinIO. */
@SpringBootTest(classes = ItemServiceApplication.class)
@AutoConfigureMockMvc
@Import(ContainersConfig.class)
class OrderImportIT {

    @Autowired MockMvc mvc;
    @Autowired OrderService service;
    @Autowired OrderRepository orders;
    @Autowired DocumentRepository documents;
    @Autowired ItemRepository items;
    @Autowired UomRepository uoms;
    @Autowired @Qualifier("minioClient") MinioClient minio;

    private Document spreadsheet(UUID owner, String sku) throws Exception {
        Item item = new Item();
        item.setSkuCode(sku);
        item.setName(sku);
        item.setItemType(Item.ItemType.FG);
        item.setBaseUom(uoms.findByCode("EA").orElseThrow());
        items.saveAndFlush(item);
        byte[] bytes = XlsxFixtures.orderBySku(new Object[] {sku, 4, 100});
        String key = "import-review/" + UUID.randomUUID();
        minio.putObject(PutObjectArgs.builder().bucket("it-assets").object(key)
                .stream(new ByteArrayInputStream(bytes), bytes.length, -1).build());
        Document doc = new Document();
        doc.setDocType(Document.DocType.ORDER);
        doc.setTitle("Order import");
        doc.setOriginalFilename("order.xlsx");
        doc.setSizeBytes((long) bytes.length);
        doc.setClassification(Document.Classification.INTERNAL);
        doc.setRevision("A");
        doc.setStatus(Document.Status.APPROVED);
        doc.setBucket("it-assets");
        doc.setObjectKey(key);
        doc.setEncryption(Document.Encryption.NONE);
        doc.setCreatedByUserId(owner);
        return documents.saveAndFlush(doc);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void ordinaryAttachmentIsNotProofThatASpreadsheetWasImported(boolean sameOwner) throws Exception {
        UUID uploader = UUID.randomUUID();
        UUID attachmentOwner = sameOwner ? uploader : UUID.randomUUID();
        String sku = "IMPORT-" + UUID.randomUUID();
        Document doc = spreadsheet(uploader, sku);
        String body = mvc.perform(post("/api/orders")
                        .with(jwt().jwt(j -> j.claim("uid", attachmentOwner.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"sku":"%s","quantity":1,"unitPriceCents":1}],"documentIds":["%s"]}
                                """.formatted(sku, doc.getId())))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID attachedTo = UUID.fromString(JsonPath.read(body, "$.id"));

        OrderDto imported = service.importOrderFromDocument(doc.getId(), uploader.toString());

        assertThat(imported.id()).isNotEqualTo(attachedTo);
        assertThat(imported.userId()).isEqualTo(uploader.toString());
        assertThat(imported.totalAmountCents()).isEqualTo(400L);
        assertThat(imported.items()).singleElement().satisfies(line -> assertThat(line.quantity()).isEqualTo(4));
        assertThat(service.importOrderFromDocument(doc.getId(), uploader.toString()).id()).isEqualTo(imported.id());
    }

    @Test
    void concurrentImportsReturnOnePersistedOrder() throws Exception {
        UUID uploader = UUID.randomUUID();
        Document doc = spreadsheet(uploader, "RACE-" + UUID.randomUUID());
        var pool = Executors.newFixedThreadPool(4);
        CountDownLatch ready = new CountDownLatch(4);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<OrderDto>> results = new ArrayList<>();
        try {
            for (int i = 0; i < 4; i++) {
                results.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start timed out");
                    return service.importOrderFromDocument(doc.getId(), uploader.toString());
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<UUID> ids = new ArrayList<>();
            for (Future<OrderDto> result : results) ids.add(result.get(20, TimeUnit.SECONDS).id());
            assertThat(ids.stream().distinct()).hasSize(1);
            assertThat(orders.findByUserIdOrderByCreatedAtDesc(uploader.toString(), Pageable.unpaged()).getContent())
                    .singleElement().satisfies(order -> assertThat(order.getTotalAmountCents()).isEqualTo(400L));
        } finally {
            start.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }
}
