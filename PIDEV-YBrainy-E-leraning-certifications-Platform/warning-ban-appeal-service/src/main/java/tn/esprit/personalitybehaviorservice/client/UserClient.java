package tn.esprit.warningbanappealservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(
        name = "${services.user.name:breadandbutteruser}",
        url = "${services.user.url:http://localhost:8899}",
        path = "/api/users/internal",
        contextId = "warningBanAppealUserClient"
)
public interface UserClient {

    @GetMapping("/{id}")
    UserDto getUserById(@PathVariable("id") Long id);

    record UserDto(
            Long userId,
            String username,
            String email,
            String firstName,
            String lastName
    ) {}
}
