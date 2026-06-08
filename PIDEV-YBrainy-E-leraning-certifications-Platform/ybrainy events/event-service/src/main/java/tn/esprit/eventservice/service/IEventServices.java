package tn.esprit.eventservice.service;

import tn.esprit.eventservice.entity.Event;
import tn.esprit.eventservice.dto.EventAnalyticsResponseDto;
import tn.esprit.eventservice.dto.EventAssignmentResponseDto;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface IEventServices {
    Event addEvent(Event event);
    Event updateEvent(Event event);
    EventDescriptionGenerationService.GeneratedDescriptionResult generateDescription(String name, String type);
    EventImageGenerationService.GeneratedImageResult generateImage(String name, String description, String type);
    EventImageGenerationService.GeneratedImageResult uploadImage(MultipartFile file);
    EventImageGenerationService.GeneratedImageFile loadGeneratedImage(String fileName);
    Event getEventById(long idEvent);
    List<Event> getAllEvents();
    void deleteEvent(long idEvent);
    EventAssignmentResponseDto assignStudentToEvent(long idEvent, long idStudent);
    EventAnalyticsResponseDto getAnalytics(String range);
}
