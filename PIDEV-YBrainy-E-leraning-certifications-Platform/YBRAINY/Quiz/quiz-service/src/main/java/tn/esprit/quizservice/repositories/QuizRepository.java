package tn.esprit.quizservice.repositories;

import tn.esprit.quizservice.entities.Quiz;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuizRepository extends JpaRepository<Quiz, Long> {
    List<Quiz> findByCourseIdOrderByCreatedAtAsc(Long courseId);
    void deleteAllByCourseId(Long courseId);
}
