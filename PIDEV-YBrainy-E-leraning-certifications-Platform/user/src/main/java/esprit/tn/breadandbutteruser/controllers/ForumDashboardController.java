package esprit.tn.breadandbutteruser.controllers;

import esprit.tn.breadandbutteruser.dto.forum.UserDashboardResponse;
import esprit.tn.breadandbutteruser.services.ForumDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class ForumDashboardController {

    private final ForumDashboardService dashboardService;

    @GetMapping("/{userId}")
    public ResponseEntity<UserDashboardResponse> getDashboard(@PathVariable Long userId) {
        return ResponseEntity.ok(dashboardService.getDashboard(userId));
    }

    @GetMapping("/{userId}/xp-timeline")
    public ResponseEntity<List<UserDashboardResponse.XpDataPointDto>> getXpTimeline(@PathVariable Long userId) {
        return ResponseEntity.ok(dashboardService.getXpTimeline(userId));
    }

    @GetMapping("/{userId}/activity")
    public ResponseEntity<List<UserDashboardResponse.DayActivityDto>> getActivity(@PathVariable Long userId) {
        return ResponseEntity.ok(dashboardService.getActivity(userId));
    }
}
