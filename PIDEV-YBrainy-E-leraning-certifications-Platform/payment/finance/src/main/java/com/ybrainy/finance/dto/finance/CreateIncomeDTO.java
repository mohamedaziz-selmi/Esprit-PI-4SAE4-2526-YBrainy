package com.ybrainy.finance.dto.finance;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateIncomeDTO {

    @NotBlank(message = "Source type is required")
    private String sourceType;

    private Long referenceId;

    private String description;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Amount must be greater than 0")
    private Double amount;

    @NotBlank(message = "Currency is required")
    private String currency;

    @NotNull(message = "Payment method is required")
    private String paymentMethod;
}