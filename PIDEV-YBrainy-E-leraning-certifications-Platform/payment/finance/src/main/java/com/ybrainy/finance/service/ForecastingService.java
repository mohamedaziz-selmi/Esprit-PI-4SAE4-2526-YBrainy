package com.ybrainy.finance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ybrainy.finance.dto.forecast.ForecastingSummaryResponseDTO;
import com.ybrainy.finance.exception.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ForecastingService {

    private final ObjectMapper objectMapper;

    @Value("${forecasting.output-directory:../forcasting/outputs}")
    private String forecastingOutputDirectory;

    @Value("${forecasting.dashboard-file-name:dashboard_data.json}")
    private String dashboardFileName;

    @Value("${forecasting.scorecard-file-name:forecast_executive_scorecard.csv}")
    private String scorecardFileName;

    @Value("${forecasting.summary-file-name:plain_language_summary.txt}")
    private String summaryFileName;

    public ForecastingSummaryResponseDTO getSummary() {
        Path outputDir = resolveOutputDirectory();
        Path dashboardPath = resolveRequiredFile(outputDir, dashboardFileName);

        JsonNode root = readDashboardJson(dashboardPath);
        ScorecardMetrics scorecard = readScorecard(resolveOptionalFile(outputDir, scorecardFileName));
        String plainSummary = readPlainSummary(resolveOptionalFile(outputDir, summaryFileName));

        JsonNode nextMonthNode = root.path("next_month_forecast");
        ForecastingSummaryResponseDTO.NextMonthForecastDTO nextMonthForecast =
                new ForecastingSummaryResponseDTO.NextMonthForecastDTO(
                        textOrNull(nextMonthNode, "month"),
                        doubleOrNull(nextMonthNode, "income"),
                        doubleOrNull(nextMonthNode, "income_low"),
                        doubleOrNull(nextMonthNode, "income_high"),
                        doubleOrNull(nextMonthNode, "expenses"),
                        doubleOrNull(nextMonthNode, "expenses_low"),
                        doubleOrNull(nextMonthNode, "expenses_high"),
                        doubleOrNull(nextMonthNode, "profit"),
                        doubleOrNull(nextMonthNode, "profit_low"),
                        doubleOrNull(nextMonthNode, "profit_high"),
                        doubleOrNull(nextMonthNode, "margin"),
                        doubleOrNull(nextMonthNode, "margin_low"),
                        doubleOrNull(nextMonthNode, "margin_high")
                );

        return new ForecastingSummaryResponseDTO(
                dashboardPath.toString(),
                fileModifiedAt(dashboardPath),
                new ForecastingSummaryResponseDTO.ExecutiveScorecardDTO(
                        scorecard.incomeTotal(),
                        scorecard.expensesTotal(),
                        scorecard.profitTotal(),
                        scorecard.marginPct(),
                        scorecard.riskLevel()
                ),
                nextMonthForecast,
                parseMonthlyForecast(root.path("forecast_monthly")),
                parseExpenseCategories(nextMonthNode.path("expenses_by_category")),
                plainSummary
        );
    }

    private Path resolveOutputDirectory() {
        Path workingDir = Paths.get("").toAbsolutePath().normalize();
        List<Path> candidates = List.of(
                Paths.get(forecastingOutputDirectory),
                Paths.get(forecastingOutputDirectory).toAbsolutePath(),
                workingDir.resolve("..").resolve("forcasting").resolve("outputs"),
                workingDir.resolve("forcasting").resolve("outputs")
        );

        for (Path candidate : candidates) {
            Path normalized = candidate.toAbsolutePath().normalize();
            if (Files.isDirectory(normalized)) {
                return normalized;
            }
        }

        throw new BusinessRuleException("Forecast output directory not found. Checked: " + candidates);
    }

    private Path resolveRequiredFile(Path directory, String fileName) {
        Path filePath = directory.resolve(fileName).normalize();
        if (!Files.isRegularFile(filePath)) {
            throw new BusinessRuleException("Forecast file not found: " + filePath);
        }
        return filePath;
    }

    private Path resolveOptionalFile(Path directory, String fileName) {
        Path filePath = directory.resolve(fileName).normalize();
        return Files.isRegularFile(filePath) ? filePath : null;
    }

    private JsonNode readDashboardJson(Path dashboardPath) {
        try {
            return objectMapper.readTree(Files.newInputStream(dashboardPath));
        } catch (IOException ex) {
            throw new BusinessRuleException("Failed to read dashboard_data.json: " + ex.getMessage());
        }
    }

    private Instant fileModifiedAt(Path filePath) {
        try {
            return Files.getLastModifiedTime(filePath).toInstant();
        } catch (IOException ex) {
            return Instant.now();
        }
    }

    private List<ForecastingSummaryResponseDTO.MonthlyForecastDTO> parseMonthlyForecast(JsonNode node) {
        List<ForecastingSummaryResponseDTO.MonthlyForecastDTO> items = new ArrayList<>();
        if (!node.isObject()) {
            return items;
        }

        List<String> months = new ArrayList<>();
        node.fieldNames().forEachRemaining(months::add);
        months.sort(String::compareTo);

        for (String month : months) {
            JsonNode monthNode = node.path(month);
            items.add(new ForecastingSummaryResponseDTO.MonthlyForecastDTO(
                    month,
                    doubleOrNull(monthNode, "income"),
                    doubleOrNull(monthNode, "expenses"),
                    doubleOrNull(monthNode, "profit"),
                    doubleOrNull(monthNode, "margin")
            ));
        }
        return items;
    }

    private List<ForecastingSummaryResponseDTO.ExpenseCategoryForecastDTO> parseExpenseCategories(JsonNode node) {
        List<ForecastingSummaryResponseDTO.ExpenseCategoryForecastDTO> categories = new ArrayList<>();
        if (!node.isObject()) {
            return categories;
        }

        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            Double amount = entry.getValue().isNumber() ? entry.getValue().asDouble() : null;
            categories.add(new ForecastingSummaryResponseDTO.ExpenseCategoryForecastDTO(entry.getKey(), amount));
        }

        categories.sort(Comparator.comparing(
                ForecastingSummaryResponseDTO.ExpenseCategoryForecastDTO::amount,
                Comparator.nullsLast(Double::compareTo)
        ).reversed());

        return categories;
    }

    private ScorecardMetrics readScorecard(Path scorecardPath) {
        if (scorecardPath == null) {
            return new ScorecardMetrics(null, null, null, null, null);
        }

        Map<String, String> values = new HashMap<>();
        try (BufferedReader reader = Files.newBufferedReader(scorecardPath, StandardCharsets.UTF_8)) {
            reader.readLine();
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",", -1);
                if (parts.length >= 2) {
                    values.put(parts[0].trim(), parts[1].trim());
                }
            }
        } catch (IOException ex) {
            throw new BusinessRuleException("Failed to read forecast executive scorecard: " + ex.getMessage());
        }

        return new ScorecardMetrics(
                parseDouble(values.get("income_total")),
                parseDouble(values.get("expenses_total")),
                parseDouble(values.get("profit_total")),
                parseDouble(values.get("margin_pct")),
                values.get("risk_level")
        );
    }

    private String readPlainSummary(Path summaryPath) {
        if (summaryPath == null) {
            return null;
        }
        try {
            String text = Files.readString(summaryPath, StandardCharsets.UTF_8);
            if (text == null) {
                return null;
            }
            String normalized = text.trim();
            if (normalized.length() <= 5000) {
                return normalized;
            }
            return normalized.substring(0, 5000).trim() + "\n...";
        } catch (IOException ex) {
            throw new BusinessRuleException("Failed to read forecast summary text: " + ex.getMessage());
        }
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText(null);
    }

    private Double doubleOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.asDouble() : null;
    }

    private Double parseDouble(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private record ScorecardMetrics(
            Double incomeTotal,
            Double expensesTotal,
            Double profitTotal,
            Double marginPct,
            String riskLevel
    ) {
    }
}
