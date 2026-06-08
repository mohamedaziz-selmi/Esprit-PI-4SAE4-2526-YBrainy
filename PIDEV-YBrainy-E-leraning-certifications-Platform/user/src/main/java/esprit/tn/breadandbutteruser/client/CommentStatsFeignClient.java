package esprit.tn.breadandbutteruser.client;

import esprit.tn.breadandbutteruser.dto.forum.CommentActivityDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "comment-service", contextId = "commentStatsUserClient")
public interface CommentStatsFeignClient {

    @GetMapping("/api/comments/stats/user/{userId}")
    CommentActivityDto getUserStats(@PathVariable Long userId);
}
