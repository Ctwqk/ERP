package com.example.task.messaging;

import com.example.common.events.EventTopology;
import com.example.common.events.TaskCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Forwards task domain events to RabbitMQ, but only after the database transaction has committed so consumers never
 * hear about a change that was rolled back. Delivery is best-effort: if the broker is down the failure is logged and
 * the event is lost (an outbox table would be the next step).
 */
@Component
public class TaskEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(TaskEventPublisher.class);

    private final RabbitTemplate rabbit;

    public TaskEventPublisher(RabbitTemplate rabbit) {
        this.rabbit = rabbit;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTaskCompleted(TaskCompletedEvent event) {
        try {
            rabbit.convertAndSend(EventTopology.TASK_EXCHANGE, EventTopology.TASK_COMPLETED_KEY, event);
            log.info("Published task.completed for task {}", event.taskId());
        } catch (AmqpException ex) {
            log.error("Could not publish task.completed for task {}", event.taskId(), ex);
        }
    }
}
