package tn.esprit.feedbackservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "feedback_reminder",
        uniqueConstraints = @UniqueConstraint(columnNames = "inscription_id")
)
@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class FeedbackReminder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    long idReminder;

    @Column(name = "inscription_id", nullable = false)
    long inscriptionId;

    @Column(name = "event_id", nullable = false)
    long eventId;

    @Column(name = "student_id", nullable = false)
    long studentId;

    @Column(nullable = false)
    LocalDateTime createdAt;

    @Column(length = 80)
    String source;

    @Column(length = 30)
    String statut;
}
