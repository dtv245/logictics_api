package com.company.logicstic.service;

import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.company.logicstic.dto.PagedResponse;
import com.company.logicstic.dto.notification.NotificationView;
import com.company.logicstic.exception.ResourceNotFoundException;
import com.company.logicstic.repository.NotificationRepository;

@Service
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public PagedResponse<NotificationView> list(int page, int pageSize) {
        var pageable = PageRequest.of(page - 1, pageSize, Sort.by("createdDate").descending());
        return PagedResponse.from(notificationRepository.findAll(pageable).map(NotificationView::from));
    }

    public NotificationView getById(UUID id) {
        return notificationRepository.findById(id)
                .map(NotificationView::from)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + id));
    }

    @Transactional
    public int markAllAsRead() {
        return notificationRepository.markAllAsRead();
    }
}