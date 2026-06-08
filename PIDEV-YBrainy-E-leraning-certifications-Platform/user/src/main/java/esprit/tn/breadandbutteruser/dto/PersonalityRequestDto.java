package esprit.tn.breadandbutteruser.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PersonalityRequestDto {
    @DecimalMin(value = "0.0", message = "visualLearningPct must be >= 0")
    @DecimalMax(value = "100.0", message = "visualLearningPct must be <= 100")
    private Double visualLearningPct;

    @DecimalMin(value = "0.0", message = "auditoryLearningPct must be >= 0")
    @DecimalMax(value = "100.0", message = "auditoryLearningPct must be <= 100")
    private Double auditoryLearningPct;

    @DecimalMin(value = "0.0", message = "kinestheticLearningPct must be >= 0")
    @DecimalMax(value = "100.0", message = "kinestheticLearningPct must be <= 100")
    private Double kinestheticLearningPct;

    @DecimalMin(value = "0.0", message = "careerAlignmentScore must be >= 0")
    @DecimalMax(value = "100.0", message = "careerAlignmentScore must be <= 100")
    private Double careerAlignmentScore;

    @DecimalMin(value = "0.0", message = "cognitiveLoadTolerance must be >= 0")
    @DecimalMax(value = "100.0", message = "cognitiveLoadTolerance must be <= 100")
    private Double cognitiveLoadTolerance;

    @Size(max = 20, message = "careerGoals supports at most 20 entries")
    private List<String> careerGoals;

    @Valid
    private BehaviorRequestDto behavior;
}
