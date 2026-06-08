package com.backend.service;

import com.backend.client.PaymentServiceClient;
import com.backend.dto.cart.AddToCartDTO;
import com.backend.dto.cart.CartHistoryResponseDTO;
import com.backend.dto.cart.CartResponseDTO;
import com.backend.dto.cart.StripeCheckoutConfirmRequestDTO;
import com.backend.dto.cart.StripeCheckoutSessionResponseDTO;
import com.backend.dto.payment.PaymentPackResponseDTO;
import com.backend.dto.payment.PaymentStripeCheckoutItemRequestDTO;
import com.backend.dto.payment.PaymentStripeCheckoutSessionRequestDTO;
import com.backend.dto.payment.PaymentStripeCheckoutVerificationRequestDTO;
import com.backend.entity.Cart;
import com.backend.entity.CartHistory;
import com.backend.events.PaymentCompletedEvent;
import com.backend.entity.CartItem;
import com.backend.entity.enums.CartAction;
import com.backend.entity.enums.CartStatus;
import com.backend.exception.BusinessRuleException;
import com.backend.exception.ResourceNotFoundException;
import com.backend.mapper.CartMapper;
import com.backend.messaging.PaymentEventPublisher;
import com.backend.repository.CartHistoryRepository;
import com.backend.repository.CartRepository;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class CartService {

    private final CartRepository cartRepository;
    private final CartHistoryRepository cartHistoryRepository;
    private final CartMapper cartMapper;
    private final PaymentServiceClient paymentServiceClient;
    private final CheckoutEmailService checkoutEmailService;
    private final CheckoutIdentityService checkoutIdentityService;
    private final PaymentEventPublisher paymentEventPublisher;

    public CartResponseDTO getActiveCart(Long userId) {
        log.info("Fetching active cart for user: {}", userId);
        Cart cart = syncLegacyPackSnapshots(getOrCreateActiveCart(userId));
        CartResponseDTO dto = cartMapper.toCartResponseDTO(cart);
        log.info("Active cart for user {} has {} items", userId, dto.getItems() != null ? dto.getItems().size() : 0);
        return dto;
    }

    public CartResponseDTO addItemToCart(Long userId, AddToCartDTO dto) {
        log.info("Adding pack {} to cart for user {}", dto.getPackId(), userId);
        PaymentPackResponseDTO pack = fetchPackOrThrow(dto.getPackId());
        Cart cart = getOrCreateActiveCart(userId);
        if (cart.getItems() == null) {
            cart.setItems(new ArrayList<>());
        }

        Optional<CartItem> existingItem = cart.getItems().stream()
                .filter(item -> item.getPackId().equals(dto.getPackId()))
                .findFirst();

        int quantity = dto.getQuantity();
        if (existingItem.isPresent()) {
            CartItem item = existingItem.get();
            item.setQuantity(item.getQuantity() + dto.getQuantity());
            item.setPackTitle(pack.getTitle());
            item.setPriceAtPurchase(pack.getSalePrice());
            item.setSubtotal(item.getQuantity() * item.getPriceAtPurchase());
            log.info("Updated quantity for pack {} in cart", dto.getPackId());
        } else {
            CartItem newItem = CartItem.builder()
                    .cart(cart)
                    .packId(pack.getId())
                    .packTitle(pack.getTitle())
                    .priceAtPurchase(pack.getSalePrice())
                    .quantity(dto.getQuantity())
                    .subtotal(pack.getSalePrice() * dto.getQuantity())
                    .build();
            cart.getItems().add(newItem);
            log.info("Added new pack {} to cart", dto.getPackId());
        }

        updateCartTotal(cart);
        Cart saved = cartRepository.save(cart);

        Long cartItemId = saved.getItems().stream()
                .filter(item -> item.getPackId().equals(dto.getPackId()))
                .map(CartItem::getId)
                .findFirst()
                .orElse(null);

        logAction(userId, saved.getId(), cartItemId, CartAction.ADD_ITEM, pack.getTitle(), quantity,
                saved.getTotalAmount(), saved.getStatus(), "Added " + quantity + " x " + pack.getTitle() + " to cart");

        return cartMapper.toCartResponseDTO(saved);
    }

    public CartResponseDTO removeItemFromCart(Long userId, Long itemId) {
        log.info("Removing item {} from cart for user {}", itemId, userId);
        Cart cart = getOrCreateActiveCart(userId);

        Optional<CartItem> itemToRemove = cart.getItems().stream()
                .filter(item -> item.getId().equals(itemId))
                .findFirst();

        if (itemToRemove.isPresent()) {
            CartItem item = itemToRemove.get();
            Long cartItemId = item.getId();
            String title = item.getPackTitle();
            int qty = item.getQuantity();

            cart.getItems().remove(item);
            updateCartTotal(cart);
            Cart saved = cartRepository.save(cart);

            logAction(userId, saved.getId(), cartItemId, CartAction.REMOVE_ITEM, title, qty, saved.getTotalAmount(),
                    saved.getStatus(), "Removed " + title + " from cart");
            return cartMapper.toCartResponseDTO(saved);
        }

        return cartMapper.toCartResponseDTO(cart);
    }

    public CartResponseDTO clearCart(Long userId) {
        log.info("Clearing cart for user {}", userId);
        Cart cart = getOrCreateActiveCart(userId);
        cart.getItems().clear();
        cart.setTotalAmount(0.0);
        Cart saved = cartRepository.save(cart);
        logAction(userId, saved.getId(), null, CartAction.CLEAR_CART, null, null, 0.0, saved.getStatus(),
                "Cleared all items from cart");
        return cartMapper.toCartResponseDTO(saved);
    }

    public CartResponseDTO checkout(Long userId) {
        log.info("Processing checkout for user {}", userId);
        Cart cart = getOrCreateActiveCart(userId);
        if (cart.getItems().isEmpty()) {
            log.warn("Attempted checkout with empty cart for user {}", userId);
            throw new BusinessRuleException("Cannot checkout an empty cart");
        }

        cart.setStatus(CartStatus.CHECKED_OUT);
        Cart saved = cartRepository.save(cart);

        logAction(userId, saved.getId(), null, CartAction.CHECKOUT, null, null, saved.getTotalAmount(),
                saved.getStatus(), "Checked out cart");

        log.info("Checkout successful for user: {}", userId);
        return cartMapper.toCartResponseDTO(saved);
    }

    @Transactional(readOnly = true)
    public StripeCheckoutSessionResponseDTO createStripeCheckoutSession(Long userId) {
        Cart cart = syncLegacyPackSnapshots(getOrCreateActiveCart(userId));
        if (cart.getItems() == null || cart.getItems().isEmpty()) {
            throw new BusinessRuleException("Cannot checkout an empty cart");
        }

        try {
            return paymentServiceClient.createStripeCheckoutSession(
                    PaymentStripeCheckoutSessionRequestDTO.builder()
                            .userId(userId)
                            .cartId(cart.getId())
                            .items(cart.getItems().stream()
                                    .map(this::toPaymentCheckoutItem)
                                    .collect(Collectors.toList()))
                            .build());
        } catch (FeignException ex) {
            throw new BusinessRuleException("Unable to start checkout right now.");
        }
    }

    public CartResponseDTO confirmStripeCheckout(Long userId, String authorization,
                                                 StripeCheckoutConfirmRequestDTO request) {
        if (!isCheckoutSessionPaid(request.getSessionId())) {
            throw new BusinessRuleException("Stripe payment is not completed.");
        }

        CartResponseDTO activeCart = getActiveCart(userId);
        if (activeCart.getItems() == null || activeCart.getItems().isEmpty()) {
            return activeCart;
        }

        CartResponseDTO checkedOutCart = checkout(userId);
        paymentEventPublisher.publishPaymentCompleted(toPaymentCompletedEvent(checkedOutCart, request.getSessionId()));
        String recipientEmail = checkoutIdentityService.resolveEmailFromBearerOrDefault(authorization);
        checkoutEmailService.sendCheckoutReceipt(recipientEmail, checkedOutCart);
        return checkedOutCart;
    }

    @Transactional(readOnly = true)
    public List<CartHistoryResponseDTO> getCartHistory(Long userId) {
        log.info("Fetching cart history for user {}", userId);
        return cartHistoryRepository.findByUserId(userId).stream()
                .map(cartMapper::toCartHistoryResponseDTO)
                .collect(Collectors.toList());
    }

    private PaymentCompletedEvent toPaymentCompletedEvent(CartResponseDTO cart, String sessionId) {
        return PaymentCompletedEvent.builder()
                .cartId(cart.getId())
                .userId(cart.getUserId())
                .totalAmount(cart.getTotalAmount())
                .currency("usd")
                .paymentMethod("STRIPE")
                .stripeSessionId(sessionId)
                .paidAt(LocalDateTime.now())
                .items(cart.getItems().stream()
                        .map(item -> PaymentCompletedEvent.Item.builder()
                                .packId(item.getPackId())
                                .packTitle(item.getPackTitle())
                                .quantity(item.getQuantity())
                                .price(item.getPriceAtPurchase())
                                .subtotal(item.getSubtotal())
                                .build())
                        .collect(Collectors.toList()))
                .build();
    }
    private boolean isCheckoutSessionPaid(String sessionId) {
        try {
            PaymentStripeCheckoutVerificationRequestDTO request = new PaymentStripeCheckoutVerificationRequestDTO();
            request.setSessionId(sessionId);
            return paymentServiceClient.verifyStripeCheckout(request).isPaid();
        } catch (FeignException ex) {
            throw new BusinessRuleException("Unable to verify Stripe payment right now.");
        }
    }

    private PaymentPackResponseDTO fetchPackOrThrow(Long packId) {
        try {
            return paymentServiceClient.getActivePackById(packId);
        } catch (FeignException.NotFound ex) {
            throw new ResourceNotFoundException("Pack not found");
        } catch (FeignException ex) {
            throw new BusinessRuleException("Unable to validate this pack right now.");
        }
    }

    private Cart syncLegacyPackSnapshots(Cart cart) {
        boolean changed = false;
        for (CartItem item : cart.getItems()) {
            if (item.getPackId() == null) {
                continue;
            }
            if (item.getPackTitle() != null && !item.getPackTitle().isBlank() && item.getPriceAtPurchase() != null) {
                continue;
            }

            try {
                PaymentPackResponseDTO pack = paymentServiceClient.getActivePackById(item.getPackId());
                if (item.getPackTitle() == null || item.getPackTitle().isBlank()) {
                    item.setPackTitle(pack.getTitle());
                    changed = true;
                }
                if (item.getPriceAtPurchase() == null && pack.getSalePrice() != null) {
                    item.setPriceAtPurchase(pack.getSalePrice());
                    item.setSubtotal(pack.getSalePrice() * item.getQuantity());
                    changed = true;
                }
            } catch (FeignException ex) {
                if (item.getPackTitle() == null || item.getPackTitle().isBlank()) {
                    item.setPackTitle("Pack #" + item.getPackId());
                    changed = true;
                }
            }
        }

        if (!changed) {
            return cart;
        }

        updateCartTotal(cart);
        return cartRepository.save(cart);
    }

    private void logAction(Long userId, Long cartId, Long cartItemId, CartAction action, String packTitle,
                           Integer quantity, Double total, CartStatus status, String description) {
        CartHistory history = CartHistory.builder()
                .userId(userId)
                .cartId(cartId)
                .cartItemId(cartItemId)
                .action(action)
                .packTitle(packTitle)
                .quantity(quantity)
                .totalAmount(total)
                .cartStatus(status)
                .description(description)
                .build();
        cartHistoryRepository.save(history);
    }

    private Cart getOrCreateActiveCart(Long userId) {
        return cartRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE)
                .orElseGet(() -> {
                    log.info("Creating new ACTIVE cart for user: {}", userId);
                    Cart newCart = Cart.builder()
                            .userId(userId)
                            .status(CartStatus.ACTIVE)
                            .totalAmount(0.0)
                            .items(new ArrayList<>())
                            .build();
                    Cart saved = cartRepository.save(newCart);
                    logAction(userId, saved.getId(), null, CartAction.CART_CREATED, null, null, 0.0,
                            saved.getStatus(), "Initialized new active cart");
                    return saved;
                });
    }

    private PaymentStripeCheckoutItemRequestDTO toPaymentCheckoutItem(CartItem item) {
        return PaymentStripeCheckoutItemRequestDTO.builder()
                .packId(item.getPackId())
                .packTitle(item.getPackTitle())
                .priceAtPurchase(item.getPriceAtPurchase())
                .quantity(item.getQuantity())
                .build();
    }

    private void updateCartTotal(Cart cart) {
        double total = cart.getItems().stream()
                .mapToDouble(CartItem::getSubtotal)
                .sum();
        cart.setTotalAmount(total);
    }
}
