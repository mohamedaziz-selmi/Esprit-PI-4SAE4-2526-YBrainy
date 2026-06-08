package tn.esprit.feedbackservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.esprit.feedbackservice.entity.Feedback;
import tn.esprit.feedbackservice.entity.FeedbackStatut;

import java.util.List;
import java.util.Optional;

@Repository
public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    /** All PUBLIE feedbacks for a given event */
    List<Feedback> findByEventIdAndStatutOrderByDateCreationDesc(long eventId, FeedbackStatut statut);

    /** All feedbacks submitted by a student */
    List<Feedback> findByStudentIdOrderByDateCreationDesc(long studentId);

    /** Check if a student already submitted feedback for an event */
    boolean existsByStudentIdAndEventId(long studentId, long eventId);

    /** Get the specific feedback of a student for an event */
    Optional<Feedback> findByStudentIdAndEventId(long studentId, long eventId);

    /** Average rating for an event (only PUBLIE feedbacks) */
    @Query("SELECT AVG(f.rating) FROM Feedback f WHERE f.eventId = :eventId AND f.statut = 'PUBLIE'")
    Double findAverageRatingByEventId(@Param("eventId") long eventId);

    /** Count of feedbacks per rating value for an event — used for rating distribution chart */
    @Query("SELECT f.rating, COUNT(f) FROM Feedback f WHERE f.eventId = :eventId AND f.statut = 'PUBLIE' GROUP BY f.rating ORDER BY f.rating")
    List<Object[]> findRatingDistributionByEventId(@Param("eventId") long eventId);

    /** All feedbacks for an event (for admin view — all statuts) */
    List<Feedback> findByEventIdOrderByDateCreationDesc(long eventId);
}
