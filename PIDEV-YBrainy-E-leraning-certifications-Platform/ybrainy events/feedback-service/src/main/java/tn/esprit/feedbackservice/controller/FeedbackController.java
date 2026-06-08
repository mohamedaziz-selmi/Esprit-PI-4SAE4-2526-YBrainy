package tn.esprit.feedbackservice.controller;

import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;
import tn.esprit.feedbackservice.dto.EventStatsDto;
import tn.esprit.feedbackservice.dto.FeedbackRequestDto;
import tn.esprit.feedbackservice.dto.SpeechTranscriptionResponseDto;
import tn.esprit.feedbackservice.entity.Feedback;
import tn.esprit.feedbackservice.service.IFeedbackService;
import tn.esprit.feedbackservice.service.SpeechToTextService;

import java.util.List;

@RestController
@CrossOrigin(
        origins = {"http://localhost:4200", "http://127.0.0.1:4200"},
        allowCredentials = "true"
)
@AllArgsConstructor
@RequestMapping("/Feedback")
public class FeedbackController {

    private final IFeedbackService feedbackService;
    private final SpeechToTextService speechToTextService;

    // ── Student endpoints ─────────────────────────────────────────────────────

    /**
     * Submit a new feedback.
     * Business rule: inscription must be CONFIRMEE.
     * POST /Feedback
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Feedback submitFeedback(@RequestBody FeedbackRequestDto dto) {
        return feedbackService.submitFeedback(dto);
    }

    @PostMapping("/transcribe")
    public SpeechTranscriptionResponseDto transcribeSpeech(@RequestParam("audio") MultipartFile audio) {
        return new SpeechTranscriptionResponseDto(speechToTextService.transcribe(audio));
    }

    /**
     * Update an existing feedback.
     * Only the student who created it can update it.
     * PUT /Feedback/{idFeedback}
     */
    @PutMapping("/{idFeedback}")
    public Feedback updateFeedback(
            @PathVariable("idFeedback") long idFeedback,
            @RequestBody FeedbackRequestDto dto) {
        return feedbackService.updateFeedback(idFeedback, dto);
    }

    /**
     * Delete a feedback.
     * DELETE /Feedback/{idFeedback}
     */
    @DeleteMapping("/{idFeedback}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFeedback(@PathVariable("idFeedback") long idFeedback) {
        feedbackService.deleteFeedback(idFeedback);
    }

    // ── Public read endpoints ─────────────────────────────────────────────────

    /**
     * Get all PUBLIE feedbacks for an event.
     * Used by the frontend to display reviews on an event page.
     * GET /Feedback/event/{eventId}
     */
    @GetMapping("/event/{eventId}")
    public List<Feedback> getFeedbacksByEvent(@PathVariable("eventId") long eventId) {
        return feedbackService.getFeedbacksByEvent(eventId);
    }

    /**
     * Get aggregated stats for an event:
     * average rating + distribution (how many 1★, 2★, 3★, 4★, 5★).
     * GET /Feedback/event/{eventId}/stats
     */
    @GetMapping("/event/{eventId}/stats")
    public EventStatsDto getStatsByEvent(@PathVariable("eventId") long eventId) {
        return feedbackService.getStatsByEvent(eventId);
    }

    /**
     * Get all feedbacks submitted by a student.
     * GET /Feedback/student/{studentId}
     */
    @GetMapping("/student/{studentId}")
    public List<Feedback> getFeedbacksByStudent(@PathVariable("studentId") long studentId) {
        return feedbackService.getFeedbacksByStudent(studentId);
    }

    // ── Admin endpoints ───────────────────────────────────────────────────────

    /**
     * Admin: get ALL feedbacks for an event (including MASQUE).
     * GET /Feedback/admin/event/{eventId}
     */
    @GetMapping("/admin/event/{eventId}")
    public List<Feedback> getAllFeedbacksByEvent(@PathVariable("eventId") long eventId) {
        return feedbackService.getAllFeedbacksByEvent(eventId);
    }

    /**
     * Admin: hide an inappropriate feedback from public view.
     * PUT /Feedback/admin/{idFeedback}/mask
     */
    @PutMapping("/admin/{idFeedback}/mask")
    public Feedback maskFeedback(@PathVariable("idFeedback") long idFeedback) {
        return feedbackService.maskFeedback(idFeedback);
    }

    /**
     * Admin: restore a masked feedback to public view.
     * PUT /Feedback/admin/{idFeedback}/unmask
     */
    @PutMapping("/admin/{idFeedback}/unmask")
    public Feedback unmaskFeedback(@PathVariable("idFeedback") long idFeedback) {
        return feedbackService.unmaskFeedback(idFeedback);
    }
}
