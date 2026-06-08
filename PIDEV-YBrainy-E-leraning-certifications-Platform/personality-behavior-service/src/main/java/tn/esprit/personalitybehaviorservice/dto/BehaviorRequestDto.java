package tn.esprit.personalitybehaviorservice.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BehaviorRequestDto {
    @DecimalMin(value = "0.0", message = "agitationLevelPct must be >= 0")
    @DecimalMax(value = "100.0", message = "agitationLevelPct must be <= 100")
    private Double agitationLevelPct;

    @DecimalMin(value = "0.0", message = "focusScorePct must be >= 0")
    @DecimalMax(value = "100.0", message = "focusScorePct must be <= 100")
    private Double focusScorePct;

    @DecimalMin(value = "0.0", message = "engagementIndexPct must be >= 0")
    @DecimalMax(value = "100.0", message = "engagementIndexPct must be <= 100")
    private Double engagementIndexPct;

    @DecimalMin(value = "0.0", message = "learningPacePercentile must be >= 0")
    @DecimalMax(value = "100.0", message = "learningPacePercentile must be <= 100")
    private Double learningPacePercentile;

    @DecimalMin(value = "0.0", message = "fraudProbabilityScore must be >= 0")
    @DecimalMax(value = "1.0", message = "fraudProbabilityScore must be <= 1")
    private Double fraudProbabilityScore;

    private Long userId;
}
