package com.backend.dto.recommendation;

import java.time.Instant;
import java.util.List;

public record RecommendationSummaryResponseDTO(
        String sourceFile,
        Instant generatedAt,
        ForecastContextDTO forecastContext,
        List<ExecutiveMetricDTO> executiveMetrics,
        List<RecommendationItemDTO> topRecommendations,
        List<ImprovementActionDTO> improvementActions,
        List<UserPlaybookStepDTO> userPlaybook
) {
    public record ForecastContextDTO(
            Double marginPct,
            Double marginTrendPct,
            String topRevenueSource,
            String watchCategory,
            Double watchGrowthPct,
            ForecastHorizonDTO forecastHorizon
    ) {
    }

    public record ForecastHorizonDTO(
            String from,
            String to,
            Integer monthsAhead,
            Double incomeTotal,
            Double expensesTotal,
            Double profitTotal
    ) {
    }

    public record RecommendationItemDTO(
            Integer recommendationRank,
            String title,
            String platform,
            String skillCategory,
            Double hybridFinalScore,
            String confidenceLevel,
            String impactBand,
            String recommendationReason,
            String url,
            String month,
            Double marginBefore,
            Double marginAfter,
            Double profitBefore,
            Double profitAfter,
            Double profitLowBefore,
            Double profitLowAfter,
            String actionKey,
            String targetMetric,
            String priority,
            String how,
            String expectedOutcome,
            String successSignal
    ) {
    }

    public record ImprovementActionDTO(
            String priority,
            String action,
            String rationale,
            String targetMetric
    ) {
    }

    public record ExecutiveMetricDTO(
            String metric,
            String value,
            String notes,
            String status
    ) {
    }

    public record UserPlaybookStepDTO(
            Integer step,
            String window,
            String priority,
            String action,
            String why,
            String how,
            String expectedImpact,
            String successSignal,
            String targetMetric
    ) {
    }
}
