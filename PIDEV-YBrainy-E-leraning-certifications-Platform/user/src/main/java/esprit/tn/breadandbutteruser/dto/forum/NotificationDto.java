package esprit.tn.breadandbutteruser.dto.forum;

import esprit.tn.breadandbutteruser.entities.Notification;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class NotificationDto {
    private Long id;
    private String type;
    private String message;
    private Long threadId;
    private boolean read;
    private LocalDateTime createdAt;

    public static NotificationDto fromEntity(Notification n) {
        return NotificationDto.builder()
                .id(n.getId())
                .type(n.getType())
                .message(n.getMessage())
                .threadId(n.getThreadId())
                .read(n.isRead())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
