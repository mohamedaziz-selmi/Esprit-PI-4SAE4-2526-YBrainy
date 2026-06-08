package tn.esprit.tpfoyer.Controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.tpfoyer.Dto.CourseAnalyticsDTO;
import tn.esprit.tpfoyer.Services.ICourseService;

/**
 * REST controller for instructor-specific analytics endpoints.
 * Provides course performance and engagement metrics.
 */
@RestController
@RequestMapping("/api/instructor")
@RequiredArgsConstructor
@Tag(name = "Instructor Analytics", description = "Endpoints for course analytics and instructor dashboard")
public class InstructorAnalyticsController {

    private final ICourseService courseService;

    /**
     * Get analytics data for a specific course.
     * Requires INSTRUCTOR or ADMIN role.
     */
    @GetMapping("/courses/{courseId}/analytics")
    @Operation(
        summary = "Get course analytics",
        description = "Returns comprehensive analytics data for a course including enrollments, progress, and engagement metrics"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Analytics retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Course not found"),
        @ApiResponse(responseCode = "403", description = "Access denied - requires INSTRUCTOR or ADMIN role")
    })
    public ResponseEntity<CourseAnalyticsDTO> getCourseAnalytics(
            @Parameter(description = "Course ID", required = true)
            @PathVariable Long courseId) {
        
        CourseAnalyticsDTO analytics = courseService.getCourseAnalytics(courseId);
        return ResponseEntity.ok(analytics);
    }
}
