package tn.esprit.inscriptionservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import tn.esprit.inscriptionservice.dto.EventDto;

import java.util.Optional;

/**
 * Feign client that fetches event details from event-service.
 * Used to enrich pending-inscription responses with event names.
 */
@FeignClient(name = "event-service")
public interface EventClient {

    @GetMapping("/Event/get/{idEvent}")
    Optional<EventDto> findById(@PathVariable("idEvent") long idEvent);
}
