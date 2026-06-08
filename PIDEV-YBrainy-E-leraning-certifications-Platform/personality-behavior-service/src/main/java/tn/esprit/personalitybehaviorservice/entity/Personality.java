package tn.esprit.personalitybehaviorservice.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Document(collection = "personalities")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Personality {

    @Id
    private String id;

    @Indexed(unique = true)
    private Long userId;

    private Double visualLearningPct;
    private Double auditoryLearningPct;
    private Double kinestheticLearningPct;
    private Double careerAlignmentScore;
    private Double cognitiveLoadTolerance;

    @Builder.Default
    private List<String> careerGoals = new ArrayList<>();

    // Embedded behavior document (denormalized for MongoDB)
    private Behavior behavior;

    // Business methods
    public void calculateStyleDistribution() {
        System.out.println("Calculating learning style distribution...");
        double total = visualLearningPct + auditoryLearningPct + kinestheticLearningPct;
        System.out.println("Visual: " + visualLearningPct + "%, Auditory: " +
                auditoryLearningPct + "%, Kinesthetic: " + kinestheticLearningPct + "%");
    }

    public void updateCareerAlignment() {
        System.out.println("Updating career alignment score...");
    }
}
