package tn.esprit.eventservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import tn.esprit.eventservice.dto.UserDto;

import java.util.Optional;

@FeignClient(name = "breadandbutteruser")
public interface UserClient {

    @GetMapping("/api/users/internal/{id}")
    Optional<UserDto> findById(@PathVariable("id") long id);

    @GetMapping("/api/users/internal/first-by-role")
    Optional<UserDto> findFirstByRole(@RequestParam("role") String role);

}
