package com.backend.dto.cart;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StripeCheckoutSessionResponseDTO {

    private String sessionId;
    private String checkoutUrl;
    private String publishableKey;
}
