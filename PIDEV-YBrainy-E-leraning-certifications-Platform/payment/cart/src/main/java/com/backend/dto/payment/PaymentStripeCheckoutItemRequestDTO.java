package com.backend.dto.payment;

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
public class PaymentStripeCheckoutItemRequestDTO {

    private Long packId;
    private String packTitle;
    private Double priceAtPurchase;
    private Integer quantity;
}
