package tn.esprit.quizservice.clients;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "enrollment-service")
public interface EnrollmentClient {

    @GetMapping("/api/enrollments/exists")
    boolean isEnrolled(
        @RequestParam Long studentId,
        @RequestParam Long courseId);
}
