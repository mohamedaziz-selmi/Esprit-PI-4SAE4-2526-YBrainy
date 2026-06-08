package tn.esprit.tpfoyer.Dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentCompletedEvent {
    private Long studentId;
    private Long courseId;
    private String paymentIntentId;
    private String pathId;
}
