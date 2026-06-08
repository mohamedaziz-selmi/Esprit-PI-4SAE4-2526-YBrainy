package esprit.tn.breadandbutteruser.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "thread-service", contextId = "threadInfoUserClient")
public interface ThreadInfoFeignClient {

    @GetMapping("/api/threads/{threadId}")
    ThreadInfo getThread(@PathVariable Long threadId);

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    class ThreadInfo {
        private Long id;
        private String title;
        private Long authorId;
    }
}
