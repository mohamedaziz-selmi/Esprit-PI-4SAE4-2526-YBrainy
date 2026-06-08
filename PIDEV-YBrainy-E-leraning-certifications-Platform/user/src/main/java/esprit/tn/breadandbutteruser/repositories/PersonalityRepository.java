package esprit.tn.breadandbutteruser.repositories;


import esprit.tn.breadandbutteruser.entities.Personality;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PersonalityRepository extends JpaRepository<Personality, Long> {

    List<Personality> findByCareerAlignmentScoreGreaterThan(Double score);

    List<Personality> findByVisualLearningPctGreaterThan(Double percentage);
}