package com.esprit.userservice.feign;

import com.esprit.userservice.dto.dashboard.ThreadUserStatsDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "thread-service", contextId = "threadStatsClient")
public interface ThreadStatsFeignClient {

    @GetMapping("/api/threads/stats/user/{userId}")
    ThreadUserStatsDto getUserStats(@PathVariable Long userId);
}
