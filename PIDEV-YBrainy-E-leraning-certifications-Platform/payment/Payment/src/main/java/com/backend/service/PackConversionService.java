package com.backend.service;

import com.backend.dto.packconversion.PackConversionSummaryResponseDTO;
import com.backend.exception.BusinessRuleException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PackConversionService {

    private final ObjectMapper objectMapper;

    @Value("${pack-conversion.output-directory:../Conversion packs/outputs}")
    private String packConversionOutputDirectory;

    @Value("${pack-conversion.summary-file-name:pack_conversion_summary.json}")
    private String packConversionSummaryFileName;

    public PackConversionSummaryResponseDTO getSummary(int limit) {
        if (limit <= 0) {
            throw new BusinessRuleException("Limit must be greater than zero.");
        }

        Path summaryPath = resolveSummaryPath();
        JsonNode root = readSummaryJson(summaryPath);
        List<PackConversionSummaryResponseDTO.PackConversionScoreDTO> topPacks =
                parseTopConversionPacks(root.path("top_conversion_packs"), limit);

        return new PackConversionSummaryResponseDTO(
                summaryPath.toString(),
                parseGeneratedAt(textOrNull(root, "generated_at"), summaryPath),
                textOrNull(root, "assumption"),
                textOrNull(root.path("scraper_inputs"), "courses_file"),
                textOrNull(root.path("scraper_inputs"), "skill_trends_file"),
                parseModelMetrics(root.path("model")),
                topPacks.isEmpty() ? null : topPacks.get(0),
                topPacks
        );
    }

    private Path resolveSummaryPath() {
        Path outputDir = resolveOutputDirectory();
        List<String> candidateFiles = List.of(
                packConversionSummaryFileName,
                "pack_conversion_summary.json"
        );

        for (String fileName : candidateFiles) {
            Path summaryPath = outputDir.resolve(fileName).normalize();
            if (Files.isRegularFile(summaryPath)) {
                return summaryPath;
            }
        }

        throw new BusinessRuleException("Pack conversion summary file not found in: " + outputDir);
    }

    private Path resolveOutputDirectory() {
        Path workingDir = Paths.get("").toAbsolutePath().normalize();
        List<Path> candidates = List.of(
                Paths.get(packConversionOutputDirectory),
                Paths.get(packConversionOutputDirectory).toAbsolutePath(),
                workingDir.resolve("..").resolve("Conversion packs").resolve("outputs"),
                workingDir.resolve("Conversion packs").resolve("outputs")
        );

        for (Path candidate : candidates) {
            Path normalized = candidate.toAbsolutePath().normalize();
            if (Files.isDirectory(normalized)) {
                return normalized;
            }
        }

        throw new BusinessRuleException("Pack conversion output directory not found. Checked: " + candidates);
    }

    private JsonNode readSummaryJson(Path summaryPath) {
        try {
            return objectMapper.readTree(Files.newInputStream(summaryPath));
        } catch (IOException ex) {
            throw new BusinessRuleException("Failed to read pack conversion summary: " + ex.getMessage());
        }
    }

    private Instant parseGeneratedAt(String value, Path sourcePath) {
        if (value == null || value.isBlank()) {
            return fileModifiedAt(sourcePath);
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDateTime.parse(value).atZone(ZoneId.systemDefault()).toInstant();
        } catch (DateTimeParseException ignored) {
        }
        return fileModifiedAt(sourcePath);
    }

    private Instant fileModifiedAt(Path filePath) {
        try {
            return Files.getLastModifiedTime(filePath).toInstant();
        } catch (IOException ex) {
            return Instant.now();
        }
    }

    private PackConversionSummaryResponseDTO.ModelMetricsDTO parseModelMetrics(JsonNode node) {
        return new PackConversionSummaryResponseDTO.ModelMetricsDTO(
                textOrNull(node, "algorithm"),
                intOrNull(node, "random_seed"),
                intOrNull(node, "user_count"),
                intOrNull(node, "training_rows"),
                intOrNull(node, "test_rows"),
                doubleOrNull(node, "positive_rate"),
                doubleOrNull(node, "accuracy"),
                doubleOrNull(node, "roc_auc")
        );
    }

    private List<PackConversionSummaryResponseDTO.PackConversionScoreDTO> parseTopConversionPacks(JsonNode node, int limit) {
        List<PackConversionSummaryResponseDTO.PackConversionScoreDTO> items = new ArrayList<>();
        if (!node.isArray()) {
            return items;
        }

        int count = 0;
        for (JsonNode item : node) {
            if (count++ >= limit) {
                break;
            }
            items.add(new PackConversionSummaryResponseDTO.PackConversionScoreDTO(
                    intOrNull(item, "pack_id"),
                    textOrNull(item, "title"),
                    intOrNull(item, "category_id"),
                    textOrNull(item, "category_name"),
                    textOrNull(item, "level"),
                    textOrNull(item, "primary_skill"),
                    doubleOrNull(item, "sale_price"),
                    doubleOrNull(item, "original_price"),
                    doubleOrNull(item, "discount_pct"),
                    intOrNull(item, "duration_hours"),
                    doubleOrNull(item, "market_demand_score"),
                    doubleOrNull(item, "market_avg_roi"),
                    intOrNull(item, "market_course_count"),
                    doubleOrNull(item, "avg_views"),
                    doubleOrNull(item, "avg_cart_adds"),
                    doubleOrNull(item, "observed_purchase_rate"),
                    doubleOrNull(item, "predicted_conversion_rate"),
                    doubleOrNull(item, "expected_buyers"),
                    intOrNull(item, "training_support"),
                    doubleOrNull(item, "expected_revenue"),
                    intOrNull(item, "conversion_rank")
            ));
        }
        return items;
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
