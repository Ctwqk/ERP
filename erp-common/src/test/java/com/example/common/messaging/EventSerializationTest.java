package com.example.common.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.common.events.DocumentUploadEvent;
import com.example.common.events.TaskCompletedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.MessageConverter;

/** The JSON wire format shared by producers and consumers, including type inference on the consumer side. */
class EventSerializationTest {

    private final ObjectMapper mapper = JsonMapper.builder().addModule(new JavaTimeModule()).build();
    private final MessageConverter converter = new ErpMessagingConfiguration().erpJsonMessageConverter(mapper);

    @Test
    void taskCompletedRoundTripsAndKeepsTheInstant() {
        TaskCompletedEvent event = new TaskCompletedEvent(UUID.randomUUID(), "Pack", UUID.randomUUID(), null,
                OffsetDateTime.parse("2026-10-19T10:15:30+02:00"));

        Message message = converter.toMessage(event, new MessageProperties());
        String json = new String(message.getBody());

        assertThat(json).contains("\"title\":\"Pack\"").contains("\"taskId\"");
        message.getMessageProperties().setInferredArgumentType(TaskCompletedEvent.class);
        TaskCompletedEvent decoded = (TaskCompletedEvent) converter.fromMessage(message);
        assertThat(decoded).usingRecursiveComparison().ignoringFields("completedAt").isEqualTo(event);
        // the offset may be normalised to UTC, the instant must not change
        assertThat(decoded.completedAt().toInstant()).isEqualTo(event.completedAt().toInstant());
    }

    @Test
    void consumerInfersTheTypeFromItsOwnSignatureIgnoringTheProducersTypeHeader() {
        DocumentUploadEvent event = new DocumentUploadEvent(List.of(UUID.randomUUID()), "ORDER", UUID.randomUUID(),
                null, UUID.randomUUID(), "u@example.com");
        Message message = converter.toMessage(event, new MessageProperties());
        message.getMessageProperties().setHeader("__TypeId__", "com.other.service.SomethingElse");

        message.getMessageProperties().setInferredArgumentType(DocumentUploadEvent.class);
        Object decoded = converter.fromMessage(message);

        assertThat(decoded).isEqualTo(event);
    }
}
