package tn.esprit.quizservice.repositories;

import tn.esprit.quizservice.entities.QuestionOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuestionOptionRepository extends JpaRepository<QuestionOption, Long> {
    List<QuestionOption> findByQuestionIdOrderByOrderIndexAsc(Long questionId);
    void deleteByQuestionId(Long questionId);
    void deleteAllByQuestionIdIn(List<Long> questionIds);
}
