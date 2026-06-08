package tn.esprit.warningbanappealservice.messaging;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserEventMessage {
    private String eventId;
    private String eventType;
    private Long userId;
}
