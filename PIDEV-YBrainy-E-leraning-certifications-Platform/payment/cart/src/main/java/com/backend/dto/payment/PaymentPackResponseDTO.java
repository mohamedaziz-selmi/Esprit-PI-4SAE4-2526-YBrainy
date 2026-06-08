package com.backend.dto.payment;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PaymentPackResponseDTO {

    private Long id;
    private String title;
    private Double salePrice;
}
