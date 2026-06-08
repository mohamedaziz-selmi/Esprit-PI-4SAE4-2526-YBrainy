package tn.esprit.eventservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.esprit.eventservice.entity.Event;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {
    boolean existsByReferenceEvent(String referenceEvent);

    long countByReferenceEventStartingWith(String prefix);
}
