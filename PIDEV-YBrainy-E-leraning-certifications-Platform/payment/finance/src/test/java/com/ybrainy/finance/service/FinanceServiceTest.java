package com.ybrainy.finance.service;

import com.ybrainy.finance.dto.finance.*;
import com.ybrainy.finance.entity.Expense;
import com.ybrainy.finance.entity.Income;
import com.ybrainy.finance.entity.enums.ExpenseCategory;
import com.ybrainy.finance.entity.enums.ExpenseStatus;
import com.ybrainy.finance.entity.enums.PaymentMethod;
import com.ybrainy.finance.exception.ResourceNotFoundException;
import com.ybrainy.finance.mapper.FinanceMapper;
import com.ybrainy.finance.repository.ExpenseRepository;
import com.ybrainy.finance.repository.IncomeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FinanceServiceTest {

    @Mock IncomeRepository incomeRepository;
    @Mock ExpenseRepository expenseRepository;
    @Mock FinanceMapper financeMapper;
    @InjectMocks FinanceService service;

    private Income sampleIncome() {
        Income i = new Income();
        i.setId(1L);
        i.setSourceType("PACK_PURCHASE");
        i.setDescription("Course pack purchase");
        i.setAmount(99.0);
        i.setCurrency("USD");
        i.setPaymentMethod(PaymentMethod.STRIPE);
        i.setReceivedDate(LocalDateTime.now());
        return i;
    }

    private Expense sampleExpense() {
        Expense e = new Expense();
        e.setId(1L);
        e.setDescription("Server hosting");
        e.setAmount(50.0);
        e.setCategory(ExpenseCategory.INFRASTRUCTURE);
        e.setStatus(ExpenseStatus.PAID);
        return e;
    }

    private IncomeResponseDTO sampleIncomeResponse() {
        IncomeResponseDTO dto = new IncomeResponseDTO();
        dto.setId(1L);
        dto.setAmount(99.0);
        dto.setCurrency("USD");
        return dto;
    }

    private ExpenseResponseDTO sampleExpenseResponse() {
        ExpenseResponseDTO dto = new ExpenseResponseDTO();
        dto.setId(1L);
        dto.setAmount(50.0);
        return dto;
    }

    /* ─── Income Tests ─── */

    @Test
    @DisplayName("createIncome maps DTO, saves, and returns response")
    void createIncome_savesAndReturnsResponse() {
        CreateIncomeDTO dto = new CreateIncomeDTO();
        dto.setAmount(99.0);
        Income entity = sampleIncome();
        IncomeResponseDTO response = sampleIncomeResponse();

        when(financeMapper.toIncomeEntity(dto)).thenReturn(entity);
        when(incomeRepository.save(entity)).thenReturn(entity);
        when(financeMapper.toIncomeResponseDTO(entity)).thenReturn(response);

        IncomeResponseDTO result = service.createIncome(dto);

        assertThat(result.getAmount()).isEqualTo(99.0);
        verify(incomeRepository).save(entity);
    }

    @Test
    @DisplayName("updateIncome patches existing entity and persists changes")
    void updateIncome_existingId_updatesAndReturns() {
        Income entity = sampleIncome();
        UpdateIncomeDTO dto = new UpdateIncomeDTO();
        dto.setAmount(150.0);
        IncomeResponseDTO response = sampleIncomeResponse();
        response.setAmount(150.0);

        when(incomeRepository.findById(1L)).thenReturn(Optional.of(entity));
        doNothing().when(financeMapper).updateIncomeEntity(dto, entity);
        when(incomeRepository.save(entity)).thenReturn(entity);
        when(financeMapper.toIncomeResponseDTO(entity)).thenReturn(response);

        IncomeResponseDTO result = service.updateIncome(1L, dto);

        assertThat(result.getAmount()).isEqualTo(150.0);
        verify(financeMapper).updateIncomeEntity(dto, entity);
        verify(incomeRepository).save(entity);
    }

    @Test
    @DisplayName("updateIncome throws ResourceNotFoundException for unknown id")
    void updateIncome_notFound_throws() {
        when(incomeRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateIncome(999L, new UpdateIncomeDTO()))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("999");
    }

    @Test
    @DisplayName("deleteIncome removes existing income by id")
    void deleteIncome_existingId_deletes() {
        when(incomeRepository.existsById(1L)).thenReturn(true);

        service.deleteIncome(1L);

        verify(incomeRepository).deleteById(1L);
    }

    @Test
    @DisplayName("deleteIncome throws ResourceNotFoundException for unknown id")
    void deleteIncome_notFound_throws() {
        when(incomeRepository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> service.deleteIncome(999L))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("999");

        verify(incomeRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("getAllIncomes returns mapped list of all incomes")
    void getAllIncomes_returnsMappedList() {
        Income income = sampleIncome();
        IncomeResponseDTO response = sampleIncomeResponse();

        when(incomeRepository.findAll()).thenReturn(List.of(income, income));
        when(financeMapper.toIncomeResponseDTO(income)).thenReturn(response);

        List<IncomeResponseDTO> result = service.getAllIncomes();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo(1L);
    }

    /* ─── Expense Tests ─── */

    @Test
    @DisplayName("createExpense maps DTO, saves, and returns response")
    void createExpense_savesAndReturnsResponse() {
        CreateExpenseDTO dto = new CreateExpenseDTO();
        dto.setAmount(50.0);
        Expense entity = sampleExpense();
        ExpenseResponseDTO response = sampleExpenseResponse();

        when(financeMapper.toExpenseEntity(dto)).thenReturn(entity);
        when(expenseRepository.save(entity)).thenReturn(entity);
        when(financeMapper.toExpenseResponseDTO(entity)).thenReturn(response);

        ExpenseResponseDTO result = service.createExpense(dto);

        assertThat(result.getAmount()).isEqualTo(50.0);
        verify(expenseRepository).save(entity);
    }

    @Test
    @DisplayName("updateExpense patches existing entity and persists")
    void updateExpense_existingId_updatesAndReturns() {
        Expense entity = sampleExpense();
        UpdateExpenseDTO dto = new UpdateExpenseDTO();
        dto.setAmount(75.0);
        ExpenseResponseDTO response = sampleExpenseResponse();
        response.setAmount(75.0);

        when(expenseRepository.findById(1L)).thenReturn(Optional.of(entity));
        doNothing().when(financeMapper).updateExpenseEntity(dto, entity);
        when(expenseRepository.save(entity)).thenReturn(entity);
        when(financeMapper.toExpenseResponseDTO(entity)).thenReturn(response);

        ExpenseResponseDTO result = service.updateExpense(1L, dto);

        assertThat(result.getAmount()).isEqualTo(75.0);
        verify(financeMapper).updateExpenseEntity(dto, entity);
    }

    @Test
    @DisplayName("updateExpense throws ResourceNotFoundException for unknown id")
    void updateExpense_notFound_throws() {
        when(expenseRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateExpense(999L, new UpdateExpenseDTO()))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("999");
    }

    @Test
    @DisplayName("deleteExpense removes existing expense by id")
    void deleteExpense_existingId_deletes() {
        when(expenseRepository.existsById(1L)).thenReturn(true);

        service.deleteExpense(1L);

        verify(expenseRepository).deleteById(1L);
    }

    @Test
    @DisplayName("deleteExpense throws ResourceNotFoundException for unknown id")
    void deleteExpense_notFound_throws() {
        when(expenseRepository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> service.deleteExpense(999L))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("999");

        verify(expenseRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("getAllExpenses returns mapped list of all expenses")
    void getAllExpenses_returnsMappedList() {
        Expense expense = sampleExpense();
        ExpenseResponseDTO response = sampleExpenseResponse();

        when(expenseRepository.findAll()).thenReturn(List.of(expense));
        when(financeMapper.toExpenseResponseDTO(expense)).thenReturn(response);

        List<ExpenseResponseDTO> result = service.getAllExpenses();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(1L);
    }
}
