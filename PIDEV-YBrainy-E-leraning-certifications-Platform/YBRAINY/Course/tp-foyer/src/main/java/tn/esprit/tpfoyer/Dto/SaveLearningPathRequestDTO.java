package tn.esprit.tpfoyer.Dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SaveLearningPathRequestDTO {
    private Long studentId;
    private String goal;
    private String generatedTitle;
    private String generatedDescription;
    private List<Long> courseIds;
    private BigDecimal totalPrice;
    private Integer totalDurationMinutes;
    private String whyIncludedJson;
}
