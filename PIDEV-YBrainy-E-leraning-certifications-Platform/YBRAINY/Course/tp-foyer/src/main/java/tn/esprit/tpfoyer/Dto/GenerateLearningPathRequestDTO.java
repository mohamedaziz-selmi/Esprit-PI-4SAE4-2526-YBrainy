package tn.esprit.tpfoyer.Dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GenerateLearningPathRequestDTO {
    private Long studentId;
    private String goal;
}
