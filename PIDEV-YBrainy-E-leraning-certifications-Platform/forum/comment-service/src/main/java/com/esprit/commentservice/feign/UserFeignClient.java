package com.esprit.commentservice.feign;

import com.esprit.commentservice.dto.AuthorDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "breadandbutteruser")
public interface UserFeignClient {

    @GetMapping("/api/users/internal/{userId}")
    AuthorDto getUser(@PathVariable Long userId);
}
