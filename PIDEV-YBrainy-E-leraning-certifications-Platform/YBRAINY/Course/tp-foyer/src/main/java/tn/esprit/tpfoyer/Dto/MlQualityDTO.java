package tn.esprit.tpfoyer.Dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MlQualityDTO {
    private String qualityLabel;
    private Double confidence;
    private Double confidencePercentage;
    private Boolean isHighQuality;
    private Integer overallScore;
    private List<String> tips;
    private Map<String, Object> factors;
}
