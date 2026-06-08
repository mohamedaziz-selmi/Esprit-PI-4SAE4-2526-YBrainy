package esprit.tn.breadandbutteruser.client;

import esprit.tn.breadandbutteruser.dto.forum.PostActivityDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "post-service", contextId = "postStatsUserClient")
public interface PostStatsFeignClient {

    @GetMapping("/api/posts/stats/user/{userId}")
    PostActivityDto getUserStats(@PathVariable Long userId);
}
