package tn.esprit.personalitybehaviorservice.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

// Embedded document in Personality (denormalized for MongoDB)
@Document(collection = "behaviors")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Behavior {

    @Id
    private String id;

    private Double agitationLevelPct;
    private Double focusScorePct;
    private Double engagementIndexPct;
    private Double learningPacePercentile;
    private Double fraudProbabilityScore;
    private LocalDateTime lastInteraction;
    private Long userId;
}
