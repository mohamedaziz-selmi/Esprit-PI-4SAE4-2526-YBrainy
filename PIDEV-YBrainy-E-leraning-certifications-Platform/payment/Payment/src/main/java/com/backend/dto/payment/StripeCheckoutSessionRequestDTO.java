package com.backend.dto.payment;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class StripeCheckoutSessionRequestDTO {

    @NotNull
    private Long userId;

    @NotNull
    private Long cartId;

    @Valid
    @NotEmpty
    private List<StripeCheckoutItemRequestDTO> items;
}
