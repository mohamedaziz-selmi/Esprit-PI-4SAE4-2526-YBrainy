package com.ybrainy.finance.dto.finance;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class CreateExpenseDTO {

    @NotBlank(message = "Title is required")
    private String title;

    private String description;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Amount must be greater than 0")
    private Double amount;

    @NotBlank(message = "Currency is required")
    private String currency;

    @NotNull(message = "Category is required")
    private String category;

    @NotNull(message = "Status is required")
    private String status;

    @NotNull(message = "Expense date is required")
    private LocalDateTime expenseDate;
}