package tn.esprit.inscriptionservice.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Entity
@Table(name = "inscription")
@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class Inscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    long idInscription;

    LocalDateTime dateInscription;

    @Enumerated(EnumType.STRING)
    InscriptionStatut statut;

    /** FK to user-service — stored as plain ID, no JPA join */
    long studentId;

    /** FK to event-service — stored as plain ID, no JPA join */
    long eventId;
}
