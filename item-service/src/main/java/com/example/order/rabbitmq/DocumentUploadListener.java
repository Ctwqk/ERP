package com.example.order.rabbitmq;

import org.springframework.stereotype.Service;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import com.example.order.service.OrderService;
import com.example.order.rabbitmq.DocumentUploadEvent;
import java.util.UUID;

@Service
public class DocumentUploadListener {

    private final OrderService orderService;

    public DocumentUploadListener(OrderService orderService) {
        this.orderService = orderService;
    }

    @RabbitListener(queues = "inventory.file.upload.q")
    public void onDocumentUploaded(DocumentUploadEvent evt) {
        // 可先过滤类型，例如只处理订单导入类文档
        if (!"ORDER_XLSX".equalsIgnoreCase(evt.documentType())) {
            return;
        }
        if (evt.documentIds() == null || evt.documentIds().isEmpty()) {
            return;
        }
        UUID docId = evt.documentIds().get(0);
        orderService.createOrderFromXlsx(docId);
    }
}
