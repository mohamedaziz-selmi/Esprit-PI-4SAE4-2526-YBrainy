package com.ybrainy.finance.dto.finance;

import jakarta.validation.constraints.DecimalMin;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class UpdateExpenseDTO {

    private String title;
    private String description;

    @DecimalMin(value = "0.0", inclusive = false, message = "Amount must be greater than 0")
    private Double amount;

    private String currency;
    private String category;
    private String status;
    private LocalDateTime expenseDate;
}