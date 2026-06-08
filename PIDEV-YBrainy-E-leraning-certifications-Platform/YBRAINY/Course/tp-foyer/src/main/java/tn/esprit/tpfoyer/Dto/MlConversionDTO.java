package tn.esprit.tpfoyer.Dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MlConversionDTO {
    private Double conversionProbability;
    private String conversionLabel;
    private Double percentage;
}
