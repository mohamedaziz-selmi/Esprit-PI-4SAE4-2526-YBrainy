package esprit.tn.breadandbutteruser.repositories;

import esprit.tn.breadandbutteruser.entities.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByRecipientUserIdOrderByCreatedAtDesc(Long userId);

    long countByRecipientUserIdAndReadFalse(Long userId);

    @Modifying
    @Transactional
    @Query("UPDATE Notification n SET n.read = true WHERE n.recipient.userId = :userId AND n.read = false")
    void markAllAsRead(@Param("userId") Long userId);
}
