package com.backend.dto.cart;

import com.backend.entity.enums.CartStatus;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class CartResponseDTO {

    private Long id;
    private Long userId;
    private CartStatus status;
    private Double totalAmount;
    private List<CartItemResponseDTO> items;
}
