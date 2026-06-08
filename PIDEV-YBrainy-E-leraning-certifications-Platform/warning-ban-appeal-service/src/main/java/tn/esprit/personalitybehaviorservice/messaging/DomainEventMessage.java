package tn.esprit.warningbanappealservice.messaging;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DomainEventMessage {
    private String eventId;
    private String eventType;
    private Long userId;
    private String warningId;
    private String appealId;
    private Instant occurredAt;
}
