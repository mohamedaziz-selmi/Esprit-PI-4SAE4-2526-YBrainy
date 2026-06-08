package tn.esprit.personalitybehaviorservice.repository;

import tn.esprit.personalitybehaviorservice.entity.Personality;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PersonalityRepository extends MongoRepository<Personality, String> {

    List<Personality> findByCareerAlignmentScoreGreaterThan(Double score);

    List<Personality> findByVisualLearningPctGreaterThan(Double percentage);

    Optional<Personality> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    void deleteByUserId(Long userId);
}
