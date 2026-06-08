package com.backend.dto.payment;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StripeCheckoutVerificationRequestDTO {

    @NotBlank(message = "sessionId is required")
    private String sessionId;
}
