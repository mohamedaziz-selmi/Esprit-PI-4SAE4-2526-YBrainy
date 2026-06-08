package com.ybrainy.finance.controller;

import com.ybrainy.finance.dto.finance.*;
import com.ybrainy.finance.dto.forecast.ForecastingSummaryResponseDTO;
import com.ybrainy.finance.service.FinanceScraperService;
import com.ybrainy.finance.service.FinanceService;
import com.ybrainy.finance.service.ForecastingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/finance")
@RequiredArgsConstructor
public class FinanceController {

    private final FinanceService financeService;
    private final FinanceScraperService financeScraperService;
    private final ForecastingService forecastingService;

    /* ─── Income Endpoints ─── */
    @PostMapping("/incomes")
    public ResponseEntity<IncomeResponseDTO> createIncome(@Valid @RequestBody CreateIncomeDTO dto) {
        return new ResponseEntity<>(financeService.createIncome(dto), HttpStatus.CREATED);
    }

    @PutMapping("/incomes/{id}")
    public ResponseEntity<IncomeResponseDTO> updateIncome(@PathVariable Long id,
            @Valid @RequestBody UpdateIncomeDTO dto) {
        return ResponseEntity.ok(financeService.updateIncome(id, dto));
    }

    @DeleteMapping("/incomes/{id}")
    public ResponseEntity<Void> deleteIncome(@PathVariable Long id) {
        financeService.deleteIncome(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/incomes")
    public ResponseEntity<List<IncomeResponseDTO>> getAllIncomes() {
        return ResponseEntity.ok(financeService.getAllIncomes());
    }

    /* ─── Expense Endpoints ─── */
    @PostMapping("/expenses")
    public ResponseEntity<ExpenseResponseDTO> createExpense(@Valid @RequestBody CreateExpenseDTO dto) {
        return new ResponseEntity<>(financeService.createExpense(dto), HttpStatus.CREATED);
    }

    @PutMapping("/expenses/{id}")
    public ResponseEntity<ExpenseResponseDTO> updateExpense(@PathVariable Long id,
            @Valid @RequestBody UpdateExpenseDTO dto) {
        return ResponseEntity.ok(financeService.updateExpense(id, dto));
    }

    @DeleteMapping("/expenses/{id}")
    public ResponseEntity<Void> deleteExpense(@PathVariable Long id) {
        financeService.deleteExpense(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/expenses")
    public ResponseEntity<List<ExpenseResponseDTO>> getAllExpenses() {
        return ResponseEntity.ok(financeService.getAllExpenses());
    }

    @GetMapping("/scraper/status")
    public ResponseEntity<FinanceScraperService.ScraperRunStatus> getScraperStatus() {
        return ResponseEntity.ok(financeScraperService.getStatus());
    }

    @PostMapping("/scraper/run")
    public ResponseEntity<FinanceScraperService.ScraperRunStatus> runScraper() {
        FinanceScraperService.ScraperRunStatus status = financeScraperService.startScraper();
        if ("already_running".equals(status.state())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(status);
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(status);
    }

    @GetMapping("/forecast/summary")
    public ResponseEntity<ForecastingSummaryResponseDTO> getForecastSummary() {
        return ResponseEntity.ok(forecastingService.getSummary());
    }
}