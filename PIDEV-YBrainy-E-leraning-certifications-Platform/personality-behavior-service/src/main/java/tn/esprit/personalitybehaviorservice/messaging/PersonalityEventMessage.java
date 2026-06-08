package tn.esprit.personalitybehaviorservice.messaging;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PersonalityEventMessage {
    private String eventId;
    private String eventType;
    private String personalityId;
    private String behaviorId;
    private Long userId;
    private Instant occurredAt;
}
