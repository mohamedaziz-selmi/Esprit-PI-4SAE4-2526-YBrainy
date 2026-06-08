package com.ybrainy.finance.entity;

import com.ybrainy.finance.entity.enums.ExpenseCategory;
import com.ybrainy.finance.entity.enums.ExpenseStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "expenses")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    private ExpenseCategory category;

    @Column(nullable = false)
    private Double amount;

    @Column(nullable = false)
    private String currency;

    @Column(nullable = false)
    private LocalDateTime expenseDate;

    @Enumerated(EnumType.STRING)
    private ExpenseStatus status;

    @CreationTimestamp
    private LocalDateTime createdAt;
}