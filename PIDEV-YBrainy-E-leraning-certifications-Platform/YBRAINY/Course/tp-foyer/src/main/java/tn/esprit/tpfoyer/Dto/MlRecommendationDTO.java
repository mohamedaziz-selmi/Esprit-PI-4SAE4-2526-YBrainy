package tn.esprit.tpfoyer.Dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MlRecommendationDTO {
    private String courseId;
    private String title;
    private String category;
    private String level;
    private Double price;
    private Double rating;
    private Integer numLectures;
    private Boolean isPaid;
    private Double matchScore;
}
