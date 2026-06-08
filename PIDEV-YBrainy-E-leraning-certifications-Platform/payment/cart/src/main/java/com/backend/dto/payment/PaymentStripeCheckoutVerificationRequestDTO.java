package com.backend.dto.payment;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PaymentStripeCheckoutVerificationRequestDTO {

    @NotBlank
    private String sessionId;
}
