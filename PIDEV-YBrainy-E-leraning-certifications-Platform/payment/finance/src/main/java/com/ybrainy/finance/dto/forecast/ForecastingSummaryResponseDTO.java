package com.ybrainy.finance.dto.forecast;

import java.time.Instant;
import java.util.List;

public record ForecastingSummaryResponseDTO(
        String sourceFile,
        Instant generatedAt,
        ExecutiveScorecardDTO executiveScorecard,
        NextMonthForecastDTO nextMonthForecast,
        List<MonthlyForecastDTO> monthlyForecast,
        List<ExpenseCategoryForecastDTO> nextMonthExpenseByCategory,
        String plainLanguageSummary
) {
    public record ExecutiveScorecardDTO(
            Double incomeTotal,
            Double expensesTotal,
            Double profitTotal,
            Double marginPct,
            String riskLevel
    ) {
    }

    public record NextMonthForecastDTO(
            String month,
            Double income,
            Double incomeLow,
            Double incomeHigh,
            Double expenses,
            Double expensesLow,
            Double expensesHigh,
            Double profit,
            Double profitLow,
            Double profitHigh,
            Double margin,
            Double marginLow,
            Double marginHigh
    ) {
    }

    public record MonthlyForecastDTO(
            String month,
            Double income,
            Double expenses,
            Double profit,
            Double margin
    ) {
    }

    public record ExpenseCategoryForecastDTO(
            String category,
            Double amount
    ) {
    }
}