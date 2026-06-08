package esprit.tn.breadandbutteruser.client;

import esprit.tn.breadandbutteruser.dto.forum.ThreadUserStatsDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "thread-service", contextId = "threadStatsUserClient")
public interface ThreadStatsFeignClient {

    @GetMapping("/api/threads/stats/user/{userId}")
    ThreadUserStatsDto getUserStats(@PathVariable Long userId);
}
