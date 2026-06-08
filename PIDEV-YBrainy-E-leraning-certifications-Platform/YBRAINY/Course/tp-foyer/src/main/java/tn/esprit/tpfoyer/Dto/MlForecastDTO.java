package tn.esprit.tpfoyer.Dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MlForecastDTO {
    private Integer forecastSteps;
    private List<Double> predictedDemand;
    private List<List<Double>> confidenceIntervals;
}
