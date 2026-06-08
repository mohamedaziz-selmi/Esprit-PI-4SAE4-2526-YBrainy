package tn.esprit.quizservice.clients;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@FeignClient(name = "breadandbutteruser")
public interface UserClient {

    @GetMapping("/api/users/internal/{id}")
    Map<String, Object> getUserById(@PathVariable Long id);
}
