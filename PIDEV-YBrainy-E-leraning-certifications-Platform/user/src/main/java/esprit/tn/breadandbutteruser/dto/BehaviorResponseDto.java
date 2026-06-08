package esprit.tn.breadandbutteruser.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BehaviorResponseDto {
    private Long behaviorId;
    private Double agitationLevelPct;
    private Double focusScorePct;
    private Double engagementIndexPct;
    private Double learningPacePercentile;
    private Double fraudProbabilityScore;
    private LocalDateTime lastInteraction;
}
