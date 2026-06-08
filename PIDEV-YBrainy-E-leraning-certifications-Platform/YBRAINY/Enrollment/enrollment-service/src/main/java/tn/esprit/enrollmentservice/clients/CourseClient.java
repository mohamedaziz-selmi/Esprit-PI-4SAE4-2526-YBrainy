package tn.esprit.enrollmentservice.clients;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@FeignClient(name = "course-service")
public interface CourseClient {

    @GetMapping("/api/courses/{id}/exists")
    boolean courseExists(@PathVariable Long id);

    @GetMapping("/api/courses/{id}")
    Map<String, Object> getCourse(@PathVariable Long id);
}
