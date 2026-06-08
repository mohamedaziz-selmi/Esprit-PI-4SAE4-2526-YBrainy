package com.esprit.messagingservice.service;

import com.esprit.messagingservice.dto.*;
import com.esprit.messagingservice.feign.UserFeignClient;
import com.esprit.messagingservice.model.PrivateMessage;
import com.esprit.messagingservice.repository.PrivateMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessageService {

    private final PrivateMessageRepository messageRepository;
    private final UserFeignClient userFeignClient;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public MessageResponse send(SendMessageRequest request) {
        PrivateMessage message = PrivateMessage.builder()
                .senderId(request.getSenderId())
                .receiverId(request.getReceiverId())
                .content(request.getContent())
                .mediaUrl(request.getMediaUrl())
                .mediaType(request.getMediaType())
                .build();
        message = messageRepository.save(message);

        MessageResponse response = toResponse(message);
        messagingTemplate.convertAndSend(
                "/topic/messages/user-" + request.getReceiverId(), response);
        return response;
    }

    public List<MessageResponse> getConversation(Long userId1, Long userId2) {
        return messageRepository.findConversation(userId1, userId2)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    /** Returns one ConversationPreview per partner, ordered by last message time. */
    public List<ConversationPreviewResponse> getInbox(Long userId) {
        List<PrivateMessage> all = messageRepository.findAllByUserId(userId);

        // Group by partner
        Map<Long, List<PrivateMessage>> byPartner = new LinkedHashMap<>();
        for (PrivateMessage m : all) {
            Long partnerId = m.getSenderId().equals(userId) ? m.getReceiverId() : m.getSenderId();
            byPartner.computeIfAbsent(partnerId, k -> new ArrayList<>()).add(m);
        }

        List<ConversationPreviewResponse> previews = new ArrayList<>();
        for (Map.Entry<Long, List<PrivateMessage>> entry : byPartner.entrySet()) {
            Long partnerId = entry.getKey();
            List<PrivateMessage> msgs = entry.getValue();
            msgs.sort(Comparator.comparing(PrivateMessage::getCreatedAt).reversed());
            PrivateMessage last = msgs.get(0);

            long unread = msgs.stream()
                    .filter(m -> m.getReceiverId().equals(userId) && !m.isRead())
                    .count();

            UserDto partner = safeGetUser(partnerId);

            previews.add(ConversationPreviewResponse.builder()
                    .userId(partnerId)
                    .username(partner != null ? partner.getUsername() : "User " + partnerId)
                    .role(partner != null ? partner.getRole() : "STUDENT")
                    .level(partner != null ? partner.getLevel() : 1)
                    .levelTitle(partner != null ? partner.getLevelTitle() : "")
                    .lastMessage(last.getContent() != null ? last.getContent() : (last.getMediaUrl() != null ? "[media]" : ""))
                    .lastMessageAt(last.getCreatedAt())
                    .unreadCount(unread)
                    .sentByMe(last.getSenderId().equals(userId))
                    .build());
        }
        return previews;
    }

    public long getUnreadCount(Long userId) {
        return messageRepository.countByReceiverIdAndReadFalse(userId);
    }

    @Transactional
    public MessageResponse markAsRead(Long messageId) {
        PrivateMessage m = messageRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Message not found: " + messageId));
        m.setRead(true);
        return toResponse(messageRepository.save(m));
    }

    @Transactional
    public void markConversationRead(Long senderId, Long receiverId) {
        messageRepository.markConversationAsRead(senderId, receiverId);
    }

    public List<UserSummaryResponse> getAllUsers() {
        try {
            return userFeignClient.getAllUsers().stream()
                    .filter(u -> u.getEffectiveId() != null)
                    .map(u -> UserSummaryResponse.builder()
                            .id(u.getEffectiveId())
                            .username(u.getUsername())
                            .role(u.getRole() != null ? u.getRole() : "STUDENT")
                            .level(u.getLevel())
                            .levelTitle(u.getLevelTitle() != null ? u.getLevelTitle() : "")
                            .build())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Could not fetch users from breadandbutteruser: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private MessageResponse toResponse(PrivateMessage m) {
        UserDto sender   = safeGetUser(m.getSenderId());
        UserDto receiver = safeGetUser(m.getReceiverId());
        return MessageResponse.builder()
                .id(m.getId())
                .senderId(m.getSenderId())
                .senderUsername(sender   != null ? sender.getUsername()   : "User " + m.getSenderId())
                .senderRole(sender       != null ? sender.getRole()       : "STUDENT")
                .senderLevel(sender      != null ? sender.getLevel()      : 1)
                .senderLevelTitle(sender != null ? sender.getLevelTitle() : "")
                .receiverId(m.getReceiverId())
                .receiverUsername(receiver != null ? receiver.getUsername() : "User " + m.getReceiverId())
                .content(m.getContent())
                .mediaUrl(m.getMediaUrl())
                .mediaType(m.getMediaType())
                .read(m.isRead())
                .createdAt(m.getCreatedAt())
                .build();
    }

    private UserDto safeGetUser(Long userId) {
        try { return userFeignClient.getUser(userId); } catch (Exception e) { return null; }
    }
}
