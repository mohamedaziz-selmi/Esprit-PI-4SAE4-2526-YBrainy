package com.esprit.userservice.controller;

import com.esprit.userservice.dto.dashboard.DayActivityDto;
import com.esprit.userservice.dto.dashboard.UserDashboardResponse;
import com.esprit.userservice.dto.dashboard.XpDataPointDto;
import com.esprit.userservice.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/{userId}")
    public ResponseEntity<UserDashboardResponse> getDashboard(@PathVariable Long userId) {
        return ResponseEntity.ok(dashboardService.getDashboard(userId));
    }

    @GetMapping("/{userId}/xp-timeline")
    public ResponseEntity<List<XpDataPointDto>> getXpTimeline(@PathVariable Long userId) {
        return ResponseEntity.ok(dashboardService.getXpTimeline(userId));
    }

    @GetMapping("/{userId}/activity")
    public ResponseEntity<List<DayActivityDto>> getActivity(@PathVariable Long userId) {
        return ResponseEntity.ok(dashboardService.getActivity(userId));
    }
}
