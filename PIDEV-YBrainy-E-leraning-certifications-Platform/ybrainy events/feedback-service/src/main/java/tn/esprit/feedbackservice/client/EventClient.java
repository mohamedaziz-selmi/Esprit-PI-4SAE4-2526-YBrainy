package tn.esprit.feedbackservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import tn.esprit.feedbackservice.dto.EventDto;

import java.util.Optional;

/**
 * Feign client that calls event-service.
 *
 * Used to enforce the business rule:
 *   "A student can only submit feedback AFTER the event has ended (statut = TERMINE)."
 */
@FeignClient(name = "event-service")
public interface EventClient {

    @GetMapping("/Event/get/{idEvent}")
    Optional<EventDto> findById(@PathVariable("idEvent") long idEvent);
}
