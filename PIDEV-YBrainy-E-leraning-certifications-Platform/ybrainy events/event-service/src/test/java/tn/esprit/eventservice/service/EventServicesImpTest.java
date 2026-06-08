package tn.esprit.eventservice.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import tn.esprit.eventservice.client.InscriptionClient;
import tn.esprit.eventservice.client.UserClient;
import tn.esprit.eventservice.dto.InscriptionAssignmentResultDto;
import tn.esprit.eventservice.dto.UserDto;
import tn.esprit.eventservice.entity.Event;
import tn.esprit.eventservice.entity.EventStatut;
import tn.esprit.eventservice.entity.EventType;
import tn.esprit.eventservice.messaging.EventCreatedPublisher;
import tn.esprit.eventservice.repository.EventRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventServicesImpTest {

    @Mock EventRepository eventRepository;
    @Mock UserClient userClient;
    @Mock InscriptionClient inscriptionClient;
    @Mock EventDescriptionGenerationService eventDescriptionGenerationService;
    @Mock EventImageGenerationService eventImageGenerationService;
    @Mock EventCreatedPublisher eventCreatedPublisher;
    @InjectMocks EventServicesImp service;

    private Event futureEvent(long id, long adminId) {
        Event e = new Event();
        e.setIdEvent(id);
        e.setName("Tech Webinar");
        e.setType(EventType.WEBINAIRE);
        e.setStatut(EventStatut.PUBLIE);
        e.setAdminId(adminId);
        e.setCapacite(50);
        e.setImageUrl("http://img.example.com/img.png");
        e.setDateDebut(LocalDateTime.now().plusDays(1));
        e.setDateFin(LocalDateTime.now().plusDays(2));
        return e;
    }

    private void stubReference() {
        when(eventRepository.countByReferenceEventStartingWith(anyString())).thenReturn(0L);
        when(eventRepository.existsByReferenceEvent(anyString())).thenReturn(false);
    }

    /* ─── addEvent ─── */

    @Test
    @DisplayName("addEvent() saves, publishes, and returns event when adminId is set and imageUrl provided")
    void addEvent_success() {
        Event input = futureEvent(0, 1L);
        Event saved = futureEvent(10L, 1L);
        saved.setReferenceEvent("WEB-20260101-0001");

        stubReference();
        when(eventRepository.save(any())).thenReturn(saved);
        doNothing().when(eventCreatedPublisher).publish(any());

        Event result = service.addEvent(input);

        assertThat(result.getIdEvent()).isEqualTo(10L);
        verify(eventRepository).save(any());
        verify(eventCreatedPublisher).publish(any());
    }

    @Test
    @DisplayName("addEvent() resolves admin from user-service when adminId is 0")
    void addEvent_noAdminId_resolvesFromUserService() {
        Event input = futureEvent(0, 0L);
        Event saved = futureEvent(11L, 5L);
        saved.setReferenceEvent("WEB-20260101-0001");

        stubReference();
        when(userClient.findFirstByRole("ADMIN"))
                .thenReturn(Optional.of(new UserDto(5L, "Admin", "User", "admin@test.com", "ADMIN")));
        when(eventRepository.save(any())).thenReturn(saved);
        doNothing().when(eventCreatedPublisher).publish(any());

        Event result = service.addEvent(input);

        verify(userClient).findFirstByRole("ADMIN");
        assertThat(result.getIdEvent()).isEqualTo(11L);
    }

    @Test
    @DisplayName("addEvent() throws BAD_REQUEST when no admin available")
    void addEvent_noAdminAvailable_throws() {
        Event input = futureEvent(0, 0L);
        when(userClient.findFirstByRole("ADMIN")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addEvent(input))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("No admin");
    }

    @Test
    @DisplayName("addEvent() throws BAD_REQUEST when dateDebut is missing")
    void addEvent_missingDateDebut_throws() {
        Event input = futureEvent(0, 1L);
        input.setDateDebut(null);

        assertThatThrownBy(() -> service.addEvent(input))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Start Date");
    }

    @Test
    @DisplayName("addEvent() throws BAD_REQUEST when dateFin is in the past")
    void addEvent_pastDateFin_throws() {
        Event input = futureEvent(0, 1L);
        input.setDateFin(LocalDateTime.now().minusDays(1));

        assertThatThrownBy(() -> service.addEvent(input))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("End Date");
    }

    @Test
    @DisplayName("addEvent() throws BAD_REQUEST when dateFin is before dateDebut")
    void addEvent_finBeforeDebut_throws() {
        Event input = futureEvent(0, 1L);
        input.setDateDebut(LocalDateTime.now().plusDays(3));
        input.setDateFin(LocalDateTime.now().plusDays(2));

        assertThatThrownBy(() -> service.addEvent(input))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("End Date must be after");
    }

    /* ─── updateEvent ─── */

    @Test
    @DisplayName("updateEvent() saves modified event when not terminated")
    void updateEvent_success() {
        Event existing = futureEvent(20L, 2L);
        existing.setReferenceEvent("WEB-20260101-0001");

        Event update = futureEvent(20L, 2L);
        update.setName("Updated Webinar");

        when(eventRepository.findById(20L)).thenReturn(Optional.of(existing));
        when(eventRepository.save(any())).thenReturn(update);

        Event result = service.updateEvent(update);

        assertThat(result.getName()).isEqualTo("Updated Webinar");
        verify(eventRepository).save(any());
    }

    @Test
    @DisplayName("updateEvent() throws CONFLICT when existing event is TERMINE")
    void updateEvent_terminated_throws() {
        Event existing = futureEvent(21L, 2L);
        existing.setStatut(EventStatut.TERMINE);

        Event update = futureEvent(21L, 2L);

        when(eventRepository.findById(21L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.updateEvent(update))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Terminated events");
    }

    /* ─── getEventById ─── */

    @Test
    @DisplayName("getEventById() returns event with inscription count attached")
    void getEventById_found() {
        Event e = futureEvent(30L, 3L);
        e.setReferenceEvent("WEB-20260101-0001");
        when(eventRepository.findById(30L)).thenReturn(Optional.of(e));
        when(inscriptionClient.countConfirmedByEventId(30L)).thenReturn(7L);

        Event result = service.getEventById(30L);

        assertThat(result).isNotNull();
        assertThat(result.getInscriptionsCount()).isEqualTo(7L);
    }

    @Test
    @DisplayName("getEventById() returns null when event not found")
    void getEventById_notFound() {
        when(eventRepository.findById(999L)).thenReturn(Optional.empty());

        Event result = service.getEventById(999L);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("getEventById() auto-terminates event when dateFin is in the past")
    void getEventById_pastDateFin_autoTerminates() {
        Event e = new Event();
        e.setIdEvent(31L);
        e.setAdminId(1L);
        e.setStatut(EventStatut.PUBLIE);
        e.setReferenceEvent("WEB-20250101-0001");
        e.setDateDebut(LocalDateTime.now().minusDays(5));
        e.setDateFin(LocalDateTime.now().minusDays(1));

        when(eventRepository.findById(31L)).thenReturn(Optional.of(e));
        when(eventRepository.save(any())).thenReturn(e);
        when(inscriptionClient.countConfirmedByEventId(31L)).thenReturn(0L);

        Event result = service.getEventById(31L);

        assertThat(result.getStatut()).isEqualTo(EventStatut.TERMINE);
        verify(eventRepository, atLeastOnce()).save(e);
    }

    /* ─── getAllEvents ─── */

    @Test
    @DisplayName("getAllEvents() returns all events with inscription counts")
    void getAllEvents_returnsList() {
        Event e1 = futureEvent(40L, 1L);
        e1.setReferenceEvent("WEB-20260101-0001");
        Event e2 = futureEvent(41L, 1L);
        e2.setReferenceEvent("WEB-20260101-0002");

        when(eventRepository.findAll()).thenReturn(List.of(e1, e2));
        when(inscriptionClient.countConfirmedByEventId(anyLong())).thenReturn(3L);

        List<Event> result = service.getAllEvents();

        assertThat(result).hasSize(2);
        result.forEach(e -> assertThat(e.getInscriptionsCount()).isEqualTo(3L));
    }

    @Test
    @DisplayName("getAllEvents() returns empty list when no events exist")
    void getAllEvents_empty() {
        when(eventRepository.findAll()).thenReturn(List.of());

        assertThat(service.getAllEvents()).isEmpty();
    }

    /* ─── deleteEvent ─── */

    @Test
    @DisplayName("deleteEvent() delegates to repository deleteById")
    void deleteEvent_callsRepository() {
        doNothing().when(eventRepository).deleteById(50L);

        service.deleteEvent(50L);

        verify(eventRepository).deleteById(50L);
    }

    /* ─── assignStudentToEvent ─── */

    @Test
    @DisplayName("assignStudentToEvent() creates inscription with EN_ATTENTE when capacity not reached")
    void assignStudent_underCapacity_enAttente() {
        Event e = futureEvent(60L, 1L);
        UserDto student = new UserDto(100L, "Alice", "Smith", "alice@test.com", "STUDENT");

        when(eventRepository.findById(60L)).thenReturn(Optional.of(e));
        when(userClient.findById(100L)).thenReturn(Optional.of(student));
        when(inscriptionClient.existsByStudentIdAndEventId(100L, 60L)).thenReturn(false);
        when(inscriptionClient.countConfirmedByEventId(60L)).thenReturn(10L);
        when(inscriptionClient.createInscription(any()))
                .thenReturn(new InscriptionAssignmentResultDto(200L, 60L, 100L, "EN_ATTENTE"));

        var result = service.assignStudentToEvent(60L, 100L);

        assertThat(result.inscriptionStatus()).isEqualTo("EN_ATTENTE");
        assertThat(result.studentId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("assignStudentToEvent() assigns LISTE_ATTENTE when event is at capacity")
    void assignStudent_atCapacity_listeAttente() {
        Event e = futureEvent(61L, 1L);
        e.setCapacite(5);
        UserDto student = new UserDto(101L, "Bob", "Jones", "bob@test.com", "STUDENT");

        when(eventRepository.findById(61L)).thenReturn(Optional.of(e));
        when(userClient.findById(101L)).thenReturn(Optional.of(student));
        when(inscriptionClient.existsByStudentIdAndEventId(101L, 61L)).thenReturn(false);
        when(inscriptionClient.countConfirmedByEventId(61L)).thenReturn(5L);
        when(inscriptionClient.createInscription(any()))
                .thenReturn(new InscriptionAssignmentResultDto(201L, 61L, 101L, "LISTE_ATTENTE"));

        var result = service.assignStudentToEvent(61L, 101L);

        assertThat(result.inscriptionStatus()).isEqualTo("LISTE_ATTENTE");
    }

    @Test
    @DisplayName("assignStudentToEvent() throws NOT_FOUND when event does not exist")
    void assignStudent_eventNotFound_throws() {
        when(eventRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assignStudentToEvent(999L, 1L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Event not found");
    }

    @Test
    @DisplayName("assignStudentToEvent() throws NOT_FOUND when student does not exist")
    void assignStudent_studentNotFound_throws() {
        Event e = futureEvent(62L, 1L);
        when(eventRepository.findById(62L)).thenReturn(Optional.of(e));
        when(userClient.findById(999L)).thenReturn(Optional.empty());
        when(userClient.findFirstByRole("STUDENT")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assignStudentToEvent(62L, 999L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Student not found");
    }

    @Test
    @DisplayName("assignStudentToEvent() throws CONFLICT when student already registered")
    void assignStudent_duplicate_throws() {
        Event e = futureEvent(63L, 1L);
        UserDto student = new UserDto(102L, "Charlie", "Brown", "cb@test.com", "STUDENT");

        when(eventRepository.findById(63L)).thenReturn(Optional.of(e));
        when(userClient.findById(102L)).thenReturn(Optional.of(student));
        when(inscriptionClient.existsByStudentIdAndEventId(102L, 63L)).thenReturn(true);

        assertThatThrownBy(() -> service.assignStudentToEvent(63L, 102L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already registered");
    }
}
