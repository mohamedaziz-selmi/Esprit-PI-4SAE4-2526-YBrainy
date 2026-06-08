package com.backend.entity;


import com.backend.entity.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "payment_transactions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String transactionReference;

    private String providerReference;

    @Column(nullable = false)
    private Double amount;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    @Column(columnDefinition = "TEXT")
    private String providerResponse;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_order_id", nullable = false)
    private PaymentOrder paymentOrder;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
