package com.backend.entity;


import com.backend.entity.enums.PaymentMethod;
import com.backend.entity.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "payment_orders")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PaymentOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String orderReference;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long cartId;

    @Column(nullable = false)
    private Double totalAmount;

    @Column(nullable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus paymentStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod paymentMethod;

    @OneToMany(mappedBy = "paymentOrder", cascade = CascadeType.ALL)
    private List<PaymentTransaction> transactions;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
