package tn.esprit.feedbackservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.esprit.feedbackservice.entity.FeedbackReminder;

@Repository
public interface FeedbackReminderRepository extends JpaRepository<FeedbackReminder, Long> {
    boolean existsByInscriptionId(long inscriptionId);
}
