package esprit.tn.breadandbutteruser.repositories;


import esprit.tn.breadandbutteruser.entities.Behavior;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BehaviorRepository extends JpaRepository<Behavior, Long> {

    List<Behavior> findByFraudProbabilityScoreGreaterThan(Double score);

    List<Behavior> findByEngagementIndexPctLessThan(Double percentage);
}