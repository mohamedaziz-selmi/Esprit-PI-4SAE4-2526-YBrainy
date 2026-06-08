package tn.esprit.feedbackservice.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "feedback",
    // A student can submit only ONE feedback per event
    uniqueConstraints = @UniqueConstraint(columnNames = {"student_id", "event_id"})
)
@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class Feedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    long idFeedback;

    /** FK to user-service — plain ID, no JPA join (microservices rule) */
    @Column(name = "student_id", nullable = false)
    long studentId;

    /** FK to event-service — plain ID, no JPA join (microservices rule) */
    @Column(name = "event_id", nullable = false)
    long eventId;

    /**
     * Rating from 1 to 5.
     * 1 = very bad, 5 = excellent
     */
    @Column(nullable = false)
    int rating;

    /**
     * Optional written comment from the student.
     * Max 1000 characters.
     */
    @Column(length = 1000)
    String comment;

    @Column(length = 24)
    String sentimentLabel;

    /** Timestamp when the feedback was submitted */
    @Column(nullable = false)
    LocalDateTime dateCreation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    FeedbackStatut statut;
}
