package esprit.tn.breadandbutteruser.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserActivityDto {
    private Long id;
    private String eventType;
    private String route;
    private Long durationMs;
    private Long occurredAtEpochMs;
    private LocalDateTime receivedAt;
    private String role;
}
