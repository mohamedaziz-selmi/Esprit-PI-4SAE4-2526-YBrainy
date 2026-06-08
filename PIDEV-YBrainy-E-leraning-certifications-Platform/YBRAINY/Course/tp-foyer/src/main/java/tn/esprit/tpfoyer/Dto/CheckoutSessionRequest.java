package tn.esprit.tpfoyer.Dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutSessionRequest {

    private Long courseId;
    private Long studentId;
    private String courseTitle;
    private Double price;
}
