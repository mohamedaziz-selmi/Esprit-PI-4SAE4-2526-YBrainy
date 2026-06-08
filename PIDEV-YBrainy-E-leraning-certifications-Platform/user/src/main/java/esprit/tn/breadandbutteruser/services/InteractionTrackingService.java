package esprit.tn.breadandbutteruser.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import esprit.tn.breadandbutteruser.dto.TrackingBatchRequestDto;
import esprit.tn.breadandbutteruser.dto.TrackingEventDto;
import esprit.tn.breadandbutteruser.entities.InteractionEvent;
import esprit.tn.breadandbutteruser.repositories.InteractionEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional
public class InteractionTrackingService {

    private final InteractionEventRepository repo;
    private final ObjectMapper objectMapper;

    public void ingest(TrackingBatchRequestDto batch, String keycloakSubject, String email, String role) {
        for (TrackingEventDto ev : batch.getEvents()) {
            repo.save(InteractionEvent.builder()
                    .keycloakSubject(keycloakSubject)
                    .email(email)
                    .role(role)
                    .eventType(ev.getEventType())
                    .occurredAtEpochMs(ev.getOccurredAtEpochMs())
                    .route(ev.getRoute())
                    .durationMs(ev.getDurationMs())
                    .metadataJson(toJson(ev.getMetadata()))
                    .receivedAt(LocalDateTime.now())
                    .build());
        }
    }

    private String toJson(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            // Preserve ingest availability but don't silently swallow corruption.
            System.err.println("Failed to serialize tracking metadata to JSON: " + e.getMessage());
            return null;
        }
    }
}
