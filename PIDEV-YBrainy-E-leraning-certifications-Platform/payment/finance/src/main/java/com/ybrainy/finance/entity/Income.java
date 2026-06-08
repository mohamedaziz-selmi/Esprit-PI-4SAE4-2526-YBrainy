package com.ybrainy.finance.entity;

import com.ybrainy.finance.entity.enums.PaymentMethod;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "income")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Income {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String sourceType; // PACK_PURCHASE or MANUAL

    @Column(nullable = true)
    private Long referenceId; // PaymentOrder ID (optional for manual)

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private Double amount;

    @Column(nullable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    private PaymentMethod paymentMethod;

    @Column(nullable = false)
    private LocalDateTime receivedDate;

    @CreationTimestamp
    private LocalDateTime createdAt;
}