package tn.esprit.inscriptionservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.esprit.inscriptionservice.entity.AdminNotification;

import java.util.List;

@Repository
public interface AdminNotificationRepository extends JpaRepository<AdminNotification, Long> {
    List<AdminNotification> findTop10ByOrderByCreatedAtDesc();
}