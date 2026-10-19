package com.example.common.events;

/** RabbitMQ exchange and routing-key names shared by producers and consumers. */
public final class EventTopology {

    /** Topic exchange for document lifecycle events (published by item-service). */
    public static final String DOCUMENT_EXCHANGE = "document.events";
    public static final String DOCUMENT_UPLOADED_KEY = "document.file.upload";

    /** Topic exchange for task lifecycle events (published by task-service). */
    public static final String TASK_EXCHANGE = "task.events";
    public static final String TASK_COMPLETED_KEY = "task.completed";

    private EventTopology() {
    }
}
