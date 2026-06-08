package tn.esprit.eventservice.service;

import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;
import tn.esprit.eventservice.client.InscriptionClient;
import tn.esprit.eventservice.client.UserClient;
import tn.esprit.eventservice.dto.EventAnalyticsResponseDto;
import tn.esprit.eventservice.dto.EventAssignmentResponseDto;
import tn.esprit.eventservice.dto.InscriptionAssignmentResultDto;
import tn.esprit.eventservice.dto.InscriptionCreateDto;
import tn.esprit.eventservice.dto.UserDto;
import tn.esprit.eventservice.entity.Event;
import tn.esprit.eventservice.entity.EventStatut;
import tn.esprit.eventservice.entity.EventType;
import tn.esprit.eventservice.messaging.EventCreatedPublisher;
import tn.esprit.eventservice.repository.EventRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Locale;
import java.util.List;

@Service
@AllArgsConstructor
public class EventServicesImp implements IEventServices {

    private final EventRepository eventRepository;

    /** Cross-service: user-service */
    private final UserClient userClient;

    /** Cross-service: inscription-service */
    private final InscriptionClient inscriptionClient;

    private final EventDescriptionGenerationService eventDescriptionGenerationService;
    private final EventImageGenerationService eventImageGenerationService;
    private final EventCreatedPublisher eventCreatedPublisher;

    // ------------------------------------------------------------------ CRUD

    @Override
    public Event addEvent(Event event) {
        validateEventDates(event);
        ensureAdminId(event, null);
        event.setDateCreation(LocalDateTime.now());
        event.setReferenceEvent(generateUniqueReference(event.getType()));
        applyGeneratedImageIfMissing(event, null);
        Event savedEvent = eventRepository.save(event);
        eventCreatedPublisher.publish(savedEvent);
        return savedEvent;
    }

    @Override
    public Event updateEvent(Event event) {
        Event existing = eventRepository.findById(event.getIdEvent()).orElse(null);
        if (existing != null) {
            applyAutomaticTermination(existing);
            if (EventStatut.TERMINE.equals(existing.getStatut())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Terminated events cannot be modified.");
            }
        }
        validateEventDates(event);
        ensureAdminId(event, existing);
        preserveOrGenerateReference(event, existing);
        applyGeneratedImageIfMissing(event, existing);
        return eventRepository.save(event);
    }

    @Override
    public EventDescriptionGenerationService.GeneratedDescriptionResult generateDescription(String name, String type) {
        return eventDescriptionGenerationService.generateDescription(name, type);
    }

    @Override
    public EventImageGenerationService.GeneratedImageResult generateImage(String name, String description, String type) {
        return eventImageGenerationService.generateImage(name, description, type);
    }

    @Override
    public EventImageGenerationService.GeneratedImageResult uploadImage(MultipartFile file) {
        return eventImageGenerationService.uploadImage(file);
    }

    @Override
    public EventImageGenerationService.GeneratedImageFile loadGeneratedImage(String fileName) {
        return eventImageGenerationService.loadGeneratedImage(fileName);
    }

    @Override
    public Event getEventById(long idEvent) {
        Event event = eventRepository.findById(idEvent).orElse(null);
        if (event != null) {
            ensureReferencePersisted(event);
            applyAutomaticTermination(event);
            attachInscriptionCount(event);
            normalizeGeneratedImageUrl(event);
        }
        return event;
    }

    @Override
    public List<Event> getAllEvents() {
        List<Event> events = eventRepository.findAll();
        for (Event event : events) {
            ensureReferencePersisted(event);
            applyAutomaticTermination(event);
            attachInscriptionCount(event);
            normalizeGeneratedImageUrl(event);
        }
        return events;
    }

    @Override
    public void deleteEvent(long idEvent) {
        eventRepository.deleteById(idEvent);
    }

    // ------------------------------------------------------- Student assignment

    @Override
    public EventAssignmentResponseDto assignStudentToEvent(long idEvent, long idStudent) {
        // 1. Resolve event locally
        Event event = eventRepository.findById(idEvent)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found."));

        // 2. Resolve student via user-service (Feign)
        UserDto student = userClient.findById(idStudent)
                .or(() -> userClient.findFirstByRole("STUDENT"))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Student not found."));

        // 3. Guard against duplicate inscription via inscription-service (Feign)
        if (inscriptionClient.existsByStudentIdAndEventId(student.idUser(), event.getIdEvent())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Student already registered for this event.");
        }

        long confirmedCount = inscriptionClient.countConfirmedByEventId(event.getIdEvent());
        String initialStatus = confirmedCount >= Math.max(0, event.getCapacite())
                ? "LISTE_ATTENTE"
                : "EN_ATTENTE";

        // 4. Delegate inscription creation to inscription-service (Feign)
        InscriptionAssignmentResultDto createdInscription = inscriptionClient.createInscription(
                new InscriptionCreateDto(event.getIdEvent(), student.idUser(), initialStatus)
        );

        // 5. Refresh count on the returned event object
        attachInscriptionCount(event);
        return new EventAssignmentResponseDto(event.getIdEvent(), student.idUser(), createdInscription.statut());
    }

    @Override
    public EventAnalyticsResponseDto getAnalytics(String range) {
        List<Event> events = eventRepository.findAll();
        events.forEach(event -> {
            applyAutomaticTermination(event);
            attachInscriptionCount(event);
        });

        String normalizedRange = normalizeRange(range);
        return new EventAnalyticsResponseDto(
                buildTrendSeries(events),
                buildOverviewSeries(events, normalizedRange)
        );
    }

    // --------------------------------------------------------- Private helpers

    private EventAnalyticsResponseDto.TrendSeries buildTrendSeries(List<Event> events) {
        LocalDate now = LocalDate.now();
        LocalDate currentMonth = now.withDayOfMonth(1);
        LocalDate previousMonth = currentMonth.minusMonths(1);

        List<String> labels = List.of("Week 01", "Week 02", "Week 03", "Week 04", "Week 05", "Week 06");
        List<Integer> currentData = aggregateWeeklyEventCounts(events, currentMonth, 6);
        List<Integer> previousData = aggregateWeeklyEventCounts(events, previousMonth, 6);

        return new EventAnalyticsResponseDto.TrendSeries(
                monthLabel(currentMonth),
                currentData.stream().mapToInt(Integer::intValue).sum(),
                monthLabel(previousMonth),
                previousData.stream().mapToInt(Integer::intValue).sum(),
                labels,
                currentData,
                previousData
        );
    }

    private EventAnalyticsResponseDto.OverviewSeries buildOverviewSeries(List<Event> events, String range) {
        List<String> labels = new ArrayList<>();
        List<Integer> eventCounts = new ArrayList<>();
        List<Integer> registrations = new ArrayList<>();
        List<Integer> activeEvents = new ArrayList<>();
        LocalDate today = LocalDate.now();

        if ("week".equals(range)) {
            labels.addAll(List.of("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"));
            initZeros(eventCounts, 7);
            initZeros(registrations, 7);
            initZeros(activeEvents, 7);

            LocalDate weekStart = today.minusDays(6);
            for (Event event : events) {
                LocalDate eventDate = toEventDate(event);
                if (eventDate == null || eventDate.isBefore(weekStart) || eventDate.isAfter(today)) continue;
                int index = eventDate.getDayOfWeek().getValue() % 7;
                eventCounts.set(index, eventCounts.get(index) + 1);
                registrations.set(index, registrations.get(index) + safeRegistrations(event));
                if (!isTerminated(event)) {
                    activeEvents.set(index, activeEvents.get(index) + 1);
                }
            }
        } else if ("month".equals(range)) {
            labels.addAll(List.of("Week 1", "Week 2", "Week 3", "Week 4"));
            initZeros(eventCounts, 4);
            initZeros(registrations, 4);
            initZeros(activeEvents, 4);

            for (Event event : events) {
                LocalDate eventDate = toEventDate(event);
                if (eventDate == null) continue;
                if (eventDate.getYear() != today.getYear() || eventDate.getMonthValue() != today.getMonthValue()) continue;
                int index = Math.min(3, (eventDate.getDayOfMonth() - 1) / 7);
                eventCounts.set(index, eventCounts.get(index) + 1);
                registrations.set(index, registrations.get(index) + safeRegistrations(event));
                if (!isTerminated(event)) {
                    activeEvents.set(index, activeEvents.get(index) + 1);
                }
            }
        } else {
            labels.addAll(List.of("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"));
            initZeros(eventCounts, 12);
            initZeros(registrations, 12);
            initZeros(activeEvents, 12);

            for (Event event : events) {
                LocalDate eventDate = toEventDate(event);
                if (eventDate == null) continue;
                int index = eventDate.getMonthValue() - 1;
                eventCounts.set(index, eventCounts.get(index) + 1);
                registrations.set(index, registrations.get(index) + safeRegistrations(event));
                if (!isTerminated(event)) {
                    activeEvents.set(index, activeEvents.get(index) + 1);
                }
            }
        }

        return new EventAnalyticsResponseDto.OverviewSeries(
                range,
                labels,
                eventCounts,
                registrations,
                activeEvents
        );
    }

    private List<Integer> aggregateWeeklyEventCounts(List<Event> events, LocalDate monthStart, int bucketCount) {
        List<Integer> buckets = new ArrayList<>();
        initZeros(buckets, bucketCount);

        for (Event event : events) {
            LocalDate eventDate = toEventDate(event);
            if (eventDate == null) continue;
            if (eventDate.getYear() != monthStart.getYear() || eventDate.getMonthValue() != monthStart.getMonthValue()) continue;
            int index = Math.min(bucketCount - 1, (eventDate.getDayOfMonth() - 1) / 5);
            buckets.set(index, buckets.get(index) + 1);
        }

        return buckets;
    }

    private void initZeros(List<Integer> target, int size) {
        for (int i = 0; i < size; i++) {
            target.add(0);
        }
    }

    private String monthLabel(LocalDate date) {
        return date.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
    }

    private String normalizeRange(String range) {
        if (range == null) return "week";
        String normalized = range.trim().toLowerCase(Locale.ENGLISH);
        return switch (normalized) {
            case "month", "year", "all" -> normalized;
            default -> "week";
        };
    }

    private LocalDate toEventDate(Event event) {
        return event != null && event.getDateDebut() != null ? event.getDateDebut().toLocalDate() : null;
    }

    private int safeRegistrations(Event event) {
        return event != null ? (int) Math.max(0, event.getInscriptionsCount()) : 0;
    }

    private boolean isTerminated(Event event) {
        if (event == null) return false;
        if (EventStatut.TERMINE.equals(event.getStatut())) return true;
        return event.getDateFin() != null && event.getDateFin().isBefore(LocalDateTime.now());
    }

    private void validateEventDates(Event event) {
        if (event == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Event payload is required.");
        }
        if (event.getDateDebut() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Start Date is required.");
        }
        if (event.getDateFin() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "End Date is required.");
        }

        LocalDate today = LocalDate.now();
        if (event.getDateDebut().toLocalDate().isBefore(today)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Start Date cannot be before today.");
        }
        if (event.getDateFin().toLocalDate().isBefore(today)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "End Date cannot be before today.");
        }
        if (event.getDateFin().isBefore(event.getDateDebut())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "End Date must be after Start Date.");
        }
    }

    private void applyAutomaticTermination(Event event) {
        if (event == null || event.getDateFin() == null) return;
        if (event.getDateFin().isBefore(LocalDateTime.now())
                && !EventStatut.TERMINE.equals(event.getStatut())) {
            event.setStatut(EventStatut.TERMINE);
            eventRepository.save(event);
        }
    }

    private void attachInscriptionCount(Event event) {
        if (event == null || event.getIdEvent() <= 0) return;
        // Delegate count to inscription-service (Feign).
        // Keep event endpoints resilient during startup or when inscription-service is temporarily unavailable.
        try {
            long count = inscriptionClient.countConfirmedByEventId(event.getIdEvent());
            event.setInscriptionsCount(Math.max(0, count));
        } catch (Exception ignored) {
            event.setInscriptionsCount(Math.max(0, event.getInscriptionsCount()));
        }
    }

    private void ensureAdminId(Event event, Event existing) {
        if (event == null) return;

        if (event.getAdminId() > 0) {
            return;
        }

        if (existing != null && existing.getAdminId() > 0) {
            event.setAdminId(existing.getAdminId());
            return;
        }

        long resolvedAdminId = userClient.findFirstByRole("ADMIN")
                .map(UserDto::idUser)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "No admin user is available to assign to this event."
                ));

        event.setAdminId(resolvedAdminId);
    }

    private void preserveOrGenerateReference(Event event, Event existing) {
        if (event == null) return;

        if (existing != null && existing.getReferenceEvent() != null && !existing.getReferenceEvent().isBlank()) {
            event.setReferenceEvent(existing.getReferenceEvent());
            return;
        }

        if (event.getReferenceEvent() == null || event.getReferenceEvent().isBlank()) {
            event.setReferenceEvent(generateUniqueReference(event.getType()));
        }
    }

    private void ensureReferencePersisted(Event event) {
        if (event == null) return;
        if (event.getReferenceEvent() != null && !event.getReferenceEvent().isBlank()) return;

        event.setReferenceEvent(generateUniqueReference(event.getType()));
        eventRepository.save(event);
    }

    private void applyGeneratedImageIfMissing(Event event, Event existing) {
        if (event == null) {
            return;
        }

        if (event.getImageUrl() != null && !event.getImageUrl().isBlank()) {
            return;
        }

        if (existing != null && existing.getImageUrl() != null && !existing.getImageUrl().isBlank()) {
            event.setImageUrl(existing.getImageUrl());
            return;
        }

        var generated = eventImageGenerationService.generateImage(
                event.getName(),
                event.getDescription(),
                event.getType() != null ? event.getType().name() : null
        );

        if (generated.imageUrl() != null && !generated.imageUrl().isBlank()) {
            event.setImageUrl(generated.imageUrl());
        }
    }

    private void normalizeGeneratedImageUrl(Event event) {
        if (event == null) return;
        String url = event.getImageUrl();
        if (url == null || url.isBlank()) return;

        // Some older records stored absolute URLs such as
        // http://localhost:8081/Event/generated-images/<file>
        // Normalize them to a same-origin relative URL so the Angular dev proxy can serve images.
        int pathIndex = url.indexOf("/Event/generated-images/");
        if (pathIndex > 0) {
            event.setImageUrl(url.substring(pathIndex));
        }
    }

    private String generateUniqueReference(EventType type) {
        String prefix = buildTypePrefix(type);
        String datePart = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String basePrefix = prefix + "-" + datePart + "-";

        long nextCounter = eventRepository.countByReferenceEventStartingWith(basePrefix) + 1;
        String candidate = buildReference(basePrefix, nextCounter);

        while (eventRepository.existsByReferenceEvent(candidate)) {
            nextCounter += 1;
            candidate = buildReference(basePrefix, nextCounter);
        }

        return candidate;
    }

    private String buildReference(String basePrefix, long counter) {
        return basePrefix + String.format("%04d", counter);
    }

    private String buildTypePrefix(EventType type) {
        if (type == null) {
            return "EVT";
        }

        return switch (type) {
            case HACKATHON -> "HCK";
            case WEBINAIRE -> "WEB";
            case FORMATION -> "FOR";
            case ATELIER -> "ATL";
        };
    }
}
