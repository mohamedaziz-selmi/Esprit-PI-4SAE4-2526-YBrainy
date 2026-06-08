package com.esprit.postservice.feign;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "thread-service")
public interface ThreadFeignClient {

    @GetMapping("/api/threads/{threadId}")
    ThreadTitleDto getThread(@PathVariable Long threadId);

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Getter @Setter
    class ThreadTitleDto {
        private Long id;
        private String title;
    }
}
