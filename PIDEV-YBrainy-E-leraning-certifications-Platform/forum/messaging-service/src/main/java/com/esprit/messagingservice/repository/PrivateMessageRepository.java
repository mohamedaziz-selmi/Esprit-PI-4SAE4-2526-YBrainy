package com.esprit.messagingservice.repository;

import com.esprit.messagingservice.model.PrivateMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PrivateMessageRepository extends JpaRepository<PrivateMessage, Long> {

    @Query("SELECT m FROM PrivateMessage m WHERE " +
           "(m.senderId = :userId1 AND m.receiverId = :userId2) OR " +
           "(m.senderId = :userId2 AND m.receiverId = :userId1) " +
           "ORDER BY m.createdAt ASC")
    List<PrivateMessage> findConversation(@Param("userId1") Long userId1, @Param("userId2") Long userId2);

    List<PrivateMessage> findByReceiverIdOrderByCreatedAtDesc(Long receiverId);

    long countByReceiverIdAndReadFalse(Long receiverId);

    @Modifying
    @Query("UPDATE PrivateMessage m SET m.read = true WHERE m.senderId = :senderId AND m.receiverId = :receiverId AND m.read = false")
    void markConversationAsRead(@Param("senderId") Long senderId, @Param("receiverId") Long receiverId);

    @Query("SELECT DISTINCT CASE WHEN m.senderId = :userId THEN m.receiverId ELSE m.senderId END " +
           "FROM PrivateMessage m WHERE m.senderId = :userId OR m.receiverId = :userId")
    List<Long> findContactIds(@Param("userId") Long userId);

    @Query("SELECT m FROM PrivateMessage m WHERE m.senderId = :userId OR m.receiverId = :userId ORDER BY m.createdAt DESC")
    List<PrivateMessage> findAllByUserId(@Param("userId") Long userId);
}
