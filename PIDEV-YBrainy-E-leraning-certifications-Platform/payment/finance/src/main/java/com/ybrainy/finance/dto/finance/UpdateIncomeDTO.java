package com.ybrainy.finance.dto.finance;

import jakarta.validation.constraints.DecimalMin;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateIncomeDTO {

    private String sourceType;
    private Long referenceId;
    private String description;

    @DecimalMin(value = "0.0", inclusive = false, message = "Amount must be greater than 0")
    private Double amount;

    private String currency;
    private String paymentMethod;
}