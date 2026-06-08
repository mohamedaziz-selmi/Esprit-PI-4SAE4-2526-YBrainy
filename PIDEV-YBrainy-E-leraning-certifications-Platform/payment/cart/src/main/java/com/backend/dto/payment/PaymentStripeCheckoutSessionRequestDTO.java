package com.backend.dto.payment;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentStripeCheckoutSessionRequestDTO {

    @NotNull
    private Long userId;

    @NotNull
    private Long cartId;

    @Valid
    @NotEmpty
    private List<PaymentStripeCheckoutItemRequestDTO> items;
}
