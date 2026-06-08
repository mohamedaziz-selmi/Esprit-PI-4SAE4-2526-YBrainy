package tn.esprit.eventservice.dto;

import java.util.List;

public record EventAnalyticsResponseDto(
        TrendSeries trend,
        OverviewSeries overview
) {
    public record TrendSeries(
            String currentLabel,
            int currentTotal,
            String previousLabel,
            int previousTotal,
            List<String> labels,
            List<Integer> currentData,
            List<Integer> previousData
    ) {}

    public record OverviewSeries(
            String range,
            List<String> labels,
            List<Integer> eventCounts,
            List<Integer> registrations,
            List<Integer> activeEvents
    ) {}
}
