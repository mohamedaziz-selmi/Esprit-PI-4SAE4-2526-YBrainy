package com.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.backend.dto.recommendation.RecommendationSummaryResponseDTO;
import com.backend.exception.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final ObjectMapper objectMapper;

    @Value("${recommendations.output-directory:../forcasting recommendations/outputs}")
    private String recommendationsOutputDirectory;

    @Value("${recommendations.summary-file-name:financial_recommendations_summary.json}")
    private String recommendationsSummaryFileName;

    public RecommendationSummaryResponseDTO getSummary(int limit) {
        if (limit <= 0) {
            throw new BusinessRuleException("Limit must be greater than zero.");
        }

        Path summaryPath = resolveSummaryPath();
        JsonNode root = readSummaryJson(summaryPath);

        return new RecommendationSummaryResponseDTO(
                summaryPath.toString(),
                fileModifiedAt(summaryPath),
                parseForecastContext(root.path("forecast_context")),
                parseExecutiveMetrics(root.path("executive_metrics")),
                parseTopRecommendations(root.path("top_recommendations"), limit),
                parseImprovementActions(root.path("improvement_actions")),
                parseUserPlaybook(root.path("user_playbook"))
        );
    }

    private Path resolveSummaryPath() {
        Path outputDir = resolveOutputDirectory();
        List<String> candidateFiles = List.of(
                recommendationsSummaryFileName,
                "financial_recommendations_summary.json",
                "recommendations_hybrid_summary.json"
        );

        for (String fileName : candidateFiles) {
            Path summaryPath = outputDir.resolve(fileName).normalize();
            if (Files.isRegularFile(summaryPath)) {
                return summaryPath;
            }
        }

        throw new BusinessRuleException("Recommendations summary file not found in: " + outputDir);
    }

    private Path resolveOutputDirectory() {
        Path workingDir = Paths.get("").toAbsolutePath().normalize();
        List<Path> candidates = List.of(
                Paths.get(recommendationsOutputDirectory),
                Paths.get(recommendationsOutputDirectory).toAbsolutePath(),
                workingDir.resolve("..").resolve("forcasting recommendations").resolve("outputs"),
                workingDir.resolve("forcasting recommendations").resolve("outputs"),
                workingDir.resolve("..").resolve("Recommendations").resolve("Recommendations output"),
                workingDir.resolve("Recommendations").resolve("Recommendations output")
        );

        for (Path candidate : candidates) {
            Path normalized = candidate.toAbsolutePath().normalize();
            if (Files.isDirectory(normalized)) {
                return normalized;
            }
        }

        throw new BusinessRuleException("Recommendations output directory not found. Checked: " + candidates);
    }

    private JsonNode readSummaryJson(Path summaryPath) {
        try {
            return objectMapper.readTree(Files.newInputStream(summaryPath));
        } catch (IOException ex) {
            throw new BusinessRuleException("Failed to read recommendations summary: " + ex.getMessage());
        }
    }

    private Instant fileModifiedAt(Path filePath) {
        try {
            return Files.getLastModifiedTime(filePath).toInstant();
        } catch (IOException ex) {
            return Instant.now();
        }
    }

    private RecommendationSummaryResponseDTO.ForecastContextDTO parseForecastContext(JsonNode node) {
        JsonNode horizon = node.path("forecast_horizon");
        RecommendationSummaryResponseDTO.ForecastHorizonDTO horizonDTO =
                new RecommendationSummaryResponseDTO.ForecastHorizonDTO(
                        textOrNull(horizon, "from"),
                        textOrNull(horizon, "to"),
                        intOrNull(horizon, "months_ahead"),
                        doubleOrNull(horizon, "income_total"),
                        doubleOrNull(horizon, "expenses_total"),
                        doubleOrNull(horizon, "profit_total")
                );

        return new RecommendationSummaryResponseDTO.ForecastContextDTO(
                doubleOrNull(node, "margin_pct"),
                doubleOrNull(node, "margin_trend_pct"),
                textOrNull(node, "top_revenue_source"),
                textOrNull(node, "watch_category"),
                doubleOrNull(node, "watch_growth_pct"),
                horizonDTO
        );
    }

    private List<RecommendationSummaryResponseDTO.RecommendationItemDTO> parseTopRecommendations(JsonNode node, int limit) {
        List<RecommendationSummaryResponseDTO.RecommendationItemDTO> items = new ArrayList<>();
        if (!node.isArray()) {
            return items;
        }

        int count = 0;
        for (JsonNode item : node) {
            if (count++ >= limit) {
                break;
            }
            items.add(new RecommendationSummaryResponseDTO.RecommendationItemDTO(
                    intOrNull(item, "recommendation_rank"),
                    textOrNull(item, "title"),
                    textOrNull(item, "platform"),
                    textOrNull(item, "skill_category"),
                    doubleOrNull(item, "hybrid_final_score"),
                    textOrNull(item, "confidence_level"),
                    textOrNull(item, "impact_band"),
                    textOrNull(item, "recommendation_reason"),
                    textOrNull(item, "url"),
                    textOrNull(item, "month"),
                    doubleOrNull(item, "margin_before"),
                    doubleOrNull(item, "margin_after"),
                    doubleOrNull(item, "profit_before"),
                    doubleOrNull(item, "profit_after"),
                    doubleOrNull(item, "profit_low_before"),
                    doubleOrNull(item, "profit_low_after"),
                    textOrNull(item, "action_key"),
                    textOrNull(item, "target_metric"),
                    textOrNull(item, "priority"),
                    textOrNull(item, "how"),
                    textOrNull(item, "expected_outcome"),
                    textOrNull(item, "success_signal")
            ));
        }
        return items;
    }

    private List<RecommendationSummaryResponseDTO.ImprovementActionDTO> parseImprovementActions(JsonNode node) {
        List<RecommendationSummaryResponseDTO.ImprovementActionDTO> actions = new ArrayList<>();
        if (!node.isArray()) {
            return actions;
        }

        for (JsonNode item : node) {
            actions.add(new RecommendationSummaryResponseDTO.ImprovementActionDTO(
                    textOrNull(item, "priority"),
                    textOrNull(item, "action"),
                    textOrNull(item, "rationale"),
                    textOrNull(item, "target_metric")
            ));
        }
        return actions;
    }

    private List<RecommendationSummaryResponseDTO.ExecutiveMetricDTO> parseExecutiveMetrics(JsonNode node) {
        List<RecommendationSummaryResponseDTO.ExecutiveMetricDTO> metrics = new ArrayList<>();
        if (!node.isArray()) {
            return metrics;
        }

        for (JsonNode item : node) {
            metrics.add(new RecommendationSummaryResponseDTO.ExecutiveMetricDTO(
                    textOrNull(item, "metric"),
                    textOrNull(item, "value"),
                    textOrNull(item, "notes"),
                    textOrNull(item, "status")
            ));
        }
        return metrics;
    }

    private List<RecommendationSummaryResponseDTO.UserPlaybookStepDTO> parseUserPlaybook(JsonNode node) {
        List<RecommendationSummaryResponseDTO.UserPlaybookStepDTO> steps = new ArrayList<>();
        if (!node.isArray()) {
            return steps;
        }

        for (JsonNode item : node) {
            steps.add(new RecommendationSummaryResponseDTO.UserPlaybookStepDTO(
                    intOrNull(item, "step"),
                    textOrNull(item, "window"),
                    textOrNull(item, "priority"),
                    textOrNull(item, "action"),
                    textOrNull(item, "why"),
                    textOrNull(item, "how"),
                    textOrNull(item, "expected_impact"),
                    textOrNull(item, "success_signal"),
                    textOrNull(item, "target_metric")
            ));
        }
        return steps;
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText(null);
    }

    private Double doubleOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.asDouble() : null;
    }

    private Integer intOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isInt() || value.isLong() ? value.asInt() : null;
    }
}
