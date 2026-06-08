package com.ybrainy.finance.dto.finance;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class IncomeResponseDTO {
    private Long id;
    private String sourceType;
    private Long referenceId;
    private String description;
    private Double amount;
    private String currency;
    private String paymentMethod;
    private LocalDateTime receivedDate;
    private LocalDateTime createdAt;
}