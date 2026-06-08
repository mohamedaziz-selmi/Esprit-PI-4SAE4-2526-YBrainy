package com.backend.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCompletedEvent {
    private Long cartId;
    private Long userId;
    private Double totalAmount;
    private String currency;
    private String paymentMethod;
    private String stripeSessionId;
    private LocalDateTime paidAt;
    private List<Item> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        private Long packId;
        private String packTitle;
        private Integer quantity;
        private Double price;
        private Double subtotal;
    }
}