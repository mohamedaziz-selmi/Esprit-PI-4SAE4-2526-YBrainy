package tn.esprit.quizservice.repositories;

import tn.esprit.quizservice.entities.AttemptAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AttemptAnswerRepository extends JpaRepository<AttemptAnswer, Long> {
    List<AttemptAnswer> findByAttemptId(Long attemptId);
    void deleteAllByAttemptIdIn(List<Long> attemptIds);
}
