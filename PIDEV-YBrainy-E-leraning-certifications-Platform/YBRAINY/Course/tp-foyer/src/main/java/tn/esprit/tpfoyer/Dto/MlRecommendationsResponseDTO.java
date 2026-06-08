package tn.esprit.tpfoyer.Dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MlRecommendationsResponseDTO {
    private List<MlRecommendationDTO> recommendations;
    private Map<String, String> basedOn;
}
