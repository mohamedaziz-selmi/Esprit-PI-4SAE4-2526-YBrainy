package esprit.tn.breadandbutteruser.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@FeignClient(name = "course-service", contextId = "courseServiceClient")
public interface CourseServiceClient {

    @GetMapping("/api/courses")
    Map<String, Object> getCourses(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size);
}
