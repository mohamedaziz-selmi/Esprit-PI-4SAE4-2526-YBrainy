package esprit.tn.breadandbutteruser.repositories;

import esprit.tn.breadandbutteruser.entities.InteractionEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface InteractionEventRepository extends JpaRepository<InteractionEvent, Long> {

    @Query("select distinct e.keycloakSubject from InteractionEvent e where e.receivedAt >= :since")
    List<String> findDistinctSubjectsSince(@Param("since") LocalDateTime since);

    List<InteractionEvent> findByKeycloakSubjectAndReceivedAtAfter(String keycloakSubject, LocalDateTime since);

    long countByKeycloakSubjectAndReceivedAtAfter(String keycloakSubject, LocalDateTime since);

    long countByEmailIgnoreCaseAndReceivedAtAfter(String email, LocalDateTime since);

    java.util.Optional<InteractionEvent> findTopByKeycloakSubjectOrderByReceivedAtDesc(String keycloakSubject);

    java.util.Optional<InteractionEvent> findTopByEmailIgnoreCaseOrderByReceivedAtDesc(String email);

    List<InteractionEvent> findTop50ByKeycloakSubjectOrderByReceivedAtDesc(String keycloakSubject);

    List<InteractionEvent> findTop50ByEmailIgnoreCaseOrderByReceivedAtDesc(String email);
}
