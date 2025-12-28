package com.example.task.messaging;

import com.example.task.config.RabbitConfig;
import com.example.task.domain.Task;
import com.example.task.repository.TaskRepository;
import com.example.task.events.InventoryInEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class InventoryEventListener {

    private final TaskRepository taskRepository;

    public InventoryEventListener(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @RabbitListener(queues = RabbitConfig.INVENTORY_IN_QUEUE)
    @Transactional
    public void onInventoryIn(InventoryInEvent event) {
        if (event == null || event.itemId() == null) return;
        UUID itemId = event.itemId();
        double qty = event.quantity();
        List<Task> tasks = taskRepository.findAll(); // simplistic; consider custom query
        for (Task t : tasks) {
            if (t.getPrerequisiteItemId() != null
                    && t.getPrerequisiteItemId().equals(itemId)
                    && t.getPrerequisiteItemQuantity() != null
                    && qty >= t.getPrerequisiteItemQuantity()
                    && t.getStatus() == Task.Status.OPEN) {
                t.setStatus(Task.Status.IN_PROGRESS);
                taskRepository.save(t);
            }
        }
    }
}

