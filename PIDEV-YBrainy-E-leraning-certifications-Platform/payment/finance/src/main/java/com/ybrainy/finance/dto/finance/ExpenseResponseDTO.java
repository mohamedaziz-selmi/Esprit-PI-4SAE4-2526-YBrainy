package com.ybrainy.finance.dto.finance;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class ExpenseResponseDTO {
    private Long id;
    private String title;
    private String description;
    private Double amount;
    private String currency;
    private String category;
    private String status;
    private LocalDateTime expenseDate;
    private LocalDateTime createdAt;
}