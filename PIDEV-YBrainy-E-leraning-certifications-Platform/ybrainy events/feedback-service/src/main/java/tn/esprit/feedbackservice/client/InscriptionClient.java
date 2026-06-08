package tn.esprit.feedbackservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Feign client that calls inscription-service.
 *
 * Used to enforce the business rule:
 *   "A student can only submit feedback if his inscription
 *    for that event is CONFIRMEE."
 */
@FeignClient(name = "inscription-service")
public interface InscriptionClient {

    /**
     * Returns true if the student has a CONFIRMEE inscription for that event.
     * Maps to GET /Inscription/confirmed?studentId=&eventId= in inscription-service.
     */
    @GetMapping("/Inscription/confirmed")
    boolean hasConfirmedInscription(
            @RequestParam("studentId") long studentId,
            @RequestParam("eventId")   long eventId
    );
}
