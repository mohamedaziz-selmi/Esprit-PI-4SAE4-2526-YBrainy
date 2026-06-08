package com.backend.dto.cart;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CartItemResponseDTO {

    private Long id;
    private Long packId;
    private String packTitle;
    private Double priceAtPurchase;
    private Integer quantity;
    private Double subtotal;
}
