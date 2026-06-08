package com.esprit.userservice.feign;

import com.esprit.userservice.dto.dashboard.CommentActivityDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "comment-service")
public interface CommentStatsFeignClient {

    @GetMapping("/api/comments/stats/user/{userId}")
    CommentActivityDto getUserStats(@PathVariable Long userId);
}
