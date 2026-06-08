package com.esprit.userservice.feign;

import com.esprit.userservice.dto.dashboard.PostActivityDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "post-service")
public interface PostStatsFeignClient {

    @GetMapping("/api/posts/stats/user/{userId}")
    PostActivityDto getUserStats(@PathVariable Long userId);
}
