package com.example.notification.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.common.events.DocumentUploadEvent;
import com.example.common.events.EventTopology;
import com.example.common.events.TaskCompletedEvent;
import com.example.notification.ContainersConfig;
import com.example.notification.domain.Notification;
import com.example.notification.repository.NotificationRepository;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;

/** REST API against PostgreSQL plus the RabbitMQ event consumers against a real broker. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ContainersConfig.class)
class NotificationFlowIT {

    @Autowired MockMvc mvc;
    @Autowired RabbitTemplate rabbit;
    @Autowired NotificationRepository notifications;

    private static JwtRequestPostProcessor as(UUID uid) {
        return jwt().jwt(j -> j.subject(uid + "@example.com").claim("uid", uid.toString()));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
    }

    @Test
    void restLifecycle_createListMarkReadAndOwnership() throws Exception {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();

        String body = mvc.perform(post("/api/notifications").with(as(alice)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Review PO\",\"type\":\"INFO\",\"recipientUserId\":\"%s\"}".formatted(bob)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("UNREAD"))
                .andExpect(jsonPath("$.createdByUserId").value(alice.toString()))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");

        mvc.perform(get("/api/notifications").with(as(bob)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/notifications").with(as(alice)))
                .andExpect(jsonPath("$.totalElements").value(0));

        mvc.perform(get("/api/notifications/{id}", id).with(as(alice))).andExpect(status().isForbidden());

        mvc.perform(patch("/api/notifications/{id}/read", id).with(as(bob)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"read\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READ"));
        mvc.perform(get("/api/notifications").param("status", "UNREAD").with(as(bob)))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(post("/api/notifications/{id}/links", id).with(as(bob)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"linkType\":\"TASK\",\"refId\":\"42\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void invalidCreateIsRejected() throws Exception {
        mvc.perform(post("/api/notifications").with(as(UUID.randomUUID())).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\",\"type\":\"INFO\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void taskCompletedEventBecomesNotificationsExactlyOnce() {
        UUID creator = UUID.randomUUID();
        UUID assignee = UUID.randomUUID();
        TaskCompletedEvent event = new TaskCompletedEvent(UUID.randomUUID(), "Calibrate press", creator, assignee,
                OffsetDateTime.now());

        // publish twice: the second delivery simulates at-least-once redelivery
        rabbit.convertAndSend(EventTopology.TASK_EXCHANGE, EventTopology.TASK_COMPLETED_KEY, event);
        rabbit.convertAndSend(EventTopology.TASK_EXCHANGE, EventTopology.TASK_COMPLETED_KEY, event);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(notifications.findByRecipientUserId(creator, Pageable.unpaged()).getContent()).hasSize(1);
            assertThat(notifications.findByRecipientUserId(assignee, Pageable.unpaged()).getContent()).hasSize(1);
        });
        Notification n = notifications.findByRecipientUserId(creator, Pageable.unpaged()).getContent().get(0);
        assertThat(n.getTitle()).isEqualTo("Task completed: Calibrate press");
        assertThat(n.getType()).isEqualTo("TASK_COMPLETED");
    }

    @Test
    void documentUploadEventNotifiesUploaderThroughRestApi() throws Exception {
        UUID uploader = UUID.randomUUID();
        UUID document = UUID.randomUUID();

        rabbit.convertAndSend(EventTopology.DOCUMENT_EXCHANGE, EventTopology.DOCUMENT_UPLOADED_KEY,
                new DocumentUploadEvent(List.of(document), "ORDER", UUID.randomUUID(), null, uploader, "u@example.com"));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                mvc.perform(get("/api/notifications").with(as(uploader)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.totalElements").value(1))
                        .andExpect(jsonPath("$.content[0].type").value("DOCUMENT_UPLOADED"))
                        .andExpect(jsonPath("$.content[0].links[0].refId").value(document.toString())));
    }
}
