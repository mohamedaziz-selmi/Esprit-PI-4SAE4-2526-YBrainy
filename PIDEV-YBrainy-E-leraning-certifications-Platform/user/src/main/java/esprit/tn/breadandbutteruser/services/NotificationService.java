package esprit.tn.breadandbutteruser.services;

import esprit.tn.breadandbutteruser.entities.Notification;
import esprit.tn.breadandbutteruser.entities.User;
import esprit.tn.breadandbutteruser.repositories.NotificationRepository;
import esprit.tn.breadandbutteruser.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public void create(Long recipientId, String type, String message, Long threadId) {
        User recipient = userRepository.findById(recipientId).orElse(null);
        if (recipient == null) {
            log.warn("Cannot create notification — user {} not found", recipientId);
            return;
        }
        Notification n = Notification.builder()
                .recipient(recipient)
                .type(type)
                .message(message)
                .threadId(threadId)
                .build();
        notificationRepository.save(n);
    }

    @Transactional(readOnly = true)
    public List<Notification> getForUser(Long userId) {
        return notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public long countUnread(Long userId) {
        return notificationRepository.countByRecipientUserIdAndReadFalse(userId);
    }

    @Transactional
    public void markAsRead(Long id) {
        notificationRepository.findById(id).ifPresent(n -> {
            n.setRead(true);
            notificationRepository.save(n);
        });
    }

    @Transactional
    public void markAllRead(Long userId) {
        notificationRepository.markAllAsRead(userId);
    }
}
