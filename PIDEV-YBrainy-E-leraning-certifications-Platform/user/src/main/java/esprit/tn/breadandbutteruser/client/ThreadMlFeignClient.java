package esprit.tn.breadandbutteruser.client;

import esprit.tn.breadandbutteruser.dto.forum.MlQualityStatsDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "thread-service", contextId = "threadMlUserClient")
public interface ThreadMlFeignClient {

    @GetMapping("/api/ai/ml-quality/user/{userId}")
    MlQualityStatsDto getMlQualityForUser(@PathVariable Long userId);
}
