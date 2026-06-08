package tn.esprit.inscriptionservice.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import tn.esprit.inscriptionservice.client.EventClient;
import tn.esprit.inscriptionservice.client.UserClient;
import tn.esprit.inscriptionservice.dto.EventDto;
import tn.esprit.inscriptionservice.dto.UserDto;
import tn.esprit.inscriptionservice.entity.Inscription;
import tn.esprit.inscriptionservice.entity.InscriptionStatut;
import tn.esprit.inscriptionservice.messaging.InscriptionConfirmedPublisher;
import tn.esprit.inscriptionservice.repository.AdminNotificationRepository;
import tn.esprit.inscriptionservice.repository.InscriptionRepository;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InscriptionServicesImpTest {

    @Mock InscriptionRepository inscriptionRepository;
    @Mock EventClient eventClient;
    @Mock UserClient userClient;
    @Mock InscriptionNotificationEmailService notificationEmailService;
    @Mock AdminNotificationRepository adminNotificationRepository;
    @Mock InscriptionConfirmedPublisher inscriptionConfirmedPublisher;
    @InjectMocks InscriptionServicesImp service;

    private Inscription sample(long id, long studentId, long eventId, InscriptionStatut statut) {
        Inscription i = new Inscription();
        i.setIdInscription(id);
        i.setStudentId(studentId);
        i.setEventId(eventId);
        i.setDateInscription(LocalDateTime.now());
        i.setStatut(statut);
        return i;
    }

    private EventDto sampleEvent(long id, int capacite) {
        return new EventDto(id, "Tech Summit", "REF-001", "Desc", "Tunis",
                capacite, LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(2),
                "WEBINAIRE", "PUBLIE");
    }

    private UserDto sampleUser(long id) {
        return new UserDto(id, "johndoe", "john@test.com", "John", "Doe");
    }

    /* ─── createInscription ─── */

    @Test
    @DisplayName("createInscription() saves inscription with EN_ATTENTE when initialStatus is null")
    void create_nullStatus_defaultsToEnAttente() {
        when(inscriptionRepository.existsByStudentIdAndEventId(1L, 10L)).thenReturn(false);
        Inscription saved = sample(100L, 1L, 10L, InscriptionStatut.EN_ATTENTE);
        when(inscriptionRepository.save(any())).thenReturn(saved);

        Inscription result = service.createInscription(10L, 1L, null);

        assertThat(result.getStatut()).isEqualTo(InscriptionStatut.EN_ATTENTE);
        verify(inscriptionRepository).save(any());
    }

    @Test
    @DisplayName("createInscription() uses provided initialStatus")
    void create_withInitialStatus() {
        when(inscriptionRepository.existsByStudentIdAndEventId(2L, 20L)).thenReturn(false);
        Inscription saved = sample(101L, 2L, 20L, InscriptionStatut.LISTE_ATTENTE);
        when(inscriptionRepository.save(any())).thenReturn(saved);

        Inscription result = service.createInscription(20L, 2L, InscriptionStatut.LISTE_ATTENTE);

        assertThat(result.getStatut()).isEqualTo(InscriptionStatut.LISTE_ATTENTE);
    }

    @Test
    @DisplayName("createInscription() throws CONFLICT when student already registered")
    void create_duplicate_throwsConflict() {
        when(inscriptionRepository.existsByStudentIdAndEventId(3L, 30L)).thenReturn(true);

        assertThatThrownBy(() -> service.createInscription(30L, 3L, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already registered");
    }

    /* ─── updateStatus ─── */

    @Test
    @DisplayName("updateStatus() confirms inscription and publishes event")
    void updateStatus_confirmee_publishes() {
        Inscription i = sample(200L, 4L, 40L, InscriptionStatut.EN_ATTENTE);
        when(inscriptionRepository.findById(200L)).thenReturn(Optional.of(i));
        when(eventClient.findById(40L)).thenReturn(Optional.of(sampleEvent(40L, 100)));
        when(inscriptionRepository.countByEventIdAndStatut(40L, InscriptionStatut.CONFIRMEE)).thenReturn(5L);
        when(inscriptionRepository.save(any())).thenReturn(i);
        when(userClient.findById(4L)).thenReturn(Optional.of(sampleUser(4L)));
        doNothing().when(inscriptionConfirmedPublisher).publish(any(), any());
        doNothing().when(notificationEmailService).sendDecisionEmail(any(), any(), any(), any());

        service.updateStatus(200L, InscriptionStatut.CONFIRMEE);

        verify(inscriptionConfirmedPublisher).publish(any(), eq("STATUS_UPDATE"));
        verify(inscriptionRepository).save(any());
        var notificationCaptor = forClass(tn.esprit.inscriptionservice.entity.AdminNotification.class);
        verify(adminNotificationRepository).save(notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().getType()).isEqualTo("INSCRIPTION_CONFIRMED");
    }

    @Test
    @DisplayName("updateStatus() throws CONFLICT when event is full and trying to confirm")
    void updateStatus_confirm_eventFull_throwsConflict() {
        Inscription i = sample(201L, 5L, 50L, InscriptionStatut.EN_ATTENTE);
        when(inscriptionRepository.findById(201L)).thenReturn(Optional.of(i));
        when(eventClient.findById(50L)).thenReturn(Optional.of(sampleEvent(50L, 5)));
        when(inscriptionRepository.countByEventIdAndStatut(50L, InscriptionStatut.CONFIRMEE)).thenReturn(5L);

        assertThatThrownBy(() -> service.updateStatus(201L, InscriptionStatut.CONFIRMEE))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already full");
    }

    @Test
    @DisplayName("updateStatus() annuls inscription without publishing confirmed event")
    void updateStatus_annule_noPublish() {
        Inscription i = sample(202L, 6L, 60L, InscriptionStatut.EN_ATTENTE);
        when(inscriptionRepository.findById(202L)).thenReturn(Optional.of(i));
        when(inscriptionRepository.save(any())).thenReturn(i);
        when(userClient.findById(6L)).thenReturn(Optional.of(sampleUser(6L)));
        when(eventClient.findById(60L)).thenReturn(Optional.of(sampleEvent(60L, 50)));

        service.updateStatus(202L, InscriptionStatut.ANNULEE);

        verify(inscriptionConfirmedPublisher, never()).publish(any(), any());
        var notificationCaptor = forClass(tn.esprit.inscriptionservice.entity.AdminNotification.class);
        verify(adminNotificationRepository).save(notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().getType()).isEqualTo("INSCRIPTION_REFUSED");
    }

    @Test
    @DisplayName("updateStatus() promotes from waitlist when a confirmed inscription is annulled")
    void updateStatus_annulConfirmed_promotesWaitlist() {
        Inscription confirmed = sample(203L, 7L, 70L, InscriptionStatut.CONFIRMEE);
        Inscription waitlisted = sample(204L, 8L, 70L, InscriptionStatut.LISTE_ATTENTE);

        when(inscriptionRepository.findById(203L)).thenReturn(Optional.of(confirmed));
        when(inscriptionRepository.save(any())).thenReturn(confirmed);
        when(inscriptionRepository.findFirstByEventIdAndStatutOrderByDateInscriptionAsc(70L, InscriptionStatut.LISTE_ATTENTE))
                .thenReturn(waitlisted);
        doNothing().when(inscriptionConfirmedPublisher).publish(any(), any());

        service.updateStatus(203L, InscriptionStatut.ANNULEE);

        verify(inscriptionRepository, atLeast(2)).save(any());
        verify(inscriptionConfirmedPublisher).publish(any(), eq("WAITLIST_PROMOTION"));
    }

    @Test
    @DisplayName("updateStatus() throws BAD_REQUEST for invalid status")
    void updateStatus_invalidStatus_throws() {
        Inscription i = sample(205L, 9L, 90L, InscriptionStatut.EN_ATTENTE);
        when(inscriptionRepository.findById(205L)).thenReturn(Optional.of(i));

        assertThatThrownBy(() -> service.updateStatus(205L, InscriptionStatut.EN_ATTENTE))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("CONFIRMEE or ANNULEE");
    }

    @Test
    @DisplayName("updateStatus() throws NOT_FOUND when inscription does not exist")
    void updateStatus_notFound_throws() {
        when(inscriptionRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateStatus(999L, InscriptionStatut.CONFIRMEE))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not found");
    }

    /* ─── cancelByStudentAndEvent ─── */

    @Test
    @DisplayName("cancelByStudentAndEvent() deletes inscription when found")
    void cancel_found_deletes() {
        Inscription i = sample(300L, 10L, 100L, InscriptionStatut.EN_ATTENTE);
        when(inscriptionRepository.findByStudentIdAndEventId(10L, 100L)).thenReturn(i);
        doNothing().when(inscriptionRepository).delete(i);

        service.cancelByStudentAndEvent(10L, 100L);

        verify(inscriptionRepository).delete(i);
    }

    @Test
    @DisplayName("cancelByStudentAndEvent() promotes from waitlist when confirmed inscription is cancelled")
    void cancel_confirmed_promotesWaitlist() {
        Inscription confirmed = sample(301L, 11L, 110L, InscriptionStatut.CONFIRMEE);
        when(inscriptionRepository.findByStudentIdAndEventId(11L, 110L)).thenReturn(confirmed);
        doNothing().when(inscriptionRepository).delete(confirmed);
        when(inscriptionRepository.findFirstByEventIdAndStatutOrderByDateInscriptionAsc(110L, InscriptionStatut.LISTE_ATTENTE))
                .thenReturn(null);

        service.cancelByStudentAndEvent(11L, 110L);

        verify(inscriptionRepository).delete(confirmed);
        verify(inscriptionRepository).findFirstByEventIdAndStatutOrderByDateInscriptionAsc(110L, InscriptionStatut.LISTE_ATTENTE);
    }

    @Test
    @DisplayName("cancelByStudentAndEvent() throws NOT_FOUND when registration does not exist")
    void cancel_notFound_throws() {
        when(inscriptionRepository.findByStudentIdAndEventId(99L, 999L)).thenReturn(null);

        assertThatThrownBy(() -> service.cancelByStudentAndEvent(99L, 999L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Registration not found");
    }

    /* ─── queries ─── */

    @Test
    @DisplayName("getPendingInscriptions() returns EN_ATTENTE and LISTE_ATTENTE inscriptions")
    void getPending_returnsList() {
        List<Inscription> pending = List.of(
                sample(400L, 12L, 120L, InscriptionStatut.EN_ATTENTE),
                sample(401L, 13L, 130L, InscriptionStatut.LISTE_ATTENTE)
        );
        when(inscriptionRepository.findByStatutInOrderByDateInscriptionDesc(
                Arrays.asList(InscriptionStatut.EN_ATTENTE, InscriptionStatut.LISTE_ATTENTE)))
                .thenReturn(pending);

        assertThat(service.getPendingInscriptions()).hasSize(2);
    }

    @Test
    @DisplayName("getDistinctEventIdsByStudent() delegates to repository")
    void getDistinctEventIdsByStudent_delegates() {
        when(inscriptionRepository.findDistinctEventIdsByStudentId(17L)).thenReturn(List.of(170L, 171L));

        assertThat(service.getDistinctEventIdsByStudent(17L)).containsExactly(170L, 171L);
    }

    @Test
    @DisplayName("getEventStatusesByStudent() delegates to repository ordering by latest inscription")
    void getEventStatusesByStudent_delegates() {
        List<Inscription> expected = List.of(
                sample(600L, 18L, 180L, InscriptionStatut.CONFIRMEE),
                sample(601L, 18L, 181L, InscriptionStatut.LISTE_ATTENTE)
        );
        when(inscriptionRepository.findByStudentIdOrderByDateInscriptionDesc(18L)).thenReturn(expected);

        assertThat(service.getEventStatusesByStudent(18L)).containsExactlyElementsOf(expected);
    }

    @Test
    @DisplayName("existsByStudentAndEvent() delegates to repository")
    void existsByStudentAndEvent_delegates() {
        when(inscriptionRepository.existsByStudentIdAndEventId(14L, 140L)).thenReturn(true);

        assertThat(service.existsByStudentAndEvent(14L, 140L)).isTrue();
    }

    @Test
    @DisplayName("countConfirmedByEvent() delegates to repository")
    void countConfirmedByEvent_delegates() {
        when(inscriptionRepository.countByEventIdAndStatut(150L, InscriptionStatut.CONFIRMEE)).thenReturn(8L);

        assertThat(service.countConfirmedByEvent(150L)).isEqualTo(8L);
    }

    @Test
    @DisplayName("hasConfirmedInscription() delegates to repository")
    void hasConfirmedInscription_delegates() {
        when(inscriptionRepository.existsByStudentIdAndEventIdAndStatut(15L, 160L, InscriptionStatut.CONFIRMEE))
                .thenReturn(true);

        assertThat(service.hasConfirmedInscription(15L, 160L)).isTrue();
    }

    @Test
    @DisplayName("getWaitlistByEvent() returns waitlisted inscriptions for event")
    void getWaitlist_returnsList() {
        List<Inscription> waitlist = List.of(sample(500L, 16L, 170L, InscriptionStatut.LISTE_ATTENTE));
        when(inscriptionRepository.findByEventIdAndStatutOrderByDateInscriptionAsc(170L, InscriptionStatut.LISTE_ATTENTE))
                .thenReturn(waitlist);

        assertThat(service.getWaitlistByEvent(170L)).hasSize(1);
    }
}
