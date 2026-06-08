package tn.esprit.inscriptionservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import tn.esprit.inscriptionservice.dto.UserDto;

import java.util.List;
import java.util.Optional;

@FeignClient(name = "breadandbutteruser")
public interface UserClient {

    @GetMapping("/api/users/internal/{id}")
    Optional<UserDto> findById(@PathVariable("id") long id);

    @GetMapping("/api/users/internal/ids-by-role")
    List<Long> findIdsByRole(@RequestParam("role") String role);

    @GetMapping("/api/users/internal/ids")
    List<Long> findAllIds();
}
