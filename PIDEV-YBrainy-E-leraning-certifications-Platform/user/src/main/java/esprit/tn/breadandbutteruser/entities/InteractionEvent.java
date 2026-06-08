package esprit.tn.breadandbutteruser.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "interaction_events")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InteractionEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String keycloakSubject;

    private String email;

    @Column(nullable = false)
    private String role;

    @Column(nullable = false)
    private String eventType;

    @Column(nullable = false)
    private Long occurredAtEpochMs;

    private String route;

    private Long durationMs;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String metadataJson;

    @Column(nullable = false)
    private LocalDateTime receivedAt;
}
