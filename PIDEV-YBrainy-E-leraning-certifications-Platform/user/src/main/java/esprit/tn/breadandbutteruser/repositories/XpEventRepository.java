package esprit.tn.breadandbutteruser.repositories;

import esprit.tn.breadandbutteruser.entities.XpEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface XpEventRepository extends JpaRepository<XpEvent, Long> {

    List<XpEvent> findTop10ByUserUserIdOrderByCreatedAtDesc(Long userId);

    List<XpEvent> findTop30ByUserUserIdOrderByCreatedAtAsc(Long userId);

    List<XpEvent> findByUserUserIdOrderByCreatedAtDesc(Long userId);

    long countByUserUserId(Long userId);
}
