package com.backend.dto.cart;

import com.backend.entity.enums.CartAction;
import com.backend.entity.enums.CartStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class CartHistoryResponseDTO {

    private Long id;
    private Long cartId;
    private Long cartItemId;
    private CartAction action;
    private String packTitle;
    private Integer quantity;
    private Double totalAmount;
    private CartStatus cartStatus;
    private String description;
    private LocalDateTime createdAt;
}
