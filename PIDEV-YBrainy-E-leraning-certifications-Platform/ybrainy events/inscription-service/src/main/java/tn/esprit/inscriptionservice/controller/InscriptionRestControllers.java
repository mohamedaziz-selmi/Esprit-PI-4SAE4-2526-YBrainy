package tn.esprit.inscriptionservice.controller;

import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import tn.esprit.inscriptionservice.client.EventClient;
import tn.esprit.inscriptionservice.client.UserClient;
import tn.esprit.inscriptionservice.dto.EventDto;
import tn.esprit.inscriptionservice.dto.InscriptionCreateDto;
import tn.esprit.inscriptionservice.dto.AdminNotificationDto;
import tn.esprit.inscriptionservice.entity.Inscription;
import tn.esprit.inscriptionservice.entity.InscriptionStatut;
import tn.esprit.inscriptionservice.service.IInscriptionServices;

import java.util.List;

@RestController
@CrossOrigin(
        origins = {"http://localhost:4200", "http://127.0.0.1:4200"},
        allowCredentials = "true"
)
@AllArgsConstructor
@RequestMapping("/Inscription")
public class InscriptionRestControllers {

    private final IInscriptionServices inscriptionServices;
    private final UserClient userClient;
    private final EventClient eventClient;

    // ── Endpoints consumed by event-service (Feign) ─────────────────────────

    /** Called by event-service to create an inscription. */
    @PostMapping
    public Inscription createInscription(@RequestBody InscriptionCreateDto dto) {
        InscriptionStatut initialStatus;
        try {
            initialStatus = dto.initialStatus() == null || dto.initialStatus().isBlank()
                    ? InscriptionStatut.EN_ATTENTE
                    : InscriptionStatut.valueOf(dto.initialStatus().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid initial inscription status.");
        }
        return inscriptionServices.createInscription(dto.eventId(), dto.studentId(), initialStatus);
    }

    /** Called by event-service to guard against duplicate registrations. */
    @GetMapping("/exists")
    public boolean existsByStudentIdAndEventId(
            @RequestParam("studentId") long studentId,
            @RequestParam("eventId") long eventId) {
        return inscriptionServices.existsByStudentAndEvent(studentId, eventId);
    }

    @GetMapping("/confirmed")
    public boolean hasConfirmedInscription(
            @RequestParam("studentId") long studentId,
            @RequestParam("eventId") long eventId) {
        return inscriptionServices.hasConfirmedInscription(studentId, eventId);
    }

    /** Called by event-service to attach inscription count to an event. */
    @GetMapping("/count")
    public long countConfirmedByEventId(@RequestParam("eventId") long eventId) {
        return inscriptionServices.countConfirmedByEvent(eventId);
    }

    // ── Public endpoints ─────────────────────────────────────────────────────

    /**
     * Returns student IDs — proxies to user-service.
     * Falls back to all user IDs if no STUDENT-role users exist.
     */
    @GetMapping("/students-ids")
    public List<Long> getStudentIds() {
        List<Long> students = userClient.findIdsByRole("STUDENT");
        if (students != null && !students.isEmpty()) {
            return students;
        }
        return userClient.findAllIds();
    }

    /** Event IDs the given student is registered for. */
    @GetMapping("/student/{idStudent}/event-ids")
    public List<Long> getRegisteredEventIdsByStudent(
            @PathVariable("idStudent") long idStudent) {
        return inscriptionServices.getDistinctEventIdsByStudent(idStudent);
    }

    /** Statuses of each event the student is registered for. */
    @GetMapping("/student/{idStudent}/event-statuses")
    public List<StudentEventStatusDto> getEventStatusesByStudent(
            @PathVariable("idStudent") long idStudent) {
        return inscriptionServices.getEventStatusesByStudent(idStudent).stream()
                .map(i -> new StudentEventStatusDto(
                        i.getEventId(),
                        i.getStatut() != null ? i.getStatut().name() : ""
                ))
                .filter(dto -> dto.idEvent() > 0)
                .toList();
    }

    /** All inscriptions in EN_ATTENTE status, enriched with event name via Feign. */
    @GetMapping("/pending")
    public List<PendingInscriptionDto> getPendingInscriptions() {
        return inscriptionServices.getPendingInscriptions().stream()
                .map(i -> {
                    String eventName;
                    try {
                        eventName = eventClient.findById(i.getEventId())
                                .map(EventDto::name)
                                .orElse("");
                    } catch (Exception ignored) {
                        eventName = "";
                    }
                    return new PendingInscriptionDto(
                            i.getIdInscription(),
                            i.getStudentId(),
                            i.getEventId(),
                            eventName,
                            i.getDateInscription() != null ? i.getDateInscription().toString() : "",
                            i.getStatut() != null ? i.getStatut().name() : ""
                    );
                })
                .filter(dto -> dto.idInscription() > 0 && dto.idEvent() > 0 && dto.idStudent() > 0)
                .toList();
    }

    @GetMapping("/event/{idEvent}/waitlist")
    public List<WaitlistEntryDto> getWaitlistByEvent(@PathVariable("idEvent") long idEvent) {
        return inscriptionServices.getWaitlistByEvent(idEvent).stream()
                .map(i -> {
                    var student = userClient.findById(i.getStudentId()).orElse(null);
                    String studentName = student == null
                            ? "Student #" + i.getStudentId()
                            : buildStudentName(student);
                    String email = student != null && student.email() != null ? student.email() : "";
                    return new WaitlistEntryDto(
                            i.getIdInscription(),
                            i.getStudentId(),
                            studentName,
                            email,
                            i.getDateInscription() != null ? i.getDateInscription().toString() : "",
                            i.getStatut() != null ? i.getStatut().name() : ""
                    );
                })
                .toList();
    }

    @GetMapping("/admin-notifications")
    public List<AdminNotificationDto> getAdminNotifications() {
        return inscriptionServices.getAdminNotifications().stream()
                .map(n -> new AdminNotificationDto(
                        n.getIdNotification(),
                        n.getType(),
                        buildNotificationTitle(n),
                        buildNotificationMessage(n),
                        n.getEventId(),
                        n.getStudentId(),
                        n.getCreatedAt() != null ? n.getCreatedAt().toString() : ""
                ))
                .toList();
    }

    /** Update inscription status to CONFIRMEE or ANNULEE. */
    @PutMapping("/{idInscription}/status/{status}")
    public void updateInscriptionStatus(
            @PathVariable("idInscription") long idInscription,
            @PathVariable("status") String status) {
        InscriptionStatut targetStatus;
        try {
            targetStatus = InscriptionStatut.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid inscription status.");
        }
        inscriptionServices.updateStatus(idInscription, targetStatus);
    }

    @PutMapping("/student/{idStudent}/event/{idEvent}/cancel")
    public void cancelStudentRegistration(
            @PathVariable("idStudent") long idStudent,
            @PathVariable("idEvent") long idEvent) {
        inscriptionServices.cancelByStudentAndEvent(idStudent, idEvent);
    }

    // ── Response DTOs ────────────────────────────────────────────────────────

    public record PendingInscriptionDto(
            long idInscription,
            long idStudent,
            long idEvent,
            String eventName,
            String dateInscription,
            String statut
    ) {}

    public record StudentEventStatusDto(
            long idEvent,
            String statut
    ) {}

    public record WaitlistEntryDto(
            long idInscription,
            long idStudent,
            String studentName,
            String studentEmail,
            String dateInscription,
            String statut
    ) {}

    private String buildStudentName(tn.esprit.inscriptionservice.dto.UserDto student) {
        String fullName = ((student.firstName() != null ? student.firstName() : "") + " " +
                (student.lastName() != null ? student.lastName() : "")).trim();
        return fullName.isBlank()
                ? (student.username() != null ? student.username() : "Student #" + student.userId())
                : fullName;
    }

    private String buildNotificationTitle(tn.esprit.inscriptionservice.entity.AdminNotification notification) {
        if (notification.getTitle() != null && !notification.getTitle().isBlank()) {
            return notification.getTitle();
        }

        return switch (notification.getType() == null ? "" : notification.getType()) {
            case "EVENT_CREATED" -> "New event published";
            case "WAITLIST_PROMOTION" -> "Waitlist promoted";
            case "INSCRIPTION_CONFIRMED" -> "Inscription accepted";
            case "INSCRIPTION_REFUSED" -> "Inscription refused";
            default -> "Admin notification";
        };
    }

    private String buildNotificationMessage(tn.esprit.inscriptionservice.entity.AdminNotification notification) {
        if (notification.getMessage() != null && !notification.getMessage().isBlank()) {
            return notification.getMessage();
        }

        return switch (notification.getType() == null ? "" : notification.getType()) {
            case "EVENT_CREATED" -> "A new event is now available for registration.";
            case "WAITLIST_PROMOTION" -> buildStudentEventMessage(
                    notification,
                    "has been moved from the waitlist to confirmed registration for"
            );
            case "INSCRIPTION_CONFIRMED" -> buildStudentEventMessage(
                    notification,
                    "was accepted for"
            );
            case "INSCRIPTION_REFUSED" -> buildStudentEventMessage(
                    notification,
                    "was refused for"
            );
            default -> "A new notification is available.";
        };
    }

    private String buildStudentEventMessage(
            tn.esprit.inscriptionservice.entity.AdminNotification notification,
            String actionText
    ) {
        String studentLabel = "Student #" + notification.getStudentId();
        String eventLabel = "event #" + notification.getEventId();

        try {
            var student = userClient.findById(notification.getStudentId()).orElse(null);
            if (student != null) {
                studentLabel = buildStudentName(student);
            }
        } catch (Exception ignored) {
            // Keep the fallback label when the user service is unavailable.
        }

        try {
            eventLabel = eventClient.findById(notification.getEventId())
                    .map(EventDto::name)
                    .filter(name -> name != null && !name.isBlank())
                    .orElse(eventLabel);
        } catch (Exception ignored) {
            // Keep the fallback label when the event service is unavailable.
        }

        return studentLabel + " " + actionText + " " + eventLabel + ".";
    }
}

//import lombok.AllArgsConstructor;
//import org.springframework.http.HttpStatus;
//import org.springframework.web.bind.annotation.*;
//import org.springframework.web.server.ResponseStatusException;
//import tn.esprit.inscriptionservice.client.EventClient;
//import tn.esprit.inscriptionservice.client.UserClient;
//import tn.esprit.inscriptionservice.dto.EventDto;
//import tn.esprit.inscriptionservice.dto.InscriptionCreateDto;
//import tn.esprit.inscriptionservice.entity.Inscription;
//import tn.esprit.inscriptionservice.entity.InscriptionStatut;
//import tn.esprit.inscriptionservice.service.IInscriptionServices;
//
//import java.util.List;
//
//@RestController
//@CrossOrigin(
//        origins = {"http://localhost:4200", "http://127.0.0.1:4200"},
//        allowCredentials = "true"
//)
//@AllArgsConstructor
//@RequestMapping("/Inscription")
//public class InscriptionRestControllers {
//
//    private final IInscriptionServices inscriptionServices;
//    private final UserClient userClient;
//    private final EventClient eventClient;
//
//    // ── Endpoints consumed by event-service (Feign) ─────────────────────────
//
//    /** Called by event-service to create an inscription. */
//    @PostMapping
//    public Inscription createInscription(@RequestBody InscriptionCreateDto dto) {
//        return inscriptionServices.createInscription(dto.eventId(), dto.studentId());
//    }
//
//    /** Called by event-service to guard against duplicate registrations. */
//    @GetMapping("/exists")
//    public boolean existsByStudentIdAndEventId(
//            @RequestParam("studentId") long studentId,
//            @RequestParam("eventId") long eventId) {
//        return inscriptionServices.existsByStudentAndEvent(studentId, eventId);
//    }
//
//    /** Called by event-service to attach inscription count to an event. */
//    @GetMapping("/count")
//    public long countConfirmedByEventId(@RequestParam("eventId") long eventId) {
//        return inscriptionServices.countConfirmedByEvent(eventId);
//    }
//
//    // ── Public endpoints ─────────────────────────────────────────────────────
//
//    /**
//     * Returns student IDs — proxies to user-service.
//     * Falls back to all user IDs if no STUDENT-role users exist.
//     */
//    @GetMapping("/students-ids")
//    public List<Long> getStudentIds() {
//        List<Long> students = userClient.findIdsByRole("STUDENT");
//        if (students != null && !students.isEmpty()) {
//            return students;
//        }
//        return userClient.findAllIds();
//    }
//
//    /** Event IDs the given student is registered for. */
//    @GetMapping("/student/{idStudent}/event-ids")
//    public List<Long> getRegisteredEventIdsByStudent(
//            @PathVariable("idStudent") long idStudent) {
//        return inscriptionServices.getDistinctEventIdsByStudent(idStudent);
//    }
//
//    /** Statuses of each event the student is registered for. */
//    @GetMapping("/student/{idStudent}/event-statuses")
//    public List<StudentEventStatusDto> getEventStatusesByStudent(
//            @PathVariable("idStudent") long idStudent) {
//        return inscriptionServices.getEventStatusesByStudent(idStudent).stream()
//                .map(i -> new StudentEventStatusDto(
//                        i.getEventId(),
//                        i.getStatut() != null ? i.getStatut().name() : ""
//                ))
//                .filter(dto -> dto.idEvent() > 0)
//                .toList();
//    }
//
//    /** All inscriptions in EN_ATTENTE status, enriched with event name via Feign. */
//    @GetMapping("/pending")
//    public List<PendingInscriptionDto> getPendingInscriptions() {
//        return inscriptionServices.getPendingInscriptions().stream()
//                .map(i -> {
//                    String eventName = eventClient.findById(i.getEventId())
//                            .map(EventDto::name)
//                            .orElse("");
//                    return new PendingInscriptionDto(
//                            i.getIdInscription(),
//                            i.getStudentId(),
//                            i.getEventId(),
//                            eventName,
//                            i.getDateInscription() != null ? i.getDateInscription().toString() : ""
//                    );
//                })
//                .filter(dto -> dto.idInscription() > 0 && dto.idEvent() > 0 && dto.idStudent() > 0)
//                .toList();
//    }
//
//    /** Update inscription status to CONFIRMEE or ANNULEE. */
//    @PutMapping("/{idInscription}/status/{status}")
//    public void updateInscriptionStatus(
//            @PathVariable("idInscription") long idInscription,
//            @PathVariable("status") String status) {
//        InscriptionStatut targetStatus;
//        try {
//            targetStatus = InscriptionStatut.valueOf(status.toUpperCase());
//        } catch (IllegalArgumentException ex) {
//            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid inscription status.");
//        }
//        inscriptionServices.updateStatus(idInscription, targetStatus);
//    }
//
//    // ── Response DTOs ────────────────────────────────────────────────────────
//
//    public record PendingInscriptionDto(
//            long idInscription,
//            long idStudent,
//            long idEvent,
//            String eventName,
//            String dateInscription
//    ) {}
//
//    public record StudentEventStatusDto(
//            long idEvent,
//            String statut
//    ) {}
//}
