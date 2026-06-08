package esprit.tn.breadandbutteruser.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "personalities")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Personality {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long personalityId;

    private Double visualLearningPct;
    private Double auditoryLearningPct;
    private Double kinestheticLearningPct;
    private Double careerAlignmentScore;
    private Double cognitiveLoadTolerance;

    @ElementCollection
    @CollectionTable(name = "career_goals", joinColumns = @JoinColumn(name = "personality_id"))
    @Column(name = "goal")
    @Builder.Default
    private List<String> careerGoals = new ArrayList<>();

    // Composition: Personality owns Behavior
    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "behavior_id", referencedColumnName = "behaviorId")
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
