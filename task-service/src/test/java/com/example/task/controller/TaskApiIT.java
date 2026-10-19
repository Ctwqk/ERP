package com.example.task.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.common.events.EventTopology;
import com.example.common.events.TaskCompletedEvent;
import com.example.task.ContainersConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
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

/** REST API on PostgreSQL (jsonb, Flyway schema, Hibernate validation) and event publication to a real RabbitMQ. */
@SpringBootTest(properties = "erp.ports.mode=stub")
@AutoConfigureMockMvc
@Import(ContainersConfig.class)
class TaskApiIT {

    @Autowired MockMvc mvc;
    @Autowired RabbitAdmin admin;
    @Autowired RabbitTemplate rabbit;
    @Autowired ObjectMapper json;

    private static JwtRequestPostProcessor as(UUID uid) {
        return jwt().jwt(j -> j.subject(uid + "@example.com").claim("uid", uid.toString()));
    }

    private String createTask(UUID caller, String body) throws Exception {
        return JsonPath.read(mvc.perform(post("/api/tasks").with(as(caller)).contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/tasks")).andExpect(status().isUnauthorized());
    }

    @Test
    void taskLifecycleOverRest() throws Exception {
        UUID alice = UUID.randomUUID();
        UUID item = UUID.randomUUID();
        String id = createTask(alice, """
                {"title":"Kit parts","priority":"HIGH","assigneeUserId":"%s",
                 "prerequisiteItemId":"%s","prerequisiteItemQuantity":2.5}""".formatted(alice, item));

        mvc.perform(get("/api/tasks/{id}", id).with(as(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.prerequisiteItemQuantity").value(2.5))
                .andExpect(jsonPath("$.createdByUserId").value(alice.toString()));

        mvc.perform(get("/api/tasks").param("assignee", "me").with(as(alice)))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/tasks").with(as(UUID.randomUUID())))
                .andExpect(jsonPath("$.totalElements").value(0));

        mvc.perform(patch("/api/tasks/{id}/status", id).with(as(alice)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DONE\"}"))
                .andExpect(status().isConflict());
        mvc.perform(patch("/api/tasks/{id}/status", id).with(as(alice)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/tasks").param("status", "IN_PROGRESS").with(as(alice)))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void linksStoreJsonMetaInJsonbColumn() throws Exception {
        UUID alice = UUID.randomUUID();
        String id = createTask(alice, "{\"title\":\"Linked\"}");

        String linkBody = mvc.perform(post("/api/tasks/{id}/links", id).with(as(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refType\":\"MO\",\"refId\":\"MO-7\",\"refMeta\":\"{\\\"line\\\":3}\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String linkId = JsonPath.read(linkBody, "$.id");

        mvc.perform(get("/api/tasks/{id}", id).with(as(alice)))
                .andExpect(jsonPath("$.links.length()").value(1))
                .andExpect(jsonPath("$.links[0].refId").value("MO-7"))
                .andExpect(jsonPath("$.links[0].refMeta").value("{\"line\": 3}"));

        mvc.perform(post("/api/tasks/{id}/links", id).with(as(alice)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refType\":\"MO\",\"refId\":\"MO-8\",\"refMeta\":\"not json\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(delete("/api/tasks/{id}/links/{l}", id, linkId).with(as(alice)))
                .andExpect(status().isNoContent());
    }

    @Test
    void validationErrorsAreBadRequests() throws Exception {
        mvc.perform(post("/api/tasks").with(as(UUID.randomUUID())).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/tasks").with(as(UUID.randomUUID())).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\",\"prerequisiteItemId\":\"%s\"}".formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/tasks/{id}", UUID.randomUUID()).with(as(UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }

    @Test
    void completingATaskPublishesTaskCompletedToRabbitMq() throws Exception {
        // a throwaway queue bound like a real consumer would bind it
        Queue probe = new Queue("test.task-completed.probe", false, false, true);
        admin.declareQueue(probe);
        admin.declareBinding(BindingBuilder.bind(probe)
                .to(new TopicExchange(EventTopology.TASK_EXCHANGE, true, false))
                .with(EventTopology.TASK_COMPLETED_KEY));

        UUID alice = UUID.randomUUID();
        String id = createTask(alice, "{\"title\":\"Ship it\",\"assigneeUserId\":\"%s\"}".formatted(alice));
        for (String s : new String[] {"IN_PROGRESS", "DONE"}) {
            mvc.perform(patch("/api/tasks/{id}/status", id).with(as(alice)).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"" + s + "\"}"))
                    .andExpect(status().isOk());
        }

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            Object received = rabbit.receiveAndConvert(probe.getName(), 500);
            assertThat(received).isNotNull();
            TaskCompletedEvent event = json.convertValue(received, TaskCompletedEvent.class);
            assertThat(event.taskId()).isEqualTo(UUID.fromString(id));
            assertThat(event.title()).isEqualTo("Ship it");
            assertThat(event.createdByUserId()).isEqualTo(alice);
            assertThat(event.assigneeUserId()).isEqualTo(alice);
        });
    }
}
