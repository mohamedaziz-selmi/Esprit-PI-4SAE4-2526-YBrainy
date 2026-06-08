package com.esprit.userservice.service;

import com.esprit.userservice.dto.NotificationResponse;
import com.esprit.userservice.exception.ResourceNotFoundException;
import com.esprit.userservice.model.Notification;
import com.esprit.userservice.model.User;
import com.esprit.userservice.repository.NotificationRepository;
import com.esprit.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public void create(Long recipientId, String type, String message, Long threadId) {
        User recipient = userRepository.findById(recipientId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + recipientId));

        Notification n = Notification.builder()
                .recipient(recipient)
                .type(type)
                .message(message)
                .threadId(threadId)
                .build();
        notificationRepository.save(n);
    }

    public List<NotificationResponse> getForUser(Long userId) {
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(userId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    public long countUnread(Long userId) {
        return notificationRepository.countByRecipientIdAndReadFalse(userId);
    }

    @Transactional
    public void markAsRead(Long id) {
        Notification n = notificationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + id));
        n.setRead(true);
        notificationRepository.save(n);
    }

    @Transactional
    public void markAllRead(Long userId) {
        notificationRepository.markAllAsRead(userId);
    }

    private NotificationResponse toResponse(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .type(n.getType())
                .message(n.getMessage())
                .threadId(n.getThreadId())
                .read(n.isRead())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
