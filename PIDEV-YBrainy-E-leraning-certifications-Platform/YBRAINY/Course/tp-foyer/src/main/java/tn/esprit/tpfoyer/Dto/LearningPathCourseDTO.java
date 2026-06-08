package tn.esprit.tpfoyer.Dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LearningPathCourseDTO {
    private Long id;
    private String title;
    private String description;
    private String thumbnailUrl;
    private BigDecimal price;
    private String level;
    private String category;
    private Integer approximateDurationMinutes;
    private Integer orderIndex;
    private String whyIncluded;
}
