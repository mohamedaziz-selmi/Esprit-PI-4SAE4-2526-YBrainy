package tn.esprit.tpfoyer.Dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewDTO {
    private Long id;
    private Long courseId;
    private Long studentId;
    private Integer rating;
    private String comment;
    private String createdAt;
}
