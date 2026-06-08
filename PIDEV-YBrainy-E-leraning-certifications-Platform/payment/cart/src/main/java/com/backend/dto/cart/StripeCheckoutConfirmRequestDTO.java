package com.backend.dto.cart;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StripeCheckoutConfirmRequestDTO {

    @NotBlank(message = "sessionId is required")
    private String sessionId;
}
