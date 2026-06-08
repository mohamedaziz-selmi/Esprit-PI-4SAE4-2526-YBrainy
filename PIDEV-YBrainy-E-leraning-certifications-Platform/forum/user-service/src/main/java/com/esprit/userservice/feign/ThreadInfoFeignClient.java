package com.esprit.userservice.feign;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "thread-service", contextId = "threadInfoClient")
public interface ThreadInfoFeignClient {

    @GetMapping("/api/threads/{threadId}")
    ThreadInfo getThread(@PathVariable Long threadId);

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Getter @Setter
    class ThreadInfo {
        private Long id;
        private String title;
        private Long authorId;
    }
}
