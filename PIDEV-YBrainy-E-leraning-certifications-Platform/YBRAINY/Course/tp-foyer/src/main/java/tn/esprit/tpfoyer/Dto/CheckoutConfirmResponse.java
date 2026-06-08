package tn.esprit.tpfoyer.Dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutConfirmResponse {
    private String sessionId;
    private String paymentIntentId;
    private List<Map<String, Object>> enrollments;
}
