package tn.esprit.feedbackservice.service;

import tn.esprit.feedbackservice.dto.EventStatsDto;
import tn.esprit.feedbackservice.dto.FeedbackRequestDto;
import tn.esprit.feedbackservice.entity.Feedback;

import java.util.List;

public interface IFeedbackService {

    /** Student submits a new feedback for an event */
    Feedback submitFeedback(FeedbackRequestDto dto);

    /** Student updates his existing feedback */
    Feedback updateFeedback(long idFeedback, FeedbackRequestDto dto);

    /** Student deletes his own feedback */
    void deleteFeedback(long idFeedback);

    /** Get all PUBLIE feedbacks for an event (public view) */
    List<Feedback> getFeedbacksByEvent(long eventId);

    /** Get all feedbacks for an event including MASQUE (admin view) */
    List<Feedback> getAllFeedbacksByEvent(long eventId);

    /** Get all feedbacks submitted by a student */
    List<Feedback> getFeedbacksByStudent(long studentId);

    /** Get aggregated stats for an event (avg rating + distribution) */
    EventStatsDto getStatsByEvent(long eventId);

    /** Admin masks a feedback (hides it from public view) */
    Feedback maskFeedback(long idFeedback);

    /** Admin unmasks a feedback */
    Feedback unmaskFeedback(long idFeedback);
}
