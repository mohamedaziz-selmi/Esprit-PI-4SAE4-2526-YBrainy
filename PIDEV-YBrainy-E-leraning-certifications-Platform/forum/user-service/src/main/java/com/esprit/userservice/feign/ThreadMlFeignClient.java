package com.esprit.userservice.feign;

import com.esprit.userservice.dto.dashboard.MlQualityStatsDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "thread-service", contextId = "threadMlClient")
public interface ThreadMlFeignClient {

    @GetMapping("/api/ai/ml-quality/user/{userId}")
    MlQualityStatsDto getMlQualityForUser(@PathVariable Long userId);
}
