package tn.esprit.personalitybehaviorservice.repository;

import tn.esprit.personalitybehaviorservice.entity.Behavior;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BehaviorRepository extends MongoRepository<Behavior, String> {

    List<Behavior> findByFraudProbabilityScoreGreaterThan(Double score);

    List<Behavior> findByEngagementIndexPctLessThan(Double percentage);

    Optional<Behavior> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    void deleteByUserId(Long userId);
}
