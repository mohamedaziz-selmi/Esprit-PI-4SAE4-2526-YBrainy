package tn.esprit.tpfoyer.Repositories;

import tn.esprit.tpfoyer.Entities.LearningPath;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LearningPathRepository extends JpaRepository<LearningPath, Long> {
    List<LearningPath> findByStudentIdAndSavedTrue(Long studentId);
    List<LearningPath> findByStudentId(Long studentId);
}
