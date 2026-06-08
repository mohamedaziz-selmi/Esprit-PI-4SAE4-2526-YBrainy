package tn.esprit.tpfoyer.Dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RatingDistributionDTO {
    private double averageRating;
    private long totalReviews;
    private long count5;
    private long count4;
    private long count3;
    private long count2;
    private long count1;
}
