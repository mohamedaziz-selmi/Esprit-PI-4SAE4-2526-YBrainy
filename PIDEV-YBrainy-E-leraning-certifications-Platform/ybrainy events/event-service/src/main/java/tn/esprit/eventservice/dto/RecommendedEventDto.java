package tn.esprit.eventservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tn.esprit.eventservice.entity.EventType;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendedEventDto {
    private long idEvent;
    private String name;
    private EventType type;
    private LocalDateTime dateDebut;
    private String location;
    private String description;
    
    private double recommendationScore; // 0.0 to 1.0
    private String recommendationReason;
}
