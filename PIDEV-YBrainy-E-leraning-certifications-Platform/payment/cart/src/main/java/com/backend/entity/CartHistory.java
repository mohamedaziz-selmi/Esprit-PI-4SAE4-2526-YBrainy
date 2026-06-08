package com.backend.entity;

import com.backend.entity.enums.CartAction;
import com.backend.entity.enums.CartStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "cart_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private Long cartId;

    private Long cartItemId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = true)
    private CartAction action;

    private String packTitle;

    private Integer quantity;

    @Column(nullable = false)
    private Double totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = true)
    private CartStatus cartStatus;

    private String description;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
