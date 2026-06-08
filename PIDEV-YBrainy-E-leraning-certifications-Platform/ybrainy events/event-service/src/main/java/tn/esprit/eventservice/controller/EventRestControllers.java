package tn.esprit.eventservice.controller;

import lombok.AllArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;
import tn.esprit.eventservice.dto.DescriptionGenerationRequest;
import tn.esprit.eventservice.dto.DescriptionGenerationResponse;
import tn.esprit.eventservice.dto.EventAnalyticsResponseDto;
import tn.esprit.eventservice.dto.EventAssignmentResponseDto;
import tn.esprit.eventservice.dto.ImageGenerationRequest;
import tn.esprit.eventservice.dto.ImageGenerationResponse;
import tn.esprit.eventservice.entity.Event;
import tn.esprit.eventservice.service.IEventServices;

import java.util.List;

@RestController
@CrossOrigin(
        origins = {"http://localhost:4200", "http://127.0.0.1:4200"},
        allowCredentials = "true"
)
@AllArgsConstructor
@RequestMapping("/Event")
public class EventRestControllers {

    private final IEventServices eventServices;

    @PostMapping("/add")
    public Event addEvent(@RequestBody Event event) {
        return eventServices.addEvent(event);
    }

    @PutMapping("/update")
    public Event updateEvent(@RequestBody Event event) {
        return eventServices.updateEvent(event);
    }

    @PostMapping("/generate-description")
    public DescriptionGenerationResponse generateDescription(@RequestBody DescriptionGenerationRequest request) {
        var result = eventServices.generateDescription(request.name(), request.type());
        return new DescriptionGenerationResponse(result.description(), result.generatedByAi());
    }

    @PostMapping("/generate-image")
    public ImageGenerationResponse generateImage(@RequestBody ImageGenerationRequest request) {
        var result = eventServices.generateImage(request.name(), request.description(), request.type());
        return new ImageGenerationResponse(result.imageUrl(), result.generatedByAi());
    }

    @PostMapping(value = "/upload-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImageGenerationResponse uploadImage(@RequestPart("image") MultipartFile image) {
        var result = eventServices.uploadImage(image);
        return new ImageGenerationResponse(result.imageUrl(), result.generatedByAi());
    }

    @GetMapping("/generated-images/{fileName:.+}")
    public ResponseEntity<byte[]> getGeneratedImage(@PathVariable("fileName") String fileName) {
        var image = eventServices.loadGeneratedImage(fileName);
        MediaType mediaType = MediaType.parseMediaType(image.contentType());
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                .body(image.bytes());
    }

    @GetMapping("/all")
    public List<Event> getAllEvents() {
        return eventServices.getAllEvents();
    }

    @GetMapping("/analytics")
    public EventAnalyticsResponseDto getAnalytics(
            @RequestParam(value = "range", defaultValue = "week") String range) {
        return eventServices.getAnalytics(range);
    }

    @GetMapping("/get/{idEvent}")
    public Event getEventById(@PathVariable("idEvent") long idEvent) {
        return eventServices.getEventById(idEvent);
    }

    @DeleteMapping("/delete/{idEvent}")
    public void deleteEvent(@PathVariable("idEvent") long idEvent) {
        eventServices.deleteEvent(idEvent);
    }

    @PostMapping("/{idEvent}/assign/{idStudent}")
    public EventAssignmentResponseDto assignStudentToEvent(
            @PathVariable("idEvent") long idEvent,
            @PathVariable("idStudent") long idStudent) {
        return eventServices.assignStudentToEvent(idEvent, idStudent);
    }
}