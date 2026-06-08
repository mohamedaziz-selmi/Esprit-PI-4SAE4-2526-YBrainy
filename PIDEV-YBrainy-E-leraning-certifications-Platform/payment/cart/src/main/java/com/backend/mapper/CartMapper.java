package com.backend.mapper;

import com.backend.dto.cart.CartHistoryResponseDTO;
import com.backend.dto.cart.CartItemResponseDTO;
import com.backend.dto.cart.CartResponseDTO;
import com.backend.entity.Cart;
import com.backend.entity.CartHistory;
import com.backend.entity.CartItem;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CartMapper {

    CartResponseDTO toCartResponseDTO(Cart cart);

    CartItemResponseDTO toCartItemResponseDTO(CartItem item);

    CartHistoryResponseDTO toCartHistoryResponseDTO(CartHistory history);
}
