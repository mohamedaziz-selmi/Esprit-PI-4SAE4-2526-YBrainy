package tn.esprit.eventservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import tn.esprit.eventservice.dto.InscriptionCreateDto;
import tn.esprit.eventservice.dto.InscriptionAssignmentResultDto;

/**
 * Feign client that delegates inscription operations to inscription-service.
 */
@FeignClient(name = "inscription-service")
public interface InscriptionClient {

    @PostMapping("/Inscription")
    InscriptionAssignmentResultDto createInscription(@RequestBody InscriptionCreateDto dto);

    @GetMapping("/Inscription/exists")
    boolean existsByStudentIdAndEventId(
            @RequestParam("studentId") long studentId,
            @RequestParam("eventId") long eventId
    );

    @GetMapping("/Inscription/count")
    long countConfirmedByEventId(@RequestParam("eventId") long eventId);

    @GetMapping("/Inscription/student/{idStudent}/event-ids")
    java.util.List<Long> getRegisteredEventIdsByStudent(@PathVariable("idStudent") long idStudent);
}