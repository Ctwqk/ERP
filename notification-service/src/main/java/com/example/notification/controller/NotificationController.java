package com.example.notification.controller;

import com.example.notification.dto.CreateNotificationLinkRequest;
import com.example.notification.dto.CreateNotificationRequest;
import com.example.notification.dto.MarkReadRequest;
import com.example.notification.dto.NotificationDto;
import com.example.notification.dto.NotificationLinkDto;
import com.example.notification.service.NotificationService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NotificationDto create(@Valid @RequestBody CreateNotificationRequest request) {
        return notificationService.create(request);
    }

    @GetMapping("/{id}")
    public NotificationDto getById(@PathVariable UUID id) {
        return notificationService.get(id);
    }

    @GetMapping
    public Page<NotificationDto> listMine(@PageableDefault(size = 20) Pageable pageable,
            @RequestParam(name = "status", required = false) String status) {
        return notificationService.listMine(pageable, status);
    }

    @PatchMapping("/{id}/read")
    public NotificationDto markRead(@PathVariable UUID id, @RequestBody MarkReadRequest request) {
        return notificationService.markRead(id, request);
    }

    @PostMapping("/{id}/links")
    @ResponseStatus(HttpStatus.CREATED)
    public NotificationLinkDto addLink(@PathVariable UUID id, @Valid @RequestBody CreateNotificationLinkRequest request) {
        return notificationService.addLink(id, request);
    }

    @DeleteMapping("/{id}/links/{linkId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteLink(@PathVariable UUID id, @PathVariable UUID linkId) {
        notificationService.deleteLink(id, linkId);
    }
}

