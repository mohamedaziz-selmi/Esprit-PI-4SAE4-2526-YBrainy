package com.backend.dto.packconversion;

import java.time.Instant;
import java.util.List;

public record PackConversionSummaryResponseDTO(
        String sourceFile,
        Instant generatedAt,
        String assumption,
        String coursesFile,
        String skillTrendsFile,
        ModelMetricsDTO modelMetrics,
        PackConversionScoreDTO champion,
        List<PackConversionScoreDTO> topConversionPacks
) {
    public record ModelMetricsDTO(
            String algorithm,
            Integer randomSeed,
            Integer userCount,
            Integer trainingRows,
            Integer testRows,
            Double positiveRate,
            Double accuracy,
            Double rocAuc
    ) {
    }

    public record PackConversionScoreDTO(
            Integer packId,
            String title,
            Integer categoryId,
            String categoryName,
            String level,
            String primarySkill,
            Double salePrice,
            Double originalPrice,
            Double discountPct,
            Integer durationHours,
            Double marketDemandScore,
            Double marketAvgRoi,
            Integer marketCourseCount,
            Double avgViews,
            Double avgCartAdds,
            Double observedPurchaseRate,
            Double predictedConversionRate,
            Double expectedBuyers,
            Integer trainingSupport,
            Double expectedRevenue,
            Integer conversionRank
    ) {
    }
}
