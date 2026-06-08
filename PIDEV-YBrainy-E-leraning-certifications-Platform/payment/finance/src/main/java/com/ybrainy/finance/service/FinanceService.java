package com.ybrainy.finance.service;

import com.ybrainy.finance.dto.finance.*;
import com.backend.events.PaymentCompletedEvent;
import com.ybrainy.finance.entity.Expense;
import com.ybrainy.finance.entity.Income;
import com.ybrainy.finance.entity.enums.PaymentMethod;
import com.ybrainy.finance.exception.ResourceNotFoundException;
import com.ybrainy.finance.mapper.FinanceMapper;
import com.ybrainy.finance.repository.ExpenseRepository;
import com.ybrainy.finance.repository.IncomeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class FinanceService {

    private final IncomeRepository incomeRepository;
    private final ExpenseRepository expenseRepository;
    private final FinanceMapper financeMapper;

    /* ─── Income CRUD ─── */
    public IncomeResponseDTO createIncome(CreateIncomeDTO dto) {
        Income entity = financeMapper.toIncomeEntity(dto);
        Income saved = incomeRepository.save(entity);
        return financeMapper.toIncomeResponseDTO(saved);
    }

    public IncomeResponseDTO updateIncome(Long id, UpdateIncomeDTO dto) {
        Income entity = incomeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Income not found with id: " + id));
        financeMapper.updateIncomeEntity(dto, entity);
        Income updated = incomeRepository.save(entity);
        return financeMapper.toIncomeResponseDTO(updated);
    }

    public void deleteIncome(Long id) {
        if (!incomeRepository.existsById(id)) {
            throw new ResourceNotFoundException("Income not found with id: " + id);
        }
        incomeRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<IncomeResponseDTO> getAllIncomes() {
        return incomeRepository.findAll().stream()
                .map(financeMapper::toIncomeResponseDTO)
                .collect(Collectors.toList());
    }

    /* ─── Expense CRUD ─── */
    public IncomeResponseDTO recordPaymentIncome(PaymentCompletedEvent event) {
        Income entity = Income.builder()
                .sourceType("PACK_PURCHASE")
                .referenceId(event.getCartId())
                .description(buildPaymentDescription(event))
                .amount(event.getTotalAmount())
                .currency(event.getCurrency() == null ? "USD" : event.getCurrency().toUpperCase())
                .paymentMethod(resolvePaymentMethod(event.getPaymentMethod()))
                .receivedDate(event.getPaidAt() == null ? LocalDateTime.now() : event.getPaidAt())
                .build();
        Income saved = incomeRepository.save(entity);
        return financeMapper.toIncomeResponseDTO(saved);
    }

    private PaymentMethod resolvePaymentMethod(String value) {
        if (value == null || value.isBlank()) {
            return PaymentMethod.STRIPE;
        }
        try {
            return PaymentMethod.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return PaymentMethod.STRIPE;
        }
    }

    private String buildPaymentDescription(PaymentCompletedEvent event) {
        String itemSummary = event.getItems() == null || event.getItems().isEmpty()
                ? "no items"
                : event.getItems().stream()
                        .map(item -> item.getPackTitle() + " x" + item.getQuantity())
                        .collect(Collectors.joining(", "));
        return "Stripe checkout " + event.getStripeSessionId() + " for cart " + event.getCartId() + ": " + itemSummary;
    }

    public ExpenseResponseDTO createExpense(CreateExpenseDTO dto) {
        Expense entity = financeMapper.toExpenseEntity(dto);
        Expense saved = expenseRepository.save(entity);
        return financeMapper.toExpenseResponseDTO(saved);
    }

    public ExpenseResponseDTO updateExpense(Long id, UpdateExpenseDTO dto) {
        Expense entity = expenseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Expense not found with id: " + id));
        financeMapper.updateExpenseEntity(dto, entity);
        Expense updated = expenseRepository.save(entity);
        return financeMapper.toExpenseResponseDTO(updated);
    }

    public void deleteExpense(Long id) {
        if (!expenseRepository.existsById(id)) {
            throw new ResourceNotFoundException("Expense not found with id: " + id);
        }
        expenseRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<ExpenseResponseDTO> getAllExpenses() {
        return expenseRepository.findAll().stream()
                .map(financeMapper::toExpenseResponseDTO)
                .collect(Collectors.toList());
    }
}