package com.esprit.threadservice.feign;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@FeignClient(name = "post-service")
public interface PostFeignClient {

    @GetMapping("/api/posts/thread/{threadId}")
    List<PostSummary> getPostsByThread(@PathVariable Long threadId);

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Getter @Setter
    class PostSummary {
        private Long id;
        private String body;
        private boolean bestAnswer;
        private AuthorInfo author;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Getter @Setter
    class AuthorInfo {
        private String username;
    }
}
