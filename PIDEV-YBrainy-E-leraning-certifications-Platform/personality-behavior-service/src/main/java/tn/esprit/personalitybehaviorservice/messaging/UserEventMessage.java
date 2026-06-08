package tn.esprit.personalitybehaviorservice.messaging;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserEventMessage {
    private String eventId;
    private String eventType;
    private Long userId;
    private String keycloakUserId;
    private String username;
    private String email;
    private String role;
    private long xp;
    private int level;
    private Instant occurredAt;
    private Map<String, Object> metadata;
}
