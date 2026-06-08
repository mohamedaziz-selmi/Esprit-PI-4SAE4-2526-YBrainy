package tn.esprit.eventservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import tn.esprit.eventservice.dto.EventStatsDto;
import tn.esprit.eventservice.dto.FeedbackDto;

import java.util.List;

@FeignClient(name = "feedback-service")
public interface FeedbackClient {

    @GetMapping("/Feedback/student/{studentId}")
    List<FeedbackDto> getFeedbacksByStudent(@PathVariable("studentId") long studentId);

    @GetMapping("/Feedback/event/{eventId}/stats")
    EventStatsDto getStatsByEvent(@PathVariable("eventId") long eventId);
}
