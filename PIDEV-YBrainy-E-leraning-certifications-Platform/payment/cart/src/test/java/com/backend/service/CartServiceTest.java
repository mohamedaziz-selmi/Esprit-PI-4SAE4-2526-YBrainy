package com.backend.service;

import com.backend.client.PaymentServiceClient;
import com.backend.dto.cart.AddToCartDTO;
import com.backend.dto.cart.CartHistoryResponseDTO;
import com.backend.dto.cart.CartItemResponseDTO;
import com.backend.dto.cart.CartResponseDTO;
import com.backend.dto.payment.PaymentPackResponseDTO;
import com.backend.entity.Cart;
import com.backend.entity.CartHistory;
import com.backend.entity.CartItem;
import com.backend.entity.enums.CartStatus;
import com.backend.exception.BusinessRuleException;
import com.backend.mapper.CartMapper;
import com.backend.messaging.PaymentEventPublisher;
import com.backend.repository.CartHistoryRepository;
import com.backend.repository.CartRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock CartRepository cartRepository;
    @Mock CartHistoryRepository cartHistoryRepository;
    @Mock CartMapper cartMapper;
    @Mock PaymentServiceClient paymentServiceClient;
    @Mock CheckoutEmailService checkoutEmailService;
    @Mock CheckoutIdentityService checkoutIdentityService;
    @Mock PaymentEventPublisher paymentEventPublisher;
    @InjectMocks CartService cartService;

    private Cart activeCartWithItems(Long userId) {
        CartItem item = CartItem.builder()
            .id(1L)
            .packId(10L)
            .packTitle("Pro Pack")
            .priceAtPurchase(99.0)
            .quantity(1)
            .subtotal(99.0)
            .build();
        Cart cart = Cart.builder()
            .id(1L)
            .userId(userId)
            .status(CartStatus.ACTIVE)
            .totalAmount(99.0)
            .items(new ArrayList<>(List.of(item)))
            .build();
        item.setCart(cart);
        return cart;
    }

    private Cart emptyActiveCart(Long userId) {
        return Cart.builder()
            .id(1L)
            .userId(userId)
            .status(CartStatus.ACTIVE)
            .totalAmount(0.0)
            .items(new ArrayList<>())
            .build();
    }

    private CartResponseDTO toResponse(Cart cart) {
        CartResponseDTO dto = new CartResponseDTO();
        dto.setId(cart.getId());
        dto.setUserId(cart.getUserId());
        dto.setStatus(cart.getStatus());
        dto.setTotalAmount(cart.getTotalAmount());
        dto.setItems(new ArrayList<>());
        return dto;
    }

    /* ─── getActiveCart ─── */

    @Test
    @DisplayName("getActiveCart creates a new ACTIVE cart when none exists")
    void getActiveCart_noExisting_createsNew() {
        when(cartRepository.findByUserIdAndStatus(1L, CartStatus.ACTIVE))
            .thenReturn(Optional.empty());
        Cart newCart = emptyActiveCart(1L);
        when(cartRepository.save(any())).thenReturn(newCart);
        when(cartHistoryRepository.save(any())).thenReturn(new CartHistory());
        CartResponseDTO response = toResponse(newCart);
        when(cartMapper.toCartResponseDTO(newCart)).thenReturn(response);

        CartResponseDTO result = cartService.getActiveCart(1L);

        assertThat(result.getStatus()).isEqualTo(CartStatus.ACTIVE);
        assertThat(result.getUserId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("getActiveCart returns existing ACTIVE cart")
    void getActiveCart_existing_returnsIt() {
        Cart existing = emptyActiveCart(1L);
        when(cartRepository.findByUserIdAndStatus(1L, CartStatus.ACTIVE))
            .thenReturn(Optional.of(existing));
        CartResponseDTO response = toResponse(existing);
        when(cartMapper.toCartResponseDTO(existing)).thenReturn(response);

        CartResponseDTO result = cartService.getActiveCart(1L);

        assertThat(result.getId()).isEqualTo(1L);
        verify(cartRepository, never()).save(any());
    }

    /* ─── addItemToCart ─── */

    @Test
    @DisplayName("addItemToCart adds a new item when pack is not already in cart")
    void addItemToCart_newItem_addsToCart() {
        Cart cart = emptyActiveCart(1L);
        PaymentPackResponseDTO pack = new PaymentPackResponseDTO();
        pack.setId(10L);
        pack.setTitle("Pro Pack");
        pack.setSalePrice(99.0);

        when(paymentServiceClient.getActivePackById(10L)).thenReturn(pack);
        when(cartRepository.findByUserIdAndStatus(1L, CartStatus.ACTIVE))
            .thenReturn(Optional.of(cart));
        when(cartRepository.save(cart)).thenAnswer(inv -> {
            Cart c = inv.getArgument(0);
            long seq = 1L;
            for (CartItem item : c.getItems()) {
                if (item.getId() == null) item.setId(seq++);
            }
            return c;
        });
        when(cartHistoryRepository.save(any())).thenReturn(new CartHistory());
        CartResponseDTO response = toResponse(cart);
        when(cartMapper.toCartResponseDTO(cart)).thenReturn(response);

        AddToCartDTO dto = new AddToCartDTO();
        dto.setPackId(10L);
        dto.setQuantity(1);

        cartService.addItemToCart(1L, dto);

        assertThat(cart.getItems()).hasSize(1);
        assertThat(cart.getTotalAmount()).isEqualTo(99.0);
    }

    @Test
    @DisplayName("addItemToCart increments quantity when pack already in cart")
    void addItemToCart_existingItem_incrementsQuantity() {
        Cart cart = activeCartWithItems(1L);
        PaymentPackResponseDTO pack = new PaymentPackResponseDTO();
        pack.setId(10L);
        pack.setTitle("Pro Pack");
        pack.setSalePrice(99.0);

        when(paymentServiceClient.getActivePackById(10L)).thenReturn(pack);
        when(cartRepository.findByUserIdAndStatus(1L, CartStatus.ACTIVE))
            .thenReturn(Optional.of(cart));
        when(cartRepository.save(cart)).thenReturn(cart);
        when(cartHistoryRepository.save(any())).thenReturn(new CartHistory());
        when(cartMapper.toCartResponseDTO(cart)).thenReturn(toResponse(cart));

        AddToCartDTO dto = new AddToCartDTO();
        dto.setPackId(10L);
        dto.setQuantity(2);

        cartService.addItemToCart(1L, dto);

        CartItem item = cart.getItems().get(0);
        assertThat(item.getQuantity()).isEqualTo(3);
    }

    /* ─── removeItemFromCart ─── */

    @Test
    @DisplayName("removeItemFromCart removes item and updates total")
    void removeItemFromCart_existing_removes() {
        Cart cart = activeCartWithItems(1L);
        when(cartRepository.findByUserIdAndStatus(1L, CartStatus.ACTIVE))
            .thenReturn(Optional.of(cart));
        when(cartRepository.save(cart)).thenReturn(cart);
        when(cartHistoryRepository.save(any())).thenReturn(new CartHistory());
        when(cartMapper.toCartResponseDTO(cart)).thenReturn(toResponse(cart));

        cartService.removeItemFromCart(1L, 1L);

        assertThat(cart.getItems()).isEmpty();
        assertThat(cart.getTotalAmount()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("removeItemFromCart with non-existent itemId returns cart unchanged")
    void removeItemFromCart_notFound_returnsCartUnchanged() {
        Cart cart = activeCartWithItems(1L);
        when(cartRepository.findByUserIdAndStatus(1L, CartStatus.ACTIVE))
            .thenReturn(Optional.of(cart));
        when(cartMapper.toCartResponseDTO(cart)).thenReturn(toResponse(cart));

        cartService.removeItemFromCart(1L, 999L);

        assertThat(cart.getItems()).hasSize(1);
        verify(cartRepository, never()).save(any());
    }

    /* ─── clearCart ─── */

    @Test
    @DisplayName("clearCart empties the items list and resets total to 0")
    void clearCart_withItems_clearsAll() {
        Cart cart = activeCartWithItems(1L);
        when(cartRepository.findByUserIdAndStatus(1L, CartStatus.ACTIVE))
            .thenReturn(Optional.of(cart));
        when(cartRepository.save(cart)).thenReturn(cart);
        when(cartHistoryRepository.save(any())).thenReturn(new CartHistory());
        when(cartMapper.toCartResponseDTO(cart)).thenReturn(toResponse(cart));

        cartService.clearCart(1L);

        assertThat(cart.getItems()).isEmpty();
        assertThat(cart.getTotalAmount()).isEqualTo(0.0);
    }

    /* ─── checkout ─── */

    @Test
    @DisplayName("checkout changes cart status to CHECKED_OUT")
    void checkout_withItems_marksCheckedOut() {
        Cart cart = activeCartWithItems(1L);
        when(cartRepository.findByUserIdAndStatus(1L, CartStatus.ACTIVE))
            .thenReturn(Optional.of(cart));
        when(cartRepository.save(cart)).thenReturn(cart);
        when(cartHistoryRepository.save(any())).thenReturn(new CartHistory());
        CartResponseDTO response = toResponse(cart);
        response.setStatus(CartStatus.CHECKED_OUT);
        when(cartMapper.toCartResponseDTO(cart)).thenReturn(response);

        CartResponseDTO result = cartService.checkout(1L);

        assertThat(cart.getStatus()).isEqualTo(CartStatus.CHECKED_OUT);
        assertThat(result.getStatus()).isEqualTo(CartStatus.CHECKED_OUT);
    }

    @Test
    @DisplayName("checkout throws BusinessRuleException when cart is empty")
    void checkout_emptyCart_throws() {
        Cart cart = emptyActiveCart(1L);
        when(cartRepository.findByUserIdAndStatus(1L, CartStatus.ACTIVE))
            .thenReturn(Optional.of(cart));

        assertThatThrownBy(() -> cartService.checkout(1L))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("empty");
    }

    /* ─── getCartHistory ─── */

    @Test
    @DisplayName("getCartHistory returns mapped list of history events")
    void getCartHistory_returnsMappedList() {
        CartHistory history = new CartHistory();
        CartHistoryResponseDTO dto = new CartHistoryResponseDTO();
        dto.setCartId(1L);

        when(cartHistoryRepository.findByUserId(1L)).thenReturn(List.of(history, history));
        when(cartMapper.toCartHistoryResponseDTO(history)).thenReturn(dto);

        List<CartHistoryResponseDTO> result = cartService.getCartHistory(1L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getCartId()).isEqualTo(1L);
    }
}
