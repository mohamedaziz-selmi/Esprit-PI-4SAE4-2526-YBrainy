package com.backend.dto.payment;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StripeCheckoutItemRequestDTO {

    @NotNull
    private Long packId;

    @NotBlank
    private String packTitle;

    @NotNull
    @Min(0)
    private Double priceAtPurchase;

    @NotNull
    @Min(1)
    private Integer quantity;
}
