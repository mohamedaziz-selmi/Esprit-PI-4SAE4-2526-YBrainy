package com.backend.controller;

import com.backend.dto.cart.AddToCartDTO;
import com.backend.dto.cart.CartHistoryResponseDTO;
import com.backend.dto.cart.CartResponseDTO;
import com.backend.dto.cart.StripeCheckoutConfirmRequestDTO;
import com.backend.dto.cart.StripeCheckoutSessionResponseDTO;
import com.backend.service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
@CrossOrigin(originPatterns = "*")
public class CartController {

    private static final Long TEMP_USER_ID = 1L;

    private final CartService cartService;

    @GetMapping
    public ResponseEntity<CartResponseDTO> getCart() {
        return ResponseEntity.ok(cartService.getActiveCart(TEMP_USER_ID));
    }

    @PostMapping("/items")
    public ResponseEntity<CartResponseDTO> addItem(@Valid @RequestBody AddToCartDTO dto) {
        return ResponseEntity.ok(cartService.addItemToCart(TEMP_USER_ID, dto));
    }

    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<CartResponseDTO> removeItem(@PathVariable Long itemId) {
        return ResponseEntity.ok(cartService.removeItemFromCart(TEMP_USER_ID, itemId));
    }

    @DeleteMapping
    public ResponseEntity<CartResponseDTO> clearCart() {
        return ResponseEntity.ok(cartService.clearCart(TEMP_USER_ID));
    }

    @PostMapping("/checkout")
    public ResponseEntity<CartResponseDTO> checkout() {
        return ResponseEntity.ok(cartService.checkout(TEMP_USER_ID));
    }

    @PostMapping("/checkout/stripe-session")
    public ResponseEntity<StripeCheckoutSessionResponseDTO> createStripeCheckoutSession() {
        return ResponseEntity.ok(cartService.createStripeCheckoutSession(TEMP_USER_ID));
    }

    @PostMapping("/checkout/stripe-confirm")
    public ResponseEntity<CartResponseDTO> confirmStripeCheckout(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @Valid @RequestBody StripeCheckoutConfirmRequestDTO request) {
        return ResponseEntity.ok(cartService.confirmStripeCheckout(TEMP_USER_ID, authorization, request));
    }

    @GetMapping("/history")
    public ResponseEntity<List<CartHistoryResponseDTO>> getHistory() {
        return ResponseEntity.ok(cartService.getCartHistory(TEMP_USER_ID));
    }
}
