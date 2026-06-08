package tn.esprit.eventservice.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Entity
@Table(name = "event")
@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    long idEvent;

    String name;
    @Column(unique = true, length = 40)
    String referenceEvent;
    @Lob
    @Column(columnDefinition = "TEXT")
    String description;
    @Lob
    @Column(columnDefinition = "LONGTEXT")
    String imageUrl;
    String location;
    int capacite;
    LocalDateTime dateDebut;
    LocalDateTime dateFin;
    LocalDateTime dateCreation;

    @Enumerated(EnumType.STRING)
    EventType type;

    @Enumerated(EnumType.STRING)
    EventStatut statut;

    /** FK to user-service — stored as plain ID, no JPA join */
    long adminId;

    /** Computed at runtime via inscription-service, not persisted */
    @Transient
    long inscriptionsCount;
}
