package tn.esprit.quizservice.repositories;

import tn.esprit.quizservice.entities.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuestionRepository extends JpaRepository<Question, Long> {
    List<Question> findByQuizIdOrderByOrderIndexAsc(Long quizId);
    void deleteByQuizId(Long quizId);
    List<Question> findByQuizIdIn(List<Long> quizIds);
    void deleteAllByQuizIdIn(List<Long> quizIds);
}
