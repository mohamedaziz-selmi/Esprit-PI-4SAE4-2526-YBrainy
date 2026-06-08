package tn.esprit.tpfoyer.Dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LearningPathDTO {
    private Long id;
    private Long studentId;
    private String goal;
    private String generatedTitle;
    private String generatedDescription;
    private List<LearningPathCourseDTO> courses;
    private BigDecimal totalPrice;
    private Integer totalDurationMinutes;
    private Boolean saved;
    private LocalDateTime createdAt;
}
