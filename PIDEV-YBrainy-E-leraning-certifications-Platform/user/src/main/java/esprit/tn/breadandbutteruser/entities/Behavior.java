package esprit.tn.breadandbutteruser.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "behaviors")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Behavior {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long behaviorId;

    private Double agitationLevelPct;
    private Double focusScorePct;
    private Double engagementIndexPct;
    private Double learningPacePercentile;
    private Double fraudProbabilityScore;
    private LocalDateTime lastInteraction;


}
