package tn.esprit.personalitybehaviorservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PersonalityResponseDto {
    private String personalityId;
    private Double visualLearningPct;
    private Double auditoryLearningPct;
    private Double kinestheticLearningPct;
    private Double careerAlignmentScore;
    private Double cognitiveLoadTolerance;
    private List<String> careerGoals;
    private BehaviorResponseDto behavior;
    private Long userId;
}
