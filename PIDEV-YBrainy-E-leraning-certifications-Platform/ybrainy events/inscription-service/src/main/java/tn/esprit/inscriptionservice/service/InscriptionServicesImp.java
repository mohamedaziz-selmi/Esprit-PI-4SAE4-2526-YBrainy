package tn.esprit.inscriptionservice.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tn.esprit.inscriptionservice.client.EventClient;
import tn.esprit.inscriptionservice.client.UserClient;
import tn.esprit.inscriptionservice.dto.EventDto;
import tn.esprit.inscriptionservice.dto.UserDto;
import tn.esprit.inscriptionservice.entity.AdminNotification;
import tn.esprit.inscriptionservice.entity.Inscription;
import tn.esprit.inscriptionservice.entity.InscriptionStatut;
import tn.esprit.inscriptionservice.messaging.InscriptionConfirmedPublisher;
import tn.esprit.inscriptionservice.repository.AdminNotificationRepository;
import tn.esprit.inscriptionservice.repository.InscriptionRepository;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Service
@AllArgsConstructor
@Slf4j
public class InscriptionServicesImp implements IInscriptionServices {

    private final InscriptionRepository inscriptionRepository;
    private final EventClient eventClient;
    private final UserClient userClient;
    private final InscriptionNotificationEmailService notificationEmailService;
    private final AdminNotificationRepository adminNotificationRepository;
    private final InscriptionConfirmedPublisher inscriptionConfirmedPublisher;

    @Override
    public Inscription createInscription(long eventId, long studentId, InscriptionStatut initialStatus) {
        if (inscriptionRepository.existsByStudentIdAndEventId(studentId, eventId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Student already registered for this event.");
        }
        Inscription inscription = new Inscription();
        inscription.setEventId(eventId);
        inscription.setStudentId(studentId);
        inscription.setDateInscription(LocalDateTime.now());
        inscription.setStatut(initialStatus == null ? InscriptionStatut.EN_ATTENTE : initialStatus);
        return inscriptionRepository.save(inscription);
    }

    @Override
    public void updateStatus(long idInscription, InscriptionStatut status) {
        Inscription inscription = inscriptionRepository.findById(idInscription)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Inscription not found."));

        if (status != InscriptionStatut.CONFIRMEE && status != InscriptionStatut.ANNULEE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Status must be CONFIRMEE or ANNULEE.");
        }
        InscriptionStatut previousStatus = inscription.getStatut();
        if (status == InscriptionStatut.CONFIRMEE && previousStatus != InscriptionStatut.CONFIRMEE) {
            EventDto event = eventClient.findById(inscription.getEventId()).orElse(null);
            long confirmedCount = inscriptionRepository.countByEventIdAndStatut(inscription.getEventId(), InscriptionStatut.CONFIRMEE);
            if (event != null && confirmedCount >= Math.max(0, event.capacite())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "This event is already full. New students must remain on the waiting list until a seat opens.");
            }
        }
        inscription.setStatut(status);
        inscription = inscriptionRepository.save(inscription);

        if (status == InscriptionStatut.CONFIRMEE && previousStatus != InscriptionStatut.CONFIRMEE) {
            inscriptionConfirmedPublisher.publish(inscription, "STATUS_UPDATE");
        }

        if (previousStatus != status) {
            createAdminDecisionNotification(inscription, status);
        }

        trySendDecisionEmail(inscription, status);
        if (previousStatus == InscriptionStatut.CONFIRMEE && status == InscriptionStatut.ANNULEE) {
            promoteFromWaitlist(inscription.getEventId());
        }
    }

    @Override
    public void cancelByStudentAndEvent(long studentId, long eventId) {
        Inscription inscription = inscriptionRepository.findByStudentIdAndEventId(studentId, eventId);
        if (inscription == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Registration not found.");
        }
        InscriptionStatut previousStatus = inscription.getStatut();
        inscriptionRepository.delete(inscription);

        if (previousStatus == InscriptionStatut.CONFIRMEE) {
            promoteFromWaitlist(eventId);
        }
    }

    @Override
    public List<Inscription> getPendingInscriptions() {
        return inscriptionRepository.findByStatutInOrderByDateInscriptionDesc(
                Arrays.asList(InscriptionStatut.EN_ATTENTE, InscriptionStatut.LISTE_ATTENTE)
        );
    }

    @Override
    public List<Inscription> getWaitlistByEvent(long eventId) {
        return inscriptionRepository.findByEventIdAndStatutOrderByDateInscriptionAsc(
                eventId,
                InscriptionStatut.LISTE_ATTENTE
        );
    }

    @Override
    public List<AdminNotification> getAdminNotifications() {
        return adminNotificationRepository.findTop10ByOrderByCreatedAtDesc();
    }

    @Override
    public List<Long> getDistinctEventIdsByStudent(long studentId) {
        return inscriptionRepository.findDistinctEventIdsByStudentId(studentId);
    }

    @Override
    public List<Inscription> getEventStatusesByStudent(long studentId) {
        return inscriptionRepository.findByStudentIdOrderByDateInscriptionDesc(studentId);
    }

    @Override
    public boolean existsByStudentAndEvent(long studentId, long eventId) {
        return inscriptionRepository.existsByStudentIdAndEventId(studentId, eventId);
    }

    @Override
    public boolean hasConfirmedInscription(long studentId, long eventId) {
        return inscriptionRepository.existsByStudentIdAndEventIdAndStatut(
                studentId,
                eventId,
                InscriptionStatut.CONFIRMEE
        );
    }

    @Override
    public long countConfirmedByEvent(long eventId) {
        return inscriptionRepository.countByEventIdAndStatut(eventId, InscriptionStatut.CONFIRMEE);
    }

    private void promoteFromWaitlist(long eventId) {
        Inscription nextInWaitlist = inscriptionRepository.findFirstByEventIdAndStatutOrderByDateInscriptionAsc(
                eventId,
                InscriptionStatut.LISTE_ATTENTE
        );

        if (nextInWaitlist == null) {
            return;
        }

        nextInWaitlist.setStatut(InscriptionStatut.CONFIRMEE);
        nextInWaitlist = inscriptionRepository.save(nextInWaitlist);
        inscriptionConfirmedPublisher.publish(nextInWaitlist, "WAITLIST_PROMOTION");

        UserDto student = userClient.findById(nextInWaitlist.getStudentId()).orElse(null);
        EventDto event = eventClient.findById(nextInWaitlist.getEventId()).orElse(null);

        if (student != null && event != null) {
            notificationEmailService.sendWaitlistPromotionEmail(student, event, nextInWaitlist);
            createAdminPromotionNotification(student, event);
        } else {
            log.warn("Waitlist promotion completed but notification payload could not be enriched. eventId={}, studentId={}",
                    nextInWaitlist.getEventId(), nextInWaitlist.getStudentId());
        }
    }

    private void createAdminPromotionNotification(UserDto student, EventDto event) {
        AdminNotification notification = new AdminNotification();
        notification.setCreatedAt(LocalDateTime.now());
        notification.setType("WAITLIST_PROMOTION");
        notification.setEventId(event.idEvent());
        notification.setStudentId(student.userId());
        notification.setTitle("Waitlist promoted");
        notification.setMessage(buildPromotionMessage(student, event));
        adminNotificationRepository.save(notification);
    }

    private String buildPromotionMessage(UserDto student, EventDto event) {
        String studentName = ((student.firstName() != null ? student.firstName() : "") + " " +
                (student.lastName() != null ? student.lastName() : "")).trim();
        if (studentName.isBlank()) {
            studentName = "Student #" + student.userId();
        }
        String eventName = event.name() != null && !event.name().isBlank()
                ? event.name()
                : "Event #" + event.idEvent();
        return studentName + " has been moved from the waitlist to confirmed registration for " + eventName + ".";
    }

    private void trySendDecisionEmail(Inscription inscription, InscriptionStatut status) {
        UserDto student = userClient.findById(inscription.getStudentId()).orElse(null);
        EventDto event = eventClient.findById(inscription.getEventId()).orElse(null);

        if (student == null) {
            log.warn("Skipping inscription decision email because student {} was not found.", inscription.getStudentId());
            return;
        }
        if (event == null) {
            log.warn("Skipping inscription decision email because event {} was not found.", inscription.getEventId());
            return;
        }

        notificationEmailService.sendDecisionEmail(student, event, inscription, status);
    }

    private void createAdminDecisionNotification(Inscription inscription, InscriptionStatut status) {
        if (inscription == null || status == null) {
            return;
        }

        if (status != InscriptionStatut.CONFIRMEE && status != InscriptionStatut.ANNULEE) {
            return;
        }

        AdminNotification notification = new AdminNotification();
        notification.setCreatedAt(LocalDateTime.now());
        notification.setType(status == InscriptionStatut.CONFIRMEE
                ? "INSCRIPTION_CONFIRMED"
                : "INSCRIPTION_REFUSED");
        notification.setEventId(inscription.getEventId());
        notification.setStudentId(inscription.getStudentId());
        adminNotificationRepository.save(notification);
    }
}

